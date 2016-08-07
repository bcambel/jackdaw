(ns jackdaw.tcp
  "Accepts messages from external sources. Associated with a core. Sends
  incoming events to the core's streams, queries the core's index for states."
  (:import [java.net InetSocketAddress InetAddress UnknownHostException]
           [java.util List]

           [java.util.concurrent Executors TimeUnit]
           [java.nio.channels ClosedChannelException]
           (javax.net.ssl SSLContext)
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.buffer ByteBufUtil]

           [io.netty.channel Channel ChannelOption
                             ChannelInitializer ChannelHandler
                             ChannelHandlerContext ChannelFutureListener
                             ChannelInboundHandlerAdapter ChannelPipeline]
           [io.netty.channel.group ChannelGroup
                                   DefaultChannelGroup]
           (io.netty.channel.socket DatagramPacket)
           (io.netty.buffer ByteBufInputStream)
           (io.netty.handler.codec MessageToMessageDecoder
                                  MessageToMessageEncoder)
           (io.netty.handler.codec.protobuf ProtobufDecoder
                                           ProtobufEncoder)
           [io.netty.handler.codec LengthFieldBasedFrameDecoder
                                   LengthFieldPrepender]
           [io.netty.handler.ssl SslHandler]
           [io.netty.channel.epoll EpollEventLoopGroup EpollServerSocketChannel]
           [io.netty.channel.nio NioEventLoopGroup]
           [io.netty.channel.socket.nio NioServerSocketChannel]
           (io.netty.util ReferenceCounted)
           (io.netty.util.concurrent Future
                              EventExecutorGroup
                              DefaultEventExecutorGroup
                              ImmediateEventExecutor)
           )
  (:require [less.awful.ssl :as ssl]
            [taoensso.timbre :as log]
            [interval-metrics.core :as metrics]
            [slingshot.slingshot :refer [try+]])
  (:use [interval-metrics.measure :only [measure-latency]]))


