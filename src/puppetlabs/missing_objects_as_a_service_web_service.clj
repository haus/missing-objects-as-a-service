(ns puppetlabs.missing-objects-as-a-service-web-service
  (:import
    (org.eclipse.jgit.http.server GitServlet))
  (:require [clojure.tools.logging :as log]
            [puppetlabs.comidi :as comidi]
            [puppetlabs.missing-objects-as-a-service-web-core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services :as tk-services]))

(trapperkeeper/defservice hello-web-service
  [[:ConfigService get-in-config]
   [:WebserverService add-servlet-handler]
   HelloService]
  (init [this context]
    (log/info "Initializing hello webservice")
    (add-servlet-handler this (GitServlet.) "our-git-repo"))

  (start [this context]
         (let [host (get-in-config [:webserver :host])
               port (get-in-config [:webserver :port])
               url-prefix (get-route this)]
              (log/infof "Hello web service started; visit http://%s:%s%s/world to check it out!"
                         host port url-prefix))
         context))
