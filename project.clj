(defproject jackdaw "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.cli "0.3.5"]
                 [io.netty/netty-all "4.1.4.Final"]
                 [less-awful-ssl "1.0.0"]
                 [interval-metrics "1.0.0"]
                 [slingshot "0.12.2"]
                 [cheshire "5.6.0"]
                 [clj-time "0.12.0"]
                 [com.taoensso/timbre "4.5.1"]
                 [com.google.protobuf/protobuf-java "2.6.1"]
                 [flake "0.4.3"]
                 [mvxcvi/multicodec "0.5.1"]
                 [mvxcvi/multihash "2.0.1"]
                 [mvxcvi/blocks "0.7.1"]
                 [byte-streams "0.2.2"]
                 [gloss "0.2.6"]
                 [clojurewerkz/buffy "1.0.2" :exclusions [[io.netty/netty-buffer ]
                                                          [io.netty/netty-common]]]
                 [pandect "0.6.0"]
  ]
  :java-source-paths ["src_java"]
  ; :plugins [[lein-protobuf "0.5.0"]]
  ; :main ^:skip-aot jackdaw.core
  :target-path "target/%s"
  :hiera
  {:vertical false
   :cluster-depth 2
   :ignore-ns #{clojure}
   :show-external false}

  :whidbey
  {:tag-types {'blocks.data.Block {'blocks.data.Block (partial into {})}
               'merkledag.link.LinkIndex {'data/link-index :index}
               'merkledag.link.MerkleLink {'data/link 'merkledag.link/link->form}
               'multihash.core.Multihash {'data/hash 'multihash.core/base58}
               'org.joda.time.DateTime {'inst str}}}
  :profiles {:uberjar {:aot :all}

          :dev {:source-paths ["dev"]
                :dependencies [ [org.clojure/tools.namespace "0.2.11"]
                                [rhizome "0.2.7"]]}
  })