(defprotocol Service
  "Services are components of a core with a managed lifecycle. They're used for
  stateful things like connection pools, network servers, and background
  threads."
  (reload! [service core]
          "Informs the service of a change in core.")
  (start! [service]
          "Starts a service. Must be idempotent.")
  (stop!  [service]
         "Stops a service. Must be idempotent.")
  (conflict? [service1 service2]
             "Do these two services conflict with one another? Adding
             a service to a core *replaces* any conflicting services."))

(defn event-executor
 "Creates a new netty execution handler for processing events. Defaults to 1
 thread per core."
 []
 (DefaultEventExecutorGroup. (.. Runtime getRuntime availableProcessors)))

(defonce ^DefaultEventExecutorGroup shared-event-executor
 (event-executor))

(defn ^DefaultChannelGroup channel-group
  "Make a channel group with a given name."
  [name]
  (DefaultChannelGroup. name (ImmediateEventExecutor/INSTANCE)))

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

(defn retain
  "Retain a ReferenceCounted object, if x is such an object. Otherwise, noop.
  Returns x."
  [x]
  (when (instance? ReferenceCounted x)
    (.retain ^ReferenceCounted x))
  x)


(defmacro channel-initializer
  "Constructs an instance of a Netty ChannelInitializer from a list of
  names and expressions which return handlers. Handlers with :shared metadata
  on their names are bound once and re-used in every invocation of
  getPipeline(), other handlers will be evaluated each time.
  (channel-pipeline-factory
             frame-decoder    (make-an-int32-frame-decoder)
    ^:shared protobuf-decoder (ProtobufDecoder. (Proto$Msg/getDefaultInstance))
    ^:shared msg-decoder      msg-decoder)"
  [& names-and-exprs]
  (assert (even? (count names-and-exprs)))
  (let [handlers (partition 2 names-and-exprs)
        shared (filter (comp :shared meta first) handlers)
        pipeline-name (vary-meta (gensym "pipeline")
                                 assoc :tag `ChannelPipeline)
        forms (map (fn [[h-name h-expr]]
                     `(.addLast ~pipeline-name
                                ~(when-let [e (:executor (meta h-name))]
                                   e)
                                ~(str h-name)
                                ~(if (:shared (meta h-name))
                                   h-name
                                   h-expr)))
                   handlers)]
;    (prn forms)
    `(let [~@(apply concat shared)]
       (proxy [ChannelInitializer] []
         (initChannel [~'ch]
           (let [~pipeline-name (.pipeline ^Channel ~'ch)]
             ~@forms
             ~pipeline-name))))))

; (defn protobuf-decoder
;   "Decodes protobufs to Msg objects"
;   []
;   ; (ProtobufDecoder. nil)
;   )
;
; (defn protobuf-encoder
;   "Encodes protobufs to Msg objects"
;   []
;   (ProtobufEncoder.))


(defn int32-frame-decoder
  []
  ; Offset 0, 4 byte header, skip those 4 bytes.
  (LengthFieldBasedFrameDecoder. Integer/MAX_VALUE, 0, 4, 0, 4))

(defn int32-frame-encoder
  []
  (LengthFieldPrepender. 4))


(defn gen-tcp-handler
  "Wraps Netty boilerplate for common TCP server handlers. Given a reference to
  a core, a stats package, a channel group, and a handler fn, returns a
  ChannelInboundHandlerAdapter which calls (handler core stats
  channel-handler-context message) for each received message.
  To prevent Netty outbound buffer from filling up in the case of clients not
  reading ack messages, we close the channel when it becomes unwritable. Clients
  should then be ready to reconnect if need be as they will receive some form
  of exception in this case.
  Automatically handles channel closure, and handles exceptions thrown by the
  handler by logging an error and closing the channel."
  [core stats ^ChannelGroup channel-group handler]
  (proxy [ChannelInboundHandlerAdapter] []
    (channelActive [ctx]
      (.add channel-group (.channel ctx)))

    (channelWritabilityChanged [^ChannelHandlerContext ctx]
      (let [channel (.channel ctx)]
        (when (not (.isWritable channel))
          (log/warn "forcefully closing connection from " (.remoteAddress channel)
                ". Client might be not reading acks fast enough or network is broken")
          (.close channel))))

    (channelRead [^ChannelHandlerContext ctx ^Object message]
      (try
        (log/info "Channel reading...")
        (handler @core stats ctx message)
        (catch java.nio.channels.ClosedChannelException e
          (log/warn "channel closed"))))

    (exceptionCaught [^ChannelHandlerContext ctx ^Throwable cause]
      (log/warn cause "TCP handler caught")
      (.close (.channel ctx)))

    (isSharable [] true)))


(def netty-implementation
  "Provide native implementation of Netty for improved performance on
  Linux only. Provide pure-Java implementation of Netty on all other
  platforms. See http://netty.io/wiki/native-transports.html"
  (if (and (.contains (. System getProperty "os.name") "Linux")
           (.contains (. System getProperty "os.arch") "amd64")
           (.equals (System/getProperty "netty.epoll.enabled" "true") "true"))
    {:event-loop-group-fn #(EpollEventLoopGroup.)
     :channel EpollServerSocketChannel}
    {:event-loop-group-fn #(NioEventLoopGroup.)
     :channel NioServerSocketChannel}))

(defn stream! [core event]
  (log/info "Arrived" event)
  nil
  )

(defn query-ast [q]
  nil
  )

(defn search [& args] nil)

(defn handle
 "Handles a msg with the given core."
 [core msg]
 (try+
   (log/info "Arrived" core msg)
   ;; Send each event/state to each stream
   (doseq [event (:states msg)] (stream! core event))
   (doseq [event (:events msg)] (stream! core event))

   (if (:query msg)
     ;; Handle query
     (let [ast (query-ast (:string (:query msg)))]
       (if-let [i (:index core)]
         {:ok true :events (search i ast)}
         {:ok false :error "no index"}))

     ; Otherwise just return an ack
     {:ok true})

   ;; Some kind of error happened
   (catch [:type :riemann.query/parse-error] {:keys [message]}
     {:ok false :error (str "parse error: " message)})
   (catch Exception ^Exception e
     {:ok false :error (.getMessage e)})))

(defn tcp-handler
  "Given a core, a channel, and a message, applies the message to core and
  writes a response back on this channel."
  [core stats ^ChannelHandlerContext ctx ^Object message]
  (let [t1 (:decode-time message)]
    (.. ctx
      ; Actually handle request
      (writeAndFlush "thanks!\n")
      (writeAndFlush (handle core message))

      ; Record time from parse to write completion
      (addListener
        (reify ChannelFutureListener
          (operationComplete [this fut]
            (metrics/update! stats
                             (- (System/nanoTime) t1))

                             ))))))


(defrecord TCPServer [^String host
                     ^int port
                     ^int so-backlog
                     equiv
                     ^ChannelGroup channel-group
                     ^ChannelInitializer initializer
                     core
                     stats
                     killer]
 ; core is a reference to a core
 ; killer is a reference to a function which shuts down the server.
 Service
 (reload! [this new-core]
          (reset! core new-core))

 (start! [this]

           (locking this
             (when-not @killer
               (let [event-loop-group-fn (:event-loop-group-fn
                                           netty-implementation)
                     boss-group (event-loop-group-fn)
                     worker-group (event-loop-group-fn)
                     bootstrap (ServerBootstrap.)]

                 ; Configure bootstrap
                 (doto bootstrap
                   (.group boss-group worker-group)
                   (.channel (:channel netty-implementation))
                   (.option ChannelOption/SO_REUSEADDR true)
                   (.option ChannelOption/TCP_NODELAY true)
                   (.option ChannelOption/SO_BACKLOG so-backlog)
                   (.childOption ChannelOption/SO_REUSEADDR true)
                   (.childOption ChannelOption/TCP_NODELAY true)
                   (.childOption ChannelOption/SO_KEEPALIVE true)
                   (.childHandler initializer))

                 ; Start bootstrap
                 (->> (InetSocketAddress. host port)
                      (.bind bootstrap)
                      (.sync)
                      (.channel)
                      (.add channel-group))
                 (log/info "TCP server" host port "online")

                 ; fn to close server
                 (reset! killer
                         (fn killer []
                           (.. channel-group close awaitUninterruptibly)
                           ; Shut down workers and boss concurrently.
                           (let [w (shutdown-event-executor-group worker-group)
                                 b (shutdown-event-executor-group boss-group)]
                             @w
                             @b)
                           (log/info "TCP server" host port "shut down")))))))

 (stop! [this]
        (locking this
          (when @killer
            (@killer)
            (reset! killer nil))))
                             )


(defn ssl-handler
 "Given an SSLContext, creates a new SSLEngine and a corresponding Netty
 SslHandler wrapping it."
 [^SSLContext context]
 (-> context
   .createSSLEngine
   (doto (.setUseClientMode false)
         (.setNeedClientAuth true))
   SslHandler.
   ; TODO: Where did this go in 4.0.21?
   ; (doto (.setEnableRenegotiation false))
   ))

(defn initializer
 "A channel pipeline initializer for a TCP server."
 [core stats channel-group ssl-context]
 ; Gross hack; should re-work the pipeline macro
 (if ssl-context
   (channel-initializer
              ssl                 (ssl-handler ssl-context)
              int32-frame-decoder (int32-frame-decoder)
     ^:shared int32-frame-encoder (int32-frame-encoder)
     ^{:shared true :executor shared-event-executor} handler
     (gen-tcp-handler core stats channel-group tcp-handler))

   (channel-initializer
              int32-frame-decoder  (int32-frame-decoder)
     ^:shared int32-frame-encoder  (int32-frame-encoder)
     ^{:shared true :executor shared-event-executor} handler
     (gen-tcp-handler core stats channel-group tcp-handler))))

(defn tcp-server
 "Create a new TCP server. Doesn't start until (service/start!).
 Options:
 :host             The host to listen on (default 127.0.0.1).
 :port             The port to listen on. (default 5554 with TLS, or 5555 std)
 :core             An atom used to track the active core for this server.
 :so-backlog       The maximum queue length for incoming tcp connections (default 50).
 :channel-group    A global channel group used to track all connections.
 :initializer      A ChannelInitializer for creating new pipelines.
 TLS options:
 :tls?             Whether to enable TLS
 :key              A PKCS8-encoded private key file
 :cert             The corresponding public certificate
 :ca-cert          The certificate of the CA which signed this key"
 ([]
  (tcp-server {}))
 ([opts]
  (let [core          (get opts :core (atom nil))
        stats         (metrics/rate+latency)
        host          (get opts :host "127.0.0.1")
        port          (get opts :port (if (:tls? opts) 5554 5555))
        so-backlog    (get opts :so-backlog 50)
        channel-group (get opts :channel-group
                           (channel-group
                             (str "tcp-server " host ":" port)))
        equiv         (select-keys opts [:tls? :key :cert :ca-cert])
        ; Use the supplied pipeline factory...
        initializer (get opts :initializer
                         ; or construct one for ourselves!
                         (if (:tls? opts)
                           ; A TLS-enabled handler
                           (do
                             (assert (:key opts))
                             (assert (:cert opts))
                             (assert (:ca-cert opts))
                             (let [ssl-context (ssl/ssl-context
                                                 (:key opts)
                                                 (:cert opts)
                                                 (:ca-cert opts))]
                               (initializer core stats channel-group
                                            ssl-context)))

                           ; A standard handler
                           (initializer core stats channel-group nil)))]

      (TCPServer. host port so-backlog equiv channel-group initializer core stats
                  (atom nil)))))
