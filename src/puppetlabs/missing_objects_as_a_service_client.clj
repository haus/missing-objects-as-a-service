(ns puppetlabs.missing-objects-as-a-service-client
  (:require [clojure.tools.logging :as log]
            [puppetlabs.missing-objects-as-a-service-client-core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.http.client.sync :as sync]))

(defprotocol JGitClient)

(trapperkeeper/defservice
  jgit-client
  JGitClient
  [[:ConfigService get-in-config]]
  (init [this context]
        (log/info "Initializing jgit-client service")
        (assoc context
          :http-client-atom (atom nil)
          :shutdown-requested? (promise)))
  (start [this context]
         (let [poll-interval (get-in-config [:jgit-client :poll-interval])
               config (get-in-config [:jgit-client])]
           (reset! (:http-client-atom context) (sync/create-client {}))
           (log/infof "Starting jgit client service. Fetching every %s milliseconds" poll-interval)
           (future (core/start-periodic-sync-process! config context))
           context))
  (stop [this context]
        (log/info "Shutting down jgit client service")
        (deliver @(:shutdown-requested? context) true)
        context))