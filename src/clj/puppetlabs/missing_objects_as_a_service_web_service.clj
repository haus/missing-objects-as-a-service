(ns puppetlabs.missing-objects-as-a-service-web-service
  (:import
    (org.eclipse.jgit.http.server GitServlet))
  (:require [clojure.tools.logging :as log]
            [puppetlabs.missing-objects-as-a-service-web-core :as web-core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [me.raynes.fs :as fs]
            [puppetlabs.http.client.sync :as sync]
            [puppetlabs.missing-objects-as-a-service-client-core :as client-core]))

(trapperkeeper/defservice
  jgit-web-service
  [[:ConfigService get-in-config]
   [:WebserverService add-servlet-handler add-ring-handler]]
  (init [this context]
        (log/info "Initializing jgit servlet")
        (let [base-path (get-in-config [:jgit-service :base-dir])
              repo-id (get-in-config [:jgit-service :repo-id])
              repo-mount (get-in-config [:jgit-service :repo-mount])
              server-config (get-in-config [:jgit-service])
              latest-commits-cache (atom nil)
              context* (assoc context
                         :latest-commits-cache latest-commits-cache
                         :shutdown-requested? (promise)
                         :http-client-atom (atom nil))]
          (fs/mkdir base-path)
          (web-core/initialize-repos! server-config)
          (fs/copy-dir "./dev-resources/code-staging" (web-core/live-repo-path base-path repo-id))
          (web-core/commit-repo server-config)
          (add-servlet-handler (GitServlet.) repo-mount
                               {:servlet-init-params {"base-path" base-path "export-all" "1"}})
          (add-ring-handler (web-core/latest-commits-handler latest-commits-cache) "")
          context*))

  (start [this context]
         (let [host (get-in-config [:webserver :host])
               port (get-in-config [:webserver :port])
               server-config (get-in-config [:jgit-service])
               repo-id (get-in-config [:jgit-service :repo-id])
               repo-mount (get-in-config [:jgit-service :repo-mount])
               commit-interval (get-in-config [:jgit-service :commit-interval] 5000)
               poll-interval (get-in-config [:jgit-client :poll-interval])
               client-config (get-in-config [:jgit-client])
               num-clients (get-in-config [:jgit-client :num-clients])]
           (log/infof "JGit servlet started; `git clone http://%s:%s%s/%s.git` to check it out!"
                      host port repo-mount repo-id)

           (reset! (:http-client-atom context) (sync/create-client {}))

           ;; Wait for startup?

           (log/infof "Commit agent starting...commiting every %s milliseconds" commit-interval)
           (future (web-core/start-periodic-commit-process! server-config context))

           ;; Wait for startup?

           (doseq [i (range 1 (inc num-clients))]
             (log/infof "Starting jgit client service #%s. Fetching every %s milliseconds" i poll-interval)
             (future (client-core/start-periodic-sync-process! i client-config context)))

           context))

  (stop [this context]
        (log/info "Shutting down jgit client service")
        (deliver (:shutdown-requested? context) true)
        context))
