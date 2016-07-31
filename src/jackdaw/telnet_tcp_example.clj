(ns jackdaw.telnet-tcp-example
  (:require [taoensso.timbre :as log])
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

(def last-buff (atom nil))

(defn timeserver-handler []
  (proxy [ChannelInboundHandlerAdapter] []
    (channelActive [^ChannelHandlerContext ctx]
      (let [time-byte-buf (-> ctx (.alloc) (.buffer 4)) ; a 4 byte buffer to write int
            now (+ 2208988800 (long (/ (System/currentTimeMillis) 1000)))]
        (.writeLong time-byte-buf now)
        (reset! last-buff now)
        (let [^ChannelFuture f (.writeAndFlush ctx time-byte-buf)]
          (.addListener f
            (reify ChannelFutureListener
              (operationComplete [this fut]
                (log/info (format "Closing conn %s" now))
                (.close ctx)))))))))

(defn telnet-tcp-handler []
  (proxy [ChannelInboundHandlerAdapter] []
    (channelActive [^ChannelHandlerContext ctx]
      (log/info "Channel active")
      (.write ctx "Welcome to my server!\r\n")
      (.write ctx "Time is now! Live in the moment..")
      (.flush ctx))

    (channelRead [ctx req]
      (condp = req
        "bye" (.write ctx "Have a nice day!\r\n")
        "time" (.write ctx (str "Now is" (java.util.Date. ) "\r\n"))
        "test" (.write ctx "123\r\n")
        (.write ctx (format "I missed what you mean by `%s` that\r\n" req))
        )
      (.flush ctx))
    (channelReadComplete [ctx]
      (.flush ctx)
        )
    (exceptionCaught [ctx cause]
      (.printStackTrace cause)
      (.close ctx)
      )
    (isSharable [] true)
    )
  )
(defn time-chan-init []
  (proxy [ChannelInitializer] []
    (initChannel [ch]
      (.addLast (.pipeline ^Channel ch)
          (into-array ChannelHandler [(timeserver-handler)]))
      )
  ))
;
(defn telnet-channel-initializer []
  (let [frame-decoder #(DelimiterBasedFrameDecoder. 8192 (Delimiters/lineDelimiter))]
    (proxy [ChannelInitializer] []
      (initChannel [ch]
        (let [handler (telnet-tcp-handler)
              pipe (.pipeline ^Channel ch)]

            (.addLast pipe (into-array ChannelHandler [(frame-decoder)]))
            (.addLast pipe (into-array ChannelHandler [(StringDecoder.)]))
            (.addLast pipe (into-array ChannelHandler [(StringEncoder.)]))

            (.addLast pipe (into-array ChannelHandler [handler]))

            ch)))))

(defn derefable
  "A simple wrapper for a netty future which on deref just calls
  (syncUninterruptibly f), and returns the future's result."
  [^Future f]
  (reify clojure.lang.IDeref
    (deref [_]
      (.syncUninterruptibly f)
      (.get f))))

(defn ^Future shutdown-event-executor-group
  "Gracefully shut down an event executor group. Returns a derefable future."
  [^EventExecutorGroup g]
  ; 10ms quiet period, 10s timeout.
  (derefable (.shutdownGracefully g 10 1000 TimeUnit/MILLISECONDS)))

(defn ^DefaultChannelGroup channel-group
  "Make a channel group with a given name."
  [name]
  (DefaultChannelGroup. name (ImmediateEventExecutor/INSTANCE)))
(defprotocol Server (start! [_]) (stop! [_]))

(defrecord TelnetServer [host port handler channel-group killer]
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
               (.add channel-group)

               )
          (log/info "TCP server" host port "online")
          (reset! killer
                  (fn killer []
                    (.. channel-group close awaitUninterruptibly)
                    ; Shut down workers and boss concurrently.
                    (let [w (shutdown-event-executor-group worker-grp)
                          b (shutdown-event-executor-group boss-grp)]
                      @w
                      @b)
                    (log/info "TCP server" host port "shut down")))

          )
    )
  )

(defn kick-off []
  (let [channel-group (channel-group (str "tcp-server localhost:" 12345))
        handler  (channel-initializer)]
    (when-let [srv (TelnetServer.  "127.0.0.1" 12345 handler channel-group (atom {}))]
      (start! srv)
      srv)))
