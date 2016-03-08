(ns puppetlabs.missing-objects-as-a-service-client
  (:require [clojure.tools.logging :as log]
            [puppetlabs.missing-objects-as-a-service-client-core :as core]
            [puppetlabs.missing-objects-as-a-service-common :as common]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]))

(defprotocol JGitClient)

(trapperkeeper/defservice
  jgit-client
  JGitClient
  [[:ShutdownService request-shutdown]]
  (init [this context]
        (log/info "Initializing hello service")
        context
        #_(let
            [scheduled-jobs-completed? (promise)
             context* (assoc context
                        :sync-agent (common/create-agent
                                      request-shutdown scheduled-jobs-completed? "sync-agent"))]
            context*))
  (start [this context]
         (log/info "Starting jgit client service")
         context
         #_(let [config (get-in-config [:file-sync])
                 client-config (:client config)
                 poll-interval (:poll-interval client-config)
                 schedule-fn (partial after poll-interval)]
             (when (< 0 poll-interval)
               (core/start-periodic-sync-process!
                 schedule-fn config context))
             (assoc context :config config
                            :started? true)))
  (stop [this context]
        (log/info "Shutting down jgit client service")
        context))