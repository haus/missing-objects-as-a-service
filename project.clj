(def ks-version "1.3.0")
(def tk-version "1.3.0")
(def tk-jetty9-version "1.5.2")

(defproject puppetlabs/missing-objects-as-a-service "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [puppetlabs/trapperkeeper ~tk-version]
                 [puppetlabs/trapperkeeper-webserver-jetty9 ~tk-jetty9-version]
                 [puppetlabs/ring-middleware "0.2.0"]
                 [ring/ring-core "1.4.0" :exclusions [org.clojure/clojure]]
                 [puppetlabs/http-client "0.5.0"]
                 [puppetlabs/comidi "0.3.1"]]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[puppetlabs/trapperkeeper ~tk-version :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test" :scope "test"]
                                  [clj-http "0.9.2"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [ring-mock "0.1.5"]]}}

  :repl-options {:init-ns user}

  :aliases {"tk" ["trampoline" "run" "--config" "dev-resources/config.conf"]}

  :main puppetlabs.trapperkeeper.main

  )
