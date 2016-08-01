(ns jackdaw.transport
  (:import [io.netty.channel.group ChannelGroup
                          DefaultChannelGroup]
            [java.util.concurrent Executors TimeUnit]
            (io.netty.util.concurrent Future
                               EventExecutorGroup
                               DefaultEventExecutorGroup
                               ImmediateEventExecutor))
  )

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
