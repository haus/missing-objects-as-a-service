(ns puppetlabs.missing-objects-as-a-service-web-service
  (:import
    (org.eclipse.jgit.http.server GitServlet))
  (:require [clojure.tools.logging :as log]
            [puppetlabs.comidi :as comidi]
            [puppetlabs.missing-objects-as-a-service-web-core :as core]
            [puppetlabs.trapperkeeper.core :as trapperkeeper]
            [puppetlabs.trapperkeeper.services :as tk-services]
            [me.raynes.fs :as fs]))

(trapperkeeper/defservice hello-web-service
  [[:ConfigService get-in-config]
   [:WebserverService add-servlet-handler]
   HelloService]
  (init [this context]
    (log/info "Initializing hello webservice")
        (let [base-path "base-path"]
          (fs/mkdir base-path)
          (core/initialize-repos! :our-repo base-path)
          (fs/touch (str (core/bare-repo-path base-path :our-repo) "/" "fooo"))
          (core/commit-repo :our-repo base-path {:msg "this is amaaaaazing" :committer (core/identity->person-ident {:name "justin" :email "foo.bar"})})
          (add-servlet-handler (GitServlet.) "/our-git-repo"
                               {:servlet-init-params {"base-path" base-path "export-all" "true"}}))
        context)

  (start [this context]
         (let [host (get-in-config [:webserver :host])
               port (get-in-config [:webserver :port])]
              (log/infof "Hello web service started; visit http://%s:%s/world to check it out!"
                         host port))
         context))
