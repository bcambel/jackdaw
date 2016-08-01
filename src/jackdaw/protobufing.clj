(ns jackdaw.protobufing
  (:use [jackdaw.server]
        [jackdaw.client])
  (:require [taoensso.timbre :as log]
            [jackdaw.transport :refer :all]

            )
  (:import [com.jackdow Data$Entry]
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
      (log/info req))
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


(deftype ClientHandler [channel]
  ChannelInboundHandler
    (channelActive [ this ctx]
      (log/info "Channel Active "))
    (channelRegistered [ this ctx]
      (let [ch (.channel ctx)]
          ;; do smth with the channel
          (assoc this :channel ch)
        )
      )
    (channelRead [this  ctx req])
    (exceptionCaught [this ctx cause]
      (.printStackTrace cause)
      (.close ctx)))



(defn client-initializer []
  (let [pr-decoder (protobuf-decoder)
        pr-encoder (protobuf-encoder)]
  (proxy [ChannelInitializer] []
    (initChannel [^SocketChannel sc]
      (let [pipe (.pipeline ^Channel sc)]
          (.addLast pipe (into-array ChannelHandler [(ProtobufVarint32FrameDecoder.)]))
          (.addLast pipe (into-array ChannelHandler [pr-decoder]))
          (.addLast pipe (into-array ChannelHandler [(ProtobufVarint32LengthFieldPrepender.)]))
          (.addLast pipe (into-array ChannelHandler [pr-encoder]))
          ; (.addLast pipe (into-array ChannelHandler [(ClientHandler. nil)]))
          sc)))))

(defn kick-off []
  (let [host "127.0.0.1"
        port 12346
        channel-group (channel-group (str "tcp-server " host ":" port))
        handler  (pbuff-channel-initializer)]
    (let [srv (->TCPServer  host port handler channel-group (atom {}))
               client (->TCPClient host port client-initializer nil nil )]
      ; (start! srv)

      ; (thread
      ;   (Thread/sleep 5000)
      ;   (connect! client))

      [srv client])))
