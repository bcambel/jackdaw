(ns jackdaw.server
  (:require [taoensso.timbre :as log]
            [jackdaw.transport :refer :all])
  (:import [java.net InetSocketAddress InetAddress UnknownHostException]
           [java.util List]

           [java.util.concurrent Executors TimeUnit]
           [java.nio.channels ClosedChannelException]
           (javax.net.ssl SSLContext)
           [io.netty.bootstrap ServerBootstrap]
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
           [io.netty.channel.socket.nio NioServerSocketChannel]
           (io.netty.util ReferenceCounted)
           (io.netty.util.concurrent Future
                              EventExecutorGroup
                              DefaultEventExecutorGroup
                              ImmediateEventExecutor)
           )
  )


(defprotocol Server (start! [_]) (stop! [_]))

(defrecord TCPServer [host port handler channel-group killer]
  Server
  (stop! [this]
    (locking this
      (when @killer
        (@killer)
        (reset! killer nil))))

  (start! [this]
    (let [boss-grp (NioEventLoopGroup. 1)
          worker-grp (NioEventLoopGroup. )
          bootstrap (ServerBootstrap. )]
          (doto bootstrap
             (.group boss-grp worker-grp)
             (.channel NioServerSocketChannel)
             (.option ChannelOption/SO_REUSEADDR true)
             (.option ChannelOption/TCP_NODELAY true)
             (.option ChannelOption/SO_BACKLOG (int 5))
             (.childOption ChannelOption/SO_REUSEADDR true)
             (.childOption ChannelOption/TCP_NODELAY true)
             (.childOption ChannelOption/SO_KEEPALIVE true)
             (.handler (LoggingHandler.))
             (.childHandler handler))

          (->> (InetSocketAddress. host port )
               (.bind bootstrap)
               (.sync)
               (.channel)
               (.add channel-group))

          (log/info "TCP server" host port "online")
          (reset! killer
                  (fn killer []
                    (.. channel-group close awaitUninterruptibly)
                    ; Shut down workers and boss concurrently.
                    (let [w (shutdown-event-executor-group worker-grp)
                          b (shutdown-event-executor-group boss-grp)]
                      @w
                      @b)
                    (log/info "TCP server" host port "shut down"))))))
