(defproject jackdaw.rss "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  ; :main ^:skip-aot jackdaw.rss
  :dependencies [[org.clojure/clojure "1.8.0"]
      [com.rometools/rome "1.6.1"]
      [com.taoensso/timbre "4.5.1"]
      [mvxcvi/blocks "0.7.1"]
      [com.climate/claypoole "1.1.3"]
  ]

  :target-path "target/%s"
  :profiles {:dev  [:project/dev  :profiles/dev]
   :test [:project/test :profiles/test]
   :uberjar {:aot :all}
   :profiles/dev  {}
   :profiles/test {}
   :project/dev   {:dependencies [
                                  [reloaded.repl "0.2.2"]
                                  [org.clojure/tools.namespace "0.2.11"]
                                  [org.clojure/tools.nrepl "0.2.12"]
                                  [eftest "0.1.1"]
                                  [com.gearswithingears/shrubbery "0.3.1"]
                                  [kerodon "0.7.0"]]
                   :source-paths   ["dev"]
                   :resource-paths ["dev/resources"]
                   :repl-options {:init-ns user}
                   :env {:port "3000"}}
   :project/test  {}}
  )
