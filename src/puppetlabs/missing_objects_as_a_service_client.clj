(ns puppetlabs.missing-objects-as-a-service-client
  (:require [puppetlabs.enterprise.jgit-utils :as jgit]
            [clojure.java.io :as io])
  (:import
            (org.eclipse.jgit.errors MissingObjectException)))

(def synced-commit-branch-name "synced-commit")

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

