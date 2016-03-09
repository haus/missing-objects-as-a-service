(ns puppetlabs.missing-objects-as-a-service-client-core
  (:require [puppetlabs.enterprise.jgit-utils :as jgit]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [schema.core :as schema])
  (:import
    (org.eclipse.jgit.errors MissingObjectException)
    (clojure.lang IFn Agent Atom IDeref)))

(def synced-commit-branch-name "synced-commit")

(schema/defn ^:always-validate sync-on-agent :- AgentState
  "Runs the sync process on the agent."
  [agent-state :- AgentState
   config :- common/FileSyncConfig
   context :- ClientContext
   force-one-time-sync? :- schema/Bool]
  (try+
    (let [{:keys [client data-dir]} config
          {:keys [server-repo-url server-api-url]} client
          {:keys [client-status event-callbacks get-http-client metrics new-commit-callbacks]} context
          latest-commits (get-latest-commits-from-server
                           (get-http-client)
                           server-api-url
                           @client-status)
          check-in-time (common/timestamp)
          repos (keys latest-commits)
          _ (log/trace "File sync process running on repos " repos)
          client-data-dir (path-to-data-dir data-dir)
          repo-states (process-repos-for-updates
                        repos
                        server-repo-url
                        latest-commits
                        @new-commit-callbacks
                        @event-callbacks
                        metrics
                        client-data-dir
                        force-one-time-sync?)
          full-success? (every? #(not= (:status %) common/repo-status-error)
                                (vals repo-states))
          sync-time (common/timestamp)]

      (update-last-checkin-status! client-status check-in-time latest-commits)
      ;; TODO: doing this as a separate function from the last-checkin thing above,
      ;; because I think we're going to want to do the updates inside of the
      ;; process-repos loop in order to improve our status information.
      (update-repos-status! client-status repo-states)
      (if full-success?
        (update-last-successful-sync-status! client-status sync-time))
      {:status (if full-success? :successful :partial-success)})
    (catch errors/file-sync-error? error
      (errors/log-file-sync-error! error "File Sync failure during sync or fetch phase")
      {:status :failed
       :error  error})))

(schema/defn ^:always-validate start-periodic-sync-process!
  "Synchronizes the repositories specified in 'config' by sending the agent an
  action.  It is important that this function only be called once, during service
  startup.  (Although, note that one-off sync runs can be triggered by sending
  the agent a 'sync-on-agent' action.)

  'schedule-fn' is the function that will be invoked after each iteration of the
  sync process to schedule the next iteration."
  [schedule-fn :- IFn
   config :- common/FileSyncConfig
   context :- ClientContext]
  (let [sync-agent (:sync-agent context)
        periodic-sync (fn [& args]
                        (-> (apply sync-on-agent args)
                            (assoc :schedule-next-run? true)))
        send-to-agent #(send-off sync-agent periodic-sync config
                                 context
                                 false)]
    (add-watch sync-agent
               ::schedule-watch
               (fn [key* ref* old-state new-state]
                 (when (:schedule-next-run? new-state)
                   (let [{:keys [shutdown-requested? scheduled-jobs-completed? jobs-scheduled?]}
                         (:scheduled-jobs-state context)]
                     (if @shutdown-requested?
                       (deliver scheduled-jobs-completed? true)
                       (do
                         (log/trace "Scheduling the next iteration of the sync process.")
                         (reset! jobs-scheduled? true)
                         (schedule-fn send-to-agent)))))))
    ; The watch is in place, now send the initial action.
    (send-to-agent)))

(defn non-empty-dir?
  "Returns true if path exists, is a directory, and contains at least 1 file."
  [path]
  (-> path
      io/as-file
      .listFiles
      count
      (> 0)))

(defn fetch-if-necessary!
  [repo-id repo-path latest-commit-id target-dir]
  (with-open [repo (jgit/get-repository-from-git-dir target-dir)]
    (jgit/validate-repo-exists! repo)
    (when-not (= latest-commit-id (jgit/master-rev-id repo))
      (try
        (jgit/fetch repo)))))

(defn apply-updates-to-repo
  [repo-id server-repo-url latest-commit-id repo-path bare?]
  (let [fetch? (non-empty-dir? repo-path)
        clone? (not fetch?)]
    (try
      (if clone?
        (jgit/clone! server-repo-url repo-path bare?)
        (fetch-if-necessary! repo-id repo-path latest-commit-id))
      (with-open [repo ( jgit/get-repository-from-git-dir repo-path)]
        (if latest-commit-id
          (let [initial-commit-id (jgit/rev-commit-id repo synced-commit-branch-name)]
            (try
              (jgit/update-ref repo synced-commit-branch-name latest-commit-id)
              ; see pe-file-sync.client-core:429
              (catch MissingObjectException e))))))))