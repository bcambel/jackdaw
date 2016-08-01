(defproject jackdaw "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [io.netty/netty-all "4.1.4.Final"]
                 [less-awful-ssl "1.0.0"]
                 [interval-metrics "1.0.0"]
                 [slingshot "0.12.2"]
                 [cheshire "5.6.0"]
                 [clj-time "0.12.0"]
                 [com.taoensso/timbre "4.5.1"]
                 [com.google.protobuf/protobuf-java "2.6.1"]
  ]
  :java-source-paths ["src_java"]
  ; :plugins [[lein-protobuf "0.5.0"]]
  :main ^:skip-aot jackdaw.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
