(ns puppetlabs.missing-objects-as-a-service-web-core
  (:require [clojure.tools.logging :as log]
            [puppetlabs.comidi :as comidi]
            [puppetlabs.kitchensink.core :as ks]
            [me.raynes.fs :as fs]
            [schema.core :as schema]
            [clojure.java.io :as io]
            [puppetlabs.enterprise.jgit-utils :as jgit-utils])
  (:import (org.eclipse.jgit.api InitCommand Git)
           (java.io File)
           (org.eclipse.jgit.lib PersonIdent)
           (clojure.lang IFn Atom)))

(def Identity
  "Schema for an author/committer."
  {:name schema/Str
   :email schema/Str})

(defn initialize-bare-repo!
  "Initialize a bare Git repository in the directory specified by 'path'."
  [path]
  (.. (new InitCommand)
      (setDirectory (io/as-file path))
      (setBare true)
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

(defn live-repo-path
  [data-dir repo-id]
  (fs/file data-dir repo-id))

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
  (ks/mkdirs! (live-repo-path base-dir repo-id))
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
          live-dir (live-repo-path base-dir repo-id)
          {:keys [msg committer]} commit-info]
      (log/infof "Committing all files in '%s' to repo '%s'" git-dir repo-id)
      (with-open [git-repo (jgit-utils/get-repository git-dir live-dir)]
        (let [git (Git/wrap git-repo)]
          (jgit-utils/add-and-commit git msg committer))))))

(schema/defn latest-commit-data
  "Returns information about the latest commit on the master branch of the
   repository specified by git-dir and staging-dir.  If a repository does
   not exist at the specified paths, an error is returned.  If no commits have
   been made on the repository, the return value will be {:commit nil}."
  [{:keys [repo-id base-dir]}]
  (let [git-dir (bare-repo-path base-dir repo-id)]
    (with-open [repo (jgit-utils/get-repository-from-git-dir git-dir)]
      (let [latest-commit (when-let [ref (.getRef repo "refs/heads/master")]
                            (-> ref
                                (.getObjectId)
                                (jgit-utils/commit-id)))]
        {:commit latest-commit}))))

(defn commit-on-agent
  [commit-config latest-commits-cache]
  (commit-repo commit-config)
  (reset! latest-commits-cache (latest-commit-data commit-config)))

(schema/defn ^:always-validate start-periodic-commit-process!
  [{:keys [commit-interval] :as config}
   {:keys [shutdown-requested? latest-commits-cache]}]
  (while (not (realized? shutdown-requested?))
    (commit-on-agent config latest-commits-cache)
    (Thread/sleep commit-interval)))


(schema/defn ^:always-validate latest-commits-handler
  [latest-commits-cache :- Atom]
  (->> (comidi/routes
        (comidi/GET "latest-commits" request
                    (fn [request]
                      (log/info "Handling request for latest-commits")
                      {:status  200
                       :headers {"Content-Type" "text/plain"}
                       :body    (:commit @latest-commits-cache)}))
        (comidi/not-found "Not Found"))
      (comidi/context "/")
      (comidi/routes->handler)))