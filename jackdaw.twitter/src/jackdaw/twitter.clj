(ns jackdaw.twitter
  (:require [taoensso.timbre :as log]
            [cheshire.core :as json]
            [blocks.core :as block]
            [clojure.string :as s]
            )
  (:use blocks.store.file)
  (:import [com.google.common.collect Lists]
            [com.twitter.hbc ClientBuilder]
            [com.twitter.hbc.core Client Constants]
            [com.twitter.hbc.core.endpoint StatusesFilterEndpoint]
            [com.twitter.hbc.core.processor StringDelimitedProcessor]
            [com.twitter.hbc.httpclient.auth Authentication OAuth1]
            [java.util.concurrent BlockingQueue LinkedBlockingQueue])
  (:gen-class)
  )

(def fs (file-store ".store"))

(defn store! [msg-list]
  ; (log/info msg-list)
  (let [messages (mapv (fn [msg] (json/parse-string msg true)) msg-list)]
    ; (log/info messages)
    (block/store! fs (str messages))
    (log/infof "Written %d tweets: %s.. " (count messages) (s/join #"," (take 3 (mapv (comp str :id) messages)))))
  )

; (def queue (LinkedBlockingQueue. (int 1e5)))

(defn consume [tracking following consumerKey consumerSecret  token secret]
  (let [auth (OAuth1. consumerKey  consumerSecret  token  secret)
        endpoint (doto (StatusesFilterEndpoint.)
                  (.followings following)
                  (.trackTerms tracking))
        client-builder (ClientBuilder.)
        queue (LinkedBlockingQueue. (int 1e5))
        ]

    (let [_   (doto client-builder
                (.hosts (Constants/STREAM_HOST))
                (.authentication auth)
                (.endpoint endpoint)
                (.processor (StringDelimitedProcessor. queue)))
                  client (.build client-builder)]
    (log/info "Connecting..")
    (.connect client)
    (log/info "Connected.." client)
    (while true
      (let [temp-list (java.util.ArrayList.)
            ; msgs (.toArray queue temp-list)
            ]
        ; (store! temp-list)
        ; (store! (seq (.toArray queue)))
        ; (.clear queue)
        (log/sometimes 0.4 (log/infof "Queue has %d items " (.size queue)))

        (when-let [size (.size queue) ]
          (loop [i 0 el []]
            (if (> i size)
              (store! el)
              (recur (inc i) (conj el (.take queue))))))

        (Thread/sleep 5000)
        )
        )


    (fn []
      (.stop client))
  ))
  )


(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
