(ns user
  (:require [clojure.string :as s]
            [clojure.stacktrace :refer [print-stack-trace print-cause-trace]]
            [jackdaw.twitter :refer :all])
  (:import [com.google.common.collect Lists]))

(try
  (require '[clojure.tools.namespace.repl :refer [refresh]])
  (catch Exception e nil))

(def *app-consumer-key* (System/getenv "CONSUMER_KEY"))
(def *app-consumer-secret* (System/getenv "CONSUMER_SECRET"))
(def *user-access-token* (System/getenv "ACCESS_KEY"))
(def *user-access-secret* (System/getenv "ACCESS_SECRET"))

(def user-int-ids (map s/trim (s/split (s/replace (slurp ".users.json") #"," "") #"\n")))

(def lucky-users  (Lists/newArrayList (into-array Long (map #(Long/parseLong %)  (take 1000 user-int-ids )))))

(def tracking (Lists/newArrayList (into-array String ["twitterapi" "#python" "#bigdata" "#clojure"
                                                      "#java" "#startups" "#entrepreneurship"
                                                      "#tech" "#iot" "#coding" "#analytics" "#cloud"
                                                      "#data" "#hadoop" "#spark" "#opensource" "#unix"
                                                      "#quote" "#database" "#web" "#design" "#ux" "#ui"
                                                      "#security" "#zeroday" "#defcon" "#wikileaks"
                                                      ])))

(def m (partial consume tracking lucky-users
                *app-consumer-key* *app-consumer-secret* *user-access-token* *user-access-secret*))
