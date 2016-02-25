(def ks-version "1.3.0")
(def tk-version "1.3.0")
(def tk-jetty9-version "1.5.2")
(def jgit-version "4.1.0.201509280440-r")

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
                 [puppetlabs/comidi "0.3.1"]
                 [org.eclipse.jgit/org.eclipse.jgit.http.server ~jgit-version]
                 [org.eclipse.jgit/org.eclipse.jgit.http.apache ~jgit-version]]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[puppetlabs/trapperkeeper ~tk-version :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test" :scope "test"]
                                  [clj-http "2.1.0"]
                                  [org.clojure/tools.namespace "0.2.4"]
                                  [ring-mock "0.1.5"]]}}

  :repl-options {:init-ns user}

  :aliases {"tk" ["trampoline" "run" "--config" "dev-resources/config.conf"]}

  :main puppetlabs.trapperkeeper.main

  )
