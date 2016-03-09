(ns puppetlabs.missing-objects-as-a-service-client
  (:require [clojure.tools.logging :as log]
            [puppetlabs.missing-objects-as-a-service-client-core :as core]
            [puppetlabs.missing-objects-as-a-service-common :as common]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.http.client.sync :as sync]))

(defprotocol JGitClient)

(trapperkeeper/defservice
  jgit-client
  JGitClient
  [[:ConfigService get-in-config]
   [:ShutdownService request-shutdown]
   [:SchedulerService after stop-job]
   [:HelloService]]
  (init [this context]
        (log/info "Initializing hello service")
        context
        (let
            [scheduled-jobs-completed? (promise)
             http-client-atom (atom nil)
             context* (assoc context
                        :http-client-atom http-client-atom
                        :scheduled-jobs-state {:shutdown-requested? (atom false)
                                               :jobs-scheduled? (atom false)
                                               :scheduled-jobs-completed? scheduled-jobs-completed?}
                        :sync-agent (common/create-agent
                                      request-shutdown scheduled-jobs-completed? "sync-agent"))]
            context*))
  (start [this context]
         (let [poll-interval (get-in-config [:jgit-client :poll-interval])
               config (get-in-config [:jgit-client])
               schedule-fn (partial after poll-interval)]
           (reset! (:http-client-atom context) (sync/create-client {}))
           (log/infof "Starting jgit client service. Fetching every %s milliseconds" poll-interval)
           (core/start-periodic-sync-process! schedule-fn config context)
           context))
  (stop [this context]
        (log/info "Shutting down jgit client service")
        context))