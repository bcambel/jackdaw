(ns jackdaw.protobufing
  (:use [jackdaw.server]
        [jackdaw.client])
  (:require [taoensso.timbre :as log]
            [jackdaw.transport :refer :all]
            [cheshire.core :refer [generate-string parse-string]]
            )
  (:import [com.jackdaw Data$Entry Data$EntryAck]
           [io.netty.handler.codec.protobuf ProtobufDecoder
                             ProtobufEncoder
                             ProtobufVarint32FrameDecoder
                             ProtobufVarint32LengthFieldPrepender]
          (io.netty.channel.socket DatagramPacket SocketChannel)
           [io.netty.channel Channel ChannelOption ChannelFuture
                             ChannelInitializer ChannelHandler ChannelInboundHandler
                             ChannelHandlerContext ChannelFutureListener
                             ChannelInboundHandlerAdapter ChannelPipeline
                             SimpleChannelInboundHandler]
                             )
  )


(defn protobuf-decoder
  "Decodes protobufs to Msg objects"
  []
  (ProtobufDecoder. (Data$Entry/getDefaultInstance)))

(defn protobuf-encoder
  "Encodes protobufs to Msg objects"
  []
  (ProtobufEncoder.))

(def last-rec (atom nil))

(defn pbuff-chan-handler []
  (proxy [ChannelInboundHandlerAdapter] []
    (channelActive [^ChannelHandlerContext ctx]
      (log/info "Channel active")
      ; (.write ctx "Welcome to my server!\r\n")
      ; (.write ctx "Time is now! Live in the moment..")
      ; (.flush ctx)
      )
    (channelInactive [^ChannelHandlerContext ctx]
      (log/info "Channel InActive")
      )
    (channelRead [ctx req]
      (log/info "Received from CLIENT!!!!")
      (log/info req)
      (reset! last-rec req)
      (let [id (.getId req)
            ack (doto (Data$EntryAck/newBuilder)
                  (.setId id)
                  (.setStatus "ok"))]
          (.write ctx (.build ack))
          (.flush ctx)))
    (channelReadComplete [ctx]
      (.flush ctx))
    (exceptionCaught [ctx cause]
      (log/info (.printStackTrace cause))
      (log/info "Oooppsss")
      ; (.close ctx)
      )
    (isSharable [] true)))

(defn pbuff-channel-initializer []
  (let [pr-decoder (protobuf-decoder)
        pr-encoder (protobuf-encoder)]
    (proxy [ChannelInitializer] []
      (initChannel [^SocketChannel ch]
        (let [handler (pbuff-chan-handler)
              pipe (.pipeline ^Channel ch)]
            (.addLast pipe (into-array ChannelHandler [(ProtobufVarint32FrameDecoder.)]))
            (.addLast pipe (into-array ChannelHandler [pr-decoder]))
            (.addLast pipe (into-array ChannelHandler [(ProtobufVarint32LengthFieldPrepender.)]))
            (.addLast pipe (into-array ChannelHandler [pr-encoder]))
            (.addLast pipe (into-array ChannelHandler [handler]))
            ch)))))



(defn entry [] (let [id (rand-int 6e5)] (doto (Data$Entry/newBuilder)
              (.setId (str id))
              (.setMultiaddr "/ip4/XX.YY.ZZZ.YY/tcp/2343")
              (.setMulticodec "/json")
              (.setLink "")
              (.setSource "me")
              (.setData (generate-string {:ts (str (java.util.Date.)) :id id  }) ))))

(defn client-handler []
  (proxy [SimpleChannelInboundHandler] []
    (channelActive [  ctx]
      (log/info "Channel Active ")
      (log/info "Send entry to Server")
      (.start (Thread. (fn[]
        (loop [i 0]
          (when (< i 10)
              (.write ctx (.build (entry)))
              (.flush ctx)
              (Thread/sleep 3000)
            (recur (inc i))))))))
    (channelRegistered [  ctx]
      (let [ch (.channel ctx)]
          ;; do smth with the channel
          ; (assoc this :channel ch)

        )
      )
    (channelRead [ctx req]
      (log/infof "Server sent smth \n ========\n%s\n" req)
      )
    (exceptionCaught [ ctx cause]
      (.printStackTrace cause)
      (.close ctx))))



(defn client-initializer []
  (let [pr-decoder (ProtobufDecoder. (Data$EntryAck/getDefaultInstance))
        pr-encoder (protobuf-encoder)]
  (proxy [ChannelInitializer] []
    (initChannel [^SocketChannel sc]
      (let [pipe (.pipeline ^Channel sc)]
          (.addLast pipe (into-array ChannelHandler [(ProtobufVarint32FrameDecoder.)]))
          (.addLast pipe (into-array ChannelHandler [pr-decoder]))
          (.addLast pipe (into-array ChannelHandler [(ProtobufVarint32LengthFieldPrepender.)]))
          (.addLast pipe (into-array ChannelHandler [pr-encoder]))
          (.addLast pipe (into-array ChannelHandler [(client-handler)]))
          sc)))))

(defn kick-off [{:keys [server-port client-host client-port] }]
  (let [host "127.0.0.1"
        channel-group (channel-group (str "tcp-server " host ":" server-port))
        handler  (pbuff-channel-initializer)]
    (let [srv (->TCPServer  host server-port handler channel-group (atom {}))
          client (->TCPClient client-host client-port client-initializer nil nil )]
      [srv client])))
