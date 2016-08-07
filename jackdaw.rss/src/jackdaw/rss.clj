(ns jackdaw.rss
  (:import [com.rometools.rome.io SyndFeedInput XmlReader]
            [java.net URL]
            [com.rometools.rome.feed.synd SyndFeed])
  (:require [taoensso.timbre :as log]
            [com.climate.claypoole :as cp]
            [blocks.core :as block])
  (:use blocks.store.file)
  (:gen-class))


(def fs (file-store ".rss"))


(defn make-enclosure [e]
  {:length (.getLength e) :type (.getType e)
   :url (.getUrl e)})

(defn make-content [c]
  {:type (.getType c) :value (.getValue c)})

(defn make-link [l]
  {:href (.getHref l) :hreflang (.getHreflang l)
              :length (.getLength l) :rel (.getRel l) :title (.getTitle l)
              :type (.getType l)})

(defn make-category [c]
  {:name (.getName c)
   :taxonomyURI (.getTaxonomyUri c)})

(defn make-person [sp]
  {:email (.getEmail sp)
   :name (.getName sp)
   :uri (.getUri sp)})

(defn make-image [i]
  {:description (.getDescription i)
   :link (.getLink i)
   :title (.getTitle i)
   :url (.getUrl i)})

(defn get-rss* [u]
  (let [url (URL. u)
        input (SyndFeedInput. )]
    (log/info "Fetching " url)
    (when-let [feeder (.build input (XmlReader. url))]
      (let [entries (.getEntries feeder)
            f {:_inst (str (type feeder))
              :type (.getFeedType feeder)
              :authors (map make-person (seq (.getAuthors feeder)))
               :categories (map make-category (seq (.getCategories feeder)))
               :contributors (map make-person (seq (.getContributors feeder)))
              :title (.getTitle feeder)
              :language (.getLanguage feeder)
              :link (.getLink feeder)
              :uri (.getUri feeder)
              :encoding (.getEncoding feeder)
              :publish-date (.getPublishedDate feeder)
              :entries (mapv (fn[e] {
                                :uri (.getUri e)
                                :link (.getLink e)
                                :authors (map make-person (seq (.getAuthors e)))
                                :categories (map make-category (seq (.getCategories e)))
                                 :contents (map make-content (seq (.getContents e)))
                                 :contributors (map make-person (seq (.getContributors e)))
                                 :enclosures (map make-enclosure (seq (.getEnclosures e)))
                                :title (.getTitle e )
                                :description (-> e .getDescription .getValue)
                                })  entries)
              }]
      (log/infof  "completed [%s]. Writing to store..." u)
      (block/store! fs (str f))
      :done
  ))))


(defn get-rss [u]
  (try
    (get-rss* u)
    (catch Throwable t
      (do
        ; (log/error t)
        (log/warn "Failed to fetch " u)))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
