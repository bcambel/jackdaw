(ns jackdaw.client
  (:require [taoensso.timbre :as log]
            [jackdaw.transport :refer :all])
  (:import [java.net InetSocketAddress InetAddress UnknownHostException]
           [java.util List]

           [java.util.concurrent Executors TimeUnit]
           [java.nio.channels ClosedChannelException]
           (javax.net.ssl SSLContext)
           [io.netty.bootstrap Bootstrap]
           [io.netty.buffer ByteBufUtil ByteBufInputStream]
           [io.netty.channel Channel ChannelOption ChannelFuture
                             ChannelInitializer ChannelHandler
                             ChannelHandlerContext ChannelFutureListener
                             ChannelInboundHandlerAdapter ChannelPipeline
                             SimpleChannelInboundHandler]
           [io.netty.channel.group ChannelGroup
                                   DefaultChannelGroup]

           (io.netty.handler.codec MessageToMessageDecoder
                                  LengthFieldBasedFrameDecoder
                                  LengthFieldPrepender
                                  MessageToMessageEncoder Delimiters
                                  DelimiterBasedFrameDecoder)
           [io.netty.handler.codec.string StringDecoder StringEncoder]
           [io.netty.handler.logging LoggingHandler LogLevel]
           [io.netty.handler.ssl SslHandler]

          ;  [io.netty.channel.epoll EpollEventLoopGroup EpollServerSocketChannel]
           [io.netty.channel.nio NioEventLoopGroup ]
           (io.netty.channel.socket DatagramPacket SocketChannel)

           [io.netty.channel.socket.nio NioServerSocketChannel NioSocketChannel]
           (io.netty.util ReferenceCounted)
           (io.netty.util.concurrent Future
                              EventExecutorGroup
                              DefaultEventExecutorGroup
                              ImmediateEventExecutor)
           )
  )


(defprotocol Client (connect! [_]) (disconnect! [_]))

(defrecord TCPClient [host port initializer killer channel]
  Client
  (disconnect! [this]
    (locking this
      (when @killer
        (@killer)
        (reset! killer nil))))

  (connect! [this]
    (log/infof "Initializing TCP Conn to Host:%s:%s" host port)
    (let [group (NioEventLoopGroup.)
          bootstrap (Bootstrap. )]
          (doto bootstrap
             (.group group)
             (.channel NioSocketChannel)
             (.handler (initializer)))
          (let [ch
                  (->> (InetSocketAddress. host port )
                       (.connect bootstrap)
                       (.sync)
                       (.channel))]
            (assoc this :channel ch)
          )

          (log/info "TCP server" host port "online")
          this
          ; (reset! killer
          ;         (fn killer []
          ;           (.shutdownGracefully group)
          ;           (log/info "TCP server" host port "shut down")))
                    )))
