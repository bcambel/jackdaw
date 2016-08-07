(ns jackdaw.twitter
  (:require [taoensso.timbre :as log]
            [cheshire.core :as json]
            [blocks.core :as block]
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

(defn store! [msg]
  (let [{:keys [text id user] :as message} (json/parse-string msg true)]
    (block/store! fs (str message))
    (println id text))
  )

(defn consume [tracking following consumerKey consumerSecret  token secret]
  (let [auth (OAuth1. consumerKey  consumerSecret  token  secret)
        endpoint (doto (StatusesFilterEndpoint.)
                  (.followings following)
                  (.trackTerms tracking))
        client-builder (ClientBuilder.)
        queue (LinkedBlockingQueue. (int 1e5))]

    (let [_   (doto client-builder
                (.hosts (Constants/STREAM_HOST))
                (.authentication auth)
                (.endpoint endpoint)
                (.processor (StringDelimitedProcessor. queue)))
                  client (.build client-builder)]
    (log/info "Connecting..")
    (.connect client)
    (log/info "Connected..")
    (while true
      (when-let [msg (.take queue)]
        (store! msg)

        (log/sometimes 0.4 (log/infof "Queue has %d items " (.size queue)))
        (Thread/sleep 10))
        )


    (fn []
      (.stop client))
  ))
  )






(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
