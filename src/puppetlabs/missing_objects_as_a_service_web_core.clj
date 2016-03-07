(ns puppetlabs.missing-objects-as-a-service-web-core
  (:require [puppetlabs.missing-objects-as-a-service-service :as hello-svc]
            [clojure.tools.logging :as log]
            [puppetlabs.comidi :as comidi]
            [puppetlabs.kitchensink.core :as ks]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [clojure.java.io :as io]
            [puppetlabs.enterprise.jgit-utils :as jgit-utils])
  (:import (org.eclipse.jgit.api InitCommand Git)
           (java.io File)
           (org.eclipse.jgit.lib PersonIdent)
           (clojure.lang IFn)))

(def Identity
  "Schema for an author/committer."
  {:name schema/Str
   :email schema/Str})

(defn app
  [hello-service]
  (comidi/routes
    (comidi/GET ["/" :caller] [caller]
      (fn [req]
        (log/info "Handling request for caller:" caller)
        {:status  200
         :headers {"Content-Type" "text/plain"}
         :body    (hello-svc/hello hello-service caller)}))
    (comidi/not-found "Not Found")))

(defn initialize-bare-repo!
  "Initialize a bare Git repository in the directory specified by 'path'."
  [path]
  (.. (new InitCommand)
      (setDirectory (io/as-file path))
      (setBare false)
      (call)))

(defn initialize-data-dir!
  "Initialize the data directory under which all git repositories will be hosted."
  [data-dir]
  (ks/mkdirs! data-dir))

(schema/defn ^:always-validate bare-repo-path :- File
             "Given a path to a data-dir (either client or storage service) and a repo-name,
             returns the path on disk to the bare Git repository with the given name."
             [data-dir :- schema/Str
              repo-id :- schema/Str]
             (fs/file data-dir (str repo-id ".git")))

(defn initialize-repos!
             "Initialize the repositories managed by this service.  For each repository ...
               * There is a directory under data-dir (specified in config) which is actual Git
                 repository (git dir).
               * If staging-dir does not exist, it will be created.
               * If there is not an existing Git repo under data-dir,
                 'git init' will be used to create one."
  [{:keys [repo-id base-dir]}]
  (log/infof "Initializing git data dir: %s for repo %s" base-dir repo-id)
  (initialize-data-dir! (fs/file base-dir))
  (let [git-dir (bare-repo-path base-dir repo-id)]
    (log/infof "Initializing Git repository at %s" git-dir)
    (initialize-bare-repo! git-dir)))

(defn get-repo
  [path]
  (.getRepository (Git/open (io/as-file path))))

(schema/defn identity->person-ident :- PersonIdent
  [{:keys [name email]} :- Identity]
  (PersonIdent. name email))

(schema/defn commit-repo
  ([{:keys [repo-id base-dir]}]
    (commit-repo repo-id base-dir
                 {:msg "this is amaaaaazing" :committer (identity->person-ident {:name "justin" :email "foo.bar"})}))
  ([repo-id :- schema/Str
    base-dir :- schema/Str
    commit-info :- {:msg schema/Str :committer PersonIdent}]
    (let [git-dir (bare-repo-path base-dir repo-id)
          {:keys [msg committer]} commit-info]
      (log/infof "Committing all files in '%s' to repo '%s'" git-dir repo-id)
      (with-open [git-repo (get-repo git-dir)]
        (let [git (Git/wrap git-repo)]
          (jgit-utils/add-and-commit git msg committer))))))

(defn commit-on-agent
  [agent-state config context]
  (commit-repo config)
  {})

(schema/defn ^:always-validate start-periodic-commit-process!
  "Synchronizes the repositories specified in 'config' by sending the agent an
  action.  It is important that this function only be called once, during service
  startup.  (Although, note that one-off sync runs can be triggered by sending
  the agent a 'sync-on-agent' action.)

  'schedule-fn' is the function that will be invoked after each iteration of the
  sync process to schedule the next iteration."
  [schedule-fn :- IFn
   config
   context]
  (let [commit-agent (:commit-agent context)
        periodic-commit (fn [& args]
                        (-> (apply commit-on-agent args)
                            (assoc :schedule-next-run? true)))
        send-to-agent #(send-off commit-agent periodic-commit config context)]

    (add-watch commit-agent
               ::schedule-watch
               (fn [key* ref* old-state new-state]
                 (when (:schedule-next-run? new-state)
                   (let [{:keys [shutdown-requested? scheduled-jobs-completed? jobs-scheduled?]}
                         (:scheduled-jobs-state context)]
                     (if @shutdown-requested?
                       (deliver scheduled-jobs-completed? true)
                       (do
                         (log/trace "Scheduling the next iteration of the commit process.")
                         (reset! jobs-scheduled? true)
                         (schedule-fn send-to-agent)))))))
    ; The watch is in place, now send the initial action.
    (send-to-agent)))