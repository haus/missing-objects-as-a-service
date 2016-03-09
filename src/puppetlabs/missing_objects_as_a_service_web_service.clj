(ns puppetlabs.missing-objects-as-a-service-web-service
  (:import
    (org.eclipse.jgit.http.server GitServlet))
  (:require [clojure.tools.logging :as log]
            [puppetlabs.comidi :as comidi]
            [puppetlabs.missing-objects-as-a-service-web-core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [me.raynes.fs :as fs]
            [puppetlabs.missing-objects-as-a-service-common :as common]))

(trapperkeeper/defservice
  jgit-web-service
  [[:ConfigService get-in-config]
   [:WebserverService add-servlet-handler add-ring-handler]
   [:SchedulerService after stop-job]
   [:ShutdownService request-shutdown]
   HelloService]
  (init [this context]
        (log/info "Initializing jgit servlet")
        (let [base-path (get-in-config [:jgit-service :base-dir])
              repo-id (get-in-config [:jgit-service :repo-id])
              repo-mount (get-in-config [:jgit-service :repo-mount])
              jgit-config (get-in-config [:jgit-service])
              scheduled-jobs-completed? (promise)
              ;latest-commits-data (core/latest-commit-data jgit-config)
              latest-commits-cache (atom nil)
              context* (assoc context
                         :scheduled-jobs-state {:shutdown-requested? (atom false)
                                                :jobs-scheduled? (atom false)
                                                :scheduled-jobs-completed? scheduled-jobs-completed?}
                         :latest-commits-cache latest-commits-cache
                         :commit-agent (common/create-agent
                                         request-shutdown scheduled-jobs-completed? "commit-agent"))]
          (fs/mkdir base-path)
          (core/initialize-repos! jgit-config)
          (fs/touch (str (core/bare-repo-path base-path repo-id) "/" "fooo"))
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
               commit-interval (get-in-config [:jgit-service :commit-interval] 5000)
               schedule-fn (partial after commit-interval)]
           (log/infof "JGit servlet started; `git clone http://%s:%s%s/%s.git` to check it out!"
                      host port repo-mount repo-id)

           (core/start-periodic-commit-process! schedule-fn jgit-config context)
           (log/infof "Commit agent started...commiting every %s milliseconds" commit-interval)

           context)))
