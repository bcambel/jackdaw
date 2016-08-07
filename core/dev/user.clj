(ns user
  (:use [jackdaw.server]
        [jackdaw.client])
  (:require [clojure.stacktrace :refer [print-stack-trace print-cause-trace]]
            [jackdaw.protobufing :refer :all])
  )

(try
  (require '[clojure.tools.namespace.repl :refer [refresh]])
  (catch Exception e nil))

(defn startup [server-port client-port & {:keys [client-host]
                                          :or {client-host "127.0.0.1"}}]
  (let [[srv cli] (kick-off {:server-port server-port :client-host client-host  :client-port client-port} )]
    (def srv srv)
    (def cli cli)
    (start! srv)
    )
  )
