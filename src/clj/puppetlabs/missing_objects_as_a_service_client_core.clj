(ns puppetlabs.missing-objects-as-a-service-client-core
  (:require [puppetlabs.enterprise.jgit-utils :as jgit]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [puppetlabs.http.client.common :as http-client]
            [puppetlabs.missing-objects-as-a-service-web-core :as core]
            [schema.core :as schema])
  (:import
    (org.eclipse.jgit.errors MissingObjectException PackProtocolException)
    (java.io IOException)
    (org.eclipse.jgit.api.errors TransportException)))

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
    (log/trace "Validating repo")
    (jgit/validate-repo-exists! repo)
    (log/trace "Repo validated")
    (if-not (= latest-commit-id (jgit/master-rev-id repo))
      (try
        (log/trace "fetching latest commit")
        (jgit/fetch repo))
      (log/trace "Nothing to fetch"))))

(defn apply-updates-to-repo
  [repo-id latest-commit-id data-dir bare?]
  (let [repo-path (core/bare-repo-path data-dir repo-id)
        fetch? (non-empty-dir? repo-path)
        clone? (not fetch?)]
    (try
      (if clone?
        (do
          (log/tracef "Cloning %s into %s" repo-id repo-path)
          (jgit/clone! (server-repo-url repo-id) repo-path bare?))
        (do
          (log/tracef "Fetching commit %s into %s" latest-commit-id repo-path)
          (fetch-if-necessary! repo-path latest-commit-id)))
      (with-open [repo (jgit/get-repository-from-git-dir repo-path)]
        (log/trace "in the with-open repo")
        (if latest-commit-id
          (try
            (log/tracef "Updating ref to %s for repo %s" latest-commit-id repo)
            (jgit/update-ref repo synced-commit-branch-name latest-commit-id)
            (log/tracef "ref updated to %s for repo %s" latest-commit-id repo)
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
      (log/tracef "Got back this response: %s" response)
      response)
    (catch IOException e
      (throw (IllegalStateException. "Unable to get latest-commits from server" e)))))

(schema/defn ^:always-validate sync-on-agent
  "Runs the sync process on the agent."
  [client-number config context]
  (let [{:keys [repo-id base-dir]} config
        {:keys [http-client-atom shutdown-requested?]} context
        latest-commits (get-latest-commits-from-server @http-client-atom)]
    (log/trace "File sync process running on repo " repo-id)
    (if latest-commits
      (try
        (apply-updates-to-repo repo-id latest-commits (str base-dir client-number) true)
        (log/trace "updates applied")
        (catch MissingObjectException e
          (log/error e)
          (deliver shutdown-requested? true))
        (catch PackProtocolException e
          (log/error e)
          (deliver shutdown-requested? true))
        (catch TransportException e
          (log/error e)
          (deliver shutdown-requested? true)))
      (log/tracef "No latest commits, got %s from server" latest-commits))
    {:status :successful}))

(defn start-periodic-sync-process!
  [client-number
   {:keys [poll-interval] :as config}
   {:keys [shutdown-requested?] :as context}]
  (log/infof "Starting sync process number %s" client-number)
  (while (not (realized? shutdown-requested?))
    (sync-on-agent client-number config context)
    (Thread/sleep poll-interval)))