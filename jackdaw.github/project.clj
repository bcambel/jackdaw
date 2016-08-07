(defproject jackdaw.github "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                  [cheshire "5.6.3"]
                  [com.taoensso/timbre "4.7.3"]
                  [slingshot "0.12.2"]
                  [clj-http "3.1.0"]
                  [clj-time "0.12.0"]
                  [mvxcvi/blocks "0.7.1"]
                  [environ "1.1.0"]
  ]

  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
          :dev { :source-paths ["dev"]
          :plugins [[lein-environ "1.1.0"]]
          :dependencies [ [org.clojure/tools.namespace "0.2.11"]
                          [rhizome "0.2.7"]]}
  }

  :repl-options {
     :prompt (fn [ns] (str "\u001B[35m[\u001B[34m" ns "\u001B[35m]\u001B[33mcλ:\u001B[m " ))

   }
  )
