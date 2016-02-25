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
           (org.eclipse.jgit.lib PersonIdent)))

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
              repo-id :- schema/Keyword]
             (fs/file data-dir (str (name repo-id) ".git")))

(defn initialize-repos!
             "Initialize the repositories managed by this service.  For each repository ...
               * There is a directory under data-dir (specified in config) which is actual Git
                 repository (git dir).
               * If staging-dir does not exist, it will be created.
               * If there is not an existing Git repo under data-dir,
                 'git init' will be used to create one."
  [repo-id data-dir]
  (log/infof "Initializing git data dir: %s" data-dir)
  (initialize-data-dir! (fs/file data-dir))
  (let [git-dir (bare-repo-path data-dir repo-id)]
    (log/infof "Initializing Git repository at %s" git-dir)
    (initialize-bare-repo! git-dir)))

(defn get-repo
  [path]
  (.getRepository (Git/open (io/as-file path))))

(schema/defn identity->person-ident :- PersonIdent
  [{:keys [name email]} :- Identity]
  (PersonIdent. name email))

(schema/defn commit-repo
  [repo-id :- schema/Keyword
   data-dir :- schema/Str
   commit-info :- {:msg schema/Str :committer PersonIdent}]
  (let [git-dir (bare-repo-path data-dir repo-id)
        {:keys [msg committer]} commit-info]
    (log/infof "Committing all files in '%s' to repo '%s'" git-dir repo-id)
    (with-open [git-repo (get-repo git-dir)]
      (let [git (Git/wrap git-repo)]
        (jgit-utils/add-and-commit git msg committer)))))