(ns puppetlabs.missing-objects-as-a-service-web-service
  (:import
    (org.eclipse.jgit.http.server GitServlet))
  (:require [clojure.tools.logging :as log]
            [puppetlabs.missing-objects-as-a-service-web-core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [me.raynes.fs :as fs]))

(trapperkeeper/defservice
  jgit-web-service
  [[:ConfigService get-in-config]
   [:WebserverService add-servlet-handler add-ring-handler]]
  (init [this context]
        (log/info "Initializing jgit servlet")
        (let [base-path (get-in-config [:jgit-service :base-dir])
              repo-id (get-in-config [:jgit-service :repo-id])
              repo-mount (get-in-config [:jgit-service :repo-mount])
              jgit-config (get-in-config [:jgit-service])
              latest-commits-cache (atom nil)
              context* (assoc context
                         :latest-commits-cache latest-commits-cache
                         :shutdown-requested? (promise))]
          (fs/mkdir base-path)
          (core/initialize-repos! jgit-config)
          (fs/touch (str (core/live-repo-path base-path repo-id) "/" "fooo"))
          (core/commit-repo jgit-config)
          (add-servlet-handler (GitServlet.) repo-mount
                               {:servlet-init-params {"base-path" base-path "export-all" "1"}})
          (add-ring-handler (core/latest-commits-handler latest-commits-cache) "")
          context*))

  (start [this context]
         (let [host (get-in-config [:webserver :host])
               port (get-in-config [:webserver :port])
               jgit-config (get-in-config [:jgit-service])
               repo-id (get-in-config [:jgit-service :repo-id])
               repo-mount (get-in-config [:jgit-service :repo-mount])
               commit-interval (get-in-config [:jgit-service :commit-interval] 5000)]
           (log/infof "JGit servlet started; `git clone http://%s:%s%s/%s.git` to check it out!"
                      host port repo-mount repo-id)

           (log/infof "Commit agent starting...commiting every %s milliseconds" commit-interval)
           (future (core/start-periodic-commit-process! jgit-config context))

           context))

  (stop [this context]
        (log/info "Shutting down jgit client service")
        (deliver @(:shutdown-requested? context) true)
        context))
