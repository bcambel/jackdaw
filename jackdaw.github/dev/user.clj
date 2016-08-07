(ns user
  (:require [clojure.string :as s]
            [clojure.stacktrace :refer [print-stack-trace print-cause-trace]]
            [jackdaw.github :refer :all]))

(try
  (require '[clojure.tools.namespace.repl :refer [refresh]])
  (catch Exception e nil))
