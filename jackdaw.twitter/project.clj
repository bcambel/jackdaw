(defproject jackdaw.twitter "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
    [cheshire "5.6.3"]
    ; [twitter-api "0.7.8" :exclusions [commons-codec]]
    [com.taoensso/timbre "4.5.1"]
    [com.twitter/hbc-core "2.2.0"]
    [mvxcvi/blocks "0.7.1"]
  ]
  ; :main ^:skip-aot jackdaw.twitter
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}

          :dev { :source-paths ["dev"]
          :dependencies [ [org.clojure/tools.namespace "0.2.11"]
                          [rhizome "0.2.7"]]}
  })
