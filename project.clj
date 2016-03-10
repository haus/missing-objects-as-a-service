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
                 ;; Transitive conflict resolutions
                 [org.apache.httpcomponents/httpclient "4.5.1"]
                 [clj-time "0.11.0"]
                 [org.apache.httpcomponents/httpcore "4.4.4"]
                 [org.clojure/tools.macro "0.1.5"]
                 [puppetlabs/typesafe-config "0.1.4"]
                 [ring/ring-servlet "1.3.0"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 ;; End conflict resolutions

                 [org.clojure/tools.logging "0.3.1"]
                 [puppetlabs/trapperkeeper ~tk-version]
                 [puppetlabs/trapperkeeper-webserver-jetty9 ~tk-jetty9-version]
                 [puppetlabs/trapperkeeper-scheduler "0.0.1"]
                 [puppetlabs/ring-middleware "0.2.0"]
                 [ring/ring-core "1.4.0" :exclusions [org.clojure/clojure]]
                 [puppetlabs/http-client "0.5.0"]
                 [puppetlabs/comidi "0.3.1"]
                 [org.eclipse.jgit/org.eclipse.jgit.http.server ~jgit-version]
                 [org.eclipse.jgit/org.eclipse.jgit.http.apache ~jgit-version]
                 [prismatic/schema "1.0.5"]
                 [puppetlabs/pe-file-sync "0.1.7"]]

  :pedantic? :abort

  :source-paths ["src/clj"]
  :java-source-paths ["src/java"]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[puppetlabs/trapperkeeper ~tk-version :classifier "test" :scope "test"]
                                  [puppetlabs/kitchensink ~ks-version :classifier "test" :scope "test"]
                                  [org.clojure/tools.namespace "0.2.4"]]}}

  :repositories [["releases" "http://nexus.delivery.puppetlabs.net/content/repositories/releases/"]
                 ["snapshots" "http://nexus.delivery.puppetlabs.net/content/repositories/snapshots/"]]

  :repl-options {:init-ns user}

  :aliases {"tk" ["trampoline" "run" "--config" "dev-resources/config.conf"]}

  :main puppetlabs.trapperkeeper.main

  )
