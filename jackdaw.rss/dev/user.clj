(ns user
  (:require [clojure.string :as s]
            [clojure.stacktrace :refer [print-stack-trace print-cause-trace]]
            [com.climate.claypoole :as cp]
            [jackdaw.rss :refer :all]))

(try
  (require '[clojure.tools.namespace.repl :refer [refresh]])
  (catch Exception e nil))

(defn read [f]
  (-> (slurp f)
      (s/split #"\n"))
  )
(def rss-list )

(defn takeoff [f]
  (let [rss-list (read f)]
    (map get-rss rss-list)))
