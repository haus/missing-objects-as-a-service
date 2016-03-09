(ns puppetlabs.missing-objects-as-a-service-client-core
  (:require [puppetlabs.enterprise.jgit-utils :as jgit]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [puppetlabs.http.client.common :as http-client]
            [puppetlabs.missing-objects-as-a-service-web-core :as core]
            [puppetlabs.http.client.sync :as sync]
            [schema.core :as schema])
  (:import
    (org.eclipse.jgit.errors MissingObjectException)
    (clojure.lang IFn Agent Atom IDeref)
    (java.io IOException)))

(def synced-commit-branch-name "synced-commit")

(defn server-repo-url
  [repo-id]
  (str "http://localhost:8080/servlet/" repo-id ".git"))

(defn non-empty-dir?
  "Returns true if path exists, is a directory, and contains at least 1 file."
  [path]
  (-> path
      io/as-file
      .listFiles
      count
      (> 0)))

(defn fetch-if-necessary!
  [repo-path latest-commit-id]
  (with-open [repo (core/get-repo repo-path)]
    (log/info "Validating repo")
    (jgit/validate-repo-exists! repo)
    (log/info "Repo validated")
    (if-not (= latest-commit-id (jgit/master-rev-id repo))
      (try
        (log/info "fetching latest commit")
        (jgit/fetch repo))
      (log/info "Nothing to fetch"))))

(defn apply-updates-to-repo
  [repo-id latest-commit-id data-dir                       ;bare?
   ]
  (let [repo-path (str data-dir "/" repo-id ".git")
        fetch? (non-empty-dir? repo-path)
        clone? (not fetch?)]
    (try
      (if clone?
        (do
          (log/infof "Cloning %s into %s" repo-id repo-path)
          (jgit/clone! (server-repo-url repo-id) repo-path false) ;bare?
                     )
        (do
          (log/infof "Fetching commit %s into %s" latest-commit-id repo-path)
          (fetch-if-necessary! repo-path latest-commit-id)))
      (with-open [repo (core/get-repo repo-path)]
        (log/info "in the with-open repo")
        (if latest-commit-id
          (try
            (log/infof "Updating ref to %s for repo %s" latest-commit-id repo)
            (jgit/update-ref repo synced-commit-branch-name latest-commit-id)
            (log/infof "ref updated to %s for repo %s" latest-commit-id repo)
            ; see pe-file-sync.client-core:429
            (catch MissingObjectException e
              (log/error (str
                           "File sync successfully fetched from the "
                           "server repo, but did not receive commit "
                           latest-commit-id))
              (throw e))))))))

(schema/defn ^:always-validate get-latest-commits-from-server
  "Request information about the latest commits available from the server.
  The latest commits are requested from the URL in the supplied
  `server-api-url` argument.  Returns the payload from the response.
  Throws a 'FileSyncPollError' if an error occurs."
  [client :- (schema/protocol http-client/HTTPClient)]
  (try
    (let [response (slurp (:body (http-client/get
                                   client "http://localhost:8080/latest-commits")))]
      (log/infof "Got back this response: %s" response)
      response)
    (catch IOException e
      (throw (IllegalStateException. "Unable to get latest-commits from server" e)))))

(schema/defn ^:always-validate sync-on-agent
  "Runs the sync process on the agent."
  [agent-state
   config
   context]
  (let [{:keys [repo-id]} config
        {:keys [http-client-atom]} context
        latest-commits (get-latest-commits-from-server @http-client-atom)]
    (log/info "File sync process running on repo " repo-id)
    (if latest-commits
      (apply-updates-to-repo repo-id latest-commits "client")
      (log/infof "No latest commits, got %s from server" latest-commits))
    (log/info "updates applied")
    {:status :successful}))

(schema/defn ^:always-validate start-periodic-sync-process!
  "Synchronizes the repositories specified in 'config' by sending the agent an
  action.  It is important that this function only be called once, during service
  startup.  (Although, note that one-off sync runs can be triggered by sending
  the agent a 'sync-on-agent' action.)

  'schedule-fn' is the function that will be invoked after each iteration of the
  sync process to schedule the next iteration."
  [schedule-fn config context]
  (let [sync-agent (:sync-agent context)
        periodic-sync (fn [& args]
                        (-> (apply sync-on-agent args)
                            (assoc :schedule-next-run? true)))
        send-to-agent #(send-off sync-agent periodic-sync config context)]
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