(ns jackdaw.github
  (:require [clojure.string                 :as s]
            [clj-http.client                :as client]
            [cheshire.core                  :refer :all]
            [taoensso.timbre :as log]
            [environ.core :refer [env]]
            )
  (:use [slingshot.slingshot :only [throw+ try+]]
      [clojure.data :only [diff]])
  (:gen-class))

(def ghub-root "https://api.github.com")

(defn get-config []
  {:github-client (System/getenv "GITHUB_CLIENT")
    :github-secret (System/getenv "GITHUB_SECRET")})

(def ghub-url*
  (str ghub-root "/search/repositories"
    "?q=+language:%s&sort=stars&order=desc&per_page=100&"))

(def ghub-url
  (str ghub-root "/repositories?per_page=100&"))

(def header-settings
  {:socket-timeout 10000 :conn-timeout 10000})

(def base-user-fields [:id :login :type])

(def ghub-proj-fields [:id :name :fork :watchers :open_issues
                       :language :description :full_name :homepage])

(def user-fields [:id :login :type :name :company :blog
                  :location :email :public_repos :public_gists
                  :followers :following :avatar_url] )

(defn user-extras [& args])
(defn update-table [table & args])
(defn insert-records [data])
(defn insert-users [data])
(defn load-project-extras* [& args])
(defn !nil? [x] (not (nil? x)))

(defn get-url
  [url & options]
  (let [{:keys [header safe care conf]
         :or {header header-settings safe false
              care true conf (get-config)}} options]
    (try+
      (client/get (format "%s&client_id=%s&client_secret=%s" url
                    (:github-client conf) (:github-secret conf))
        header)
      (catch [:status 403] {:keys [request-time headers body]}
        (log/warn "403" request-time headers))
      (catch [:status 404] {:keys [request-time headers body]}
        (log/warn "NOT FOUND" url request-time headers body))
      (catch Object _
        (when care
          (log/error (:throwable &throw-context) "Unexpected Error"))
        (when-not safe
          (throw+))))))

(defn find-next-url
  "Figure out the next url to call
  <https://api.github.com/search/repositories?q=...&page=2>;
  rel=\"next\", <https://api.github.com/search/repositories?q=+...&page=34>; rel=\"last\"
  "
  [stupid-header]
  (when (!nil? stupid-header)
    (try
      (let [[next-s last-s] (.split stupid-header ",")
            next-page (vec (.split next-s ";"))
            is-next (.contains (last next-page) "next")]
        (when is-next
          (s/replace (subs (first next-page) 1) ">" "")))
      (catch Throwable t
        (log/warn t stupid-header)))))


(defn fetch-url
  [url]
  (try
    (let [response (get-url url :header header-settings)]
        (if (nil? response)
          {:success false :next-url nil :data nil :reason "Empty Response"}
          (do
            (let [repos (parse-string (:body response) true)
                  next-url (find-next-url
                      (-> response :headers :link))]
              (log/debug (:headers response))
              {:success true :next-url next-url :data repos}))))
    (catch Throwable t
      (do
        (throw+ t)
        {:success false :reason (.getMessage t) :repos [] :next-url nil }))))

(defn import-repos
  [language max-iter]
  (let [max-iter (or max-iter 10000)]
    (loop [url (format ghub-url* language)
           looped 1]
      (log/warn (format "Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            repos (get data :items)]
        (insert-records repos)
        (when (and next-url (< looped max-iter))
          (recur next-url (inc looped)))))
    1))

(defn expand-user
  "Fetch latest user information from github"
  [user-login]
  (let [url (format "%s/users/%s?"
                     ghub-root user-login (env :client-id) (env :client-secret))
        response (get-url url :header header-settings)]
      (when (!nil? response)
        (let [user-data (parse-string (:body response) true)]
          (when-let [user-info (select-keys user-data user-fields)]
            (log/warn (format "%s -> %s" user-login user-info))
            user-info)))))

(defn expand-project
  [proj]
  (let [url (format "%s/repos/%s?"
                     ghub-root proj (env :client-id) (env :client-secret))
        response (get-url url :header header-settings)]
       (log/info (:headers response))
      (when (!nil? response)
        (let [proj-data (parse-string (:body response) true)]
          (when-let [proj-info (select-keys proj-data ghub-proj-fields)]
            (log/warn (format "%s -> %s" proj proj-info))
            proj-info)))))

(defn user-starred
  "Fetches recursively user repositories"
  [user-login max-iter action-fn]
  (let [max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/starred?per_page=100&"
                      ghub-root user-login)]
    (loop [url start-url
           looped 1]
      (log/warn (format "[STARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            repos data]
        (when-not (empty? repos)
          (let [user-extra {}
              _ (log/infof "Found %d repos" (count repos))
              new-repos (set (map :full_name repos))
              all-repos (set (concat (or (:starred user-extra) #{}) new-repos))]
            (update-table :github_user_list :login user-login
                :starred all-repos))

          (action-fn user-login repos)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn user-repos
  [user-login max-iter]
  (let [max-iter (or max-iter 10000)
        start-url (format "%s/users/%s/repos?per_page=100&"
                      ghub-root user-login)]
    (loop [url start-url
           looped 1]
      (log/warn (format "[USER-REPOS]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            repos data]
        (when-not (empty? repos)
          (let [user-extra (user-extras nil user-login :repos)
              new-repos (set (map :full_name repos))
              all-repos (set (concat (or (:repos user-extra) #{}) new-repos))]
            (update-table :github_user_list :login user-login
                :repos all-repos))


          (insert-records repos)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-stargazers
  [project-name max-iter]
  (let [max-iter (or max-iter 1e3)
        start-url (format "%s/repos/%s/stargazers?per_page=100"
                      ghub-root project-name)]
    (loop [url start-url
           looped 1]
      (log/warn (format "[PROJSTARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
         (let [proj-extra (load-project-extras* nil project-name :stargazers)
              new-users (set (map :login users))
              all-stargazers (set (concat (or (:stargazers proj-extra) #{}) new-users))]
          (update-table :github_project_list :proj project-name
                :stargazers all-stargazers)
                )

          (insert-users users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-watchers
  [project-name max-iter]
  (let [max-iter (or max-iter 10000)
        start-url (format "%s/repos/%s/subscribers?per_page=100"
                      ghub-root project-name)]

    (loop [url start-url
           looped 1]
      (log/warn (format "[PROJSTARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
          ;; INSERT
          (let [proj-extra (load-project-extras* nil project-name :watchers)
                new-users (set (map :login users))
                all-watchers (set (concat (or (:watchers proj-extra) #{}) new-users))]
          (update-table :github_project_list :proj project-name
                :watchers all-watchers))

          (insert-users users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-contrib
  [project-name max-iter]
  (let [max-iter (or max-iter 10000)
        start-url (format "%s/repos/%s/contributors?per_page=100"
                      ghub-root project-name )]
    (loop [url start-url
           looped 1]
      (log/warn (format "[PROJSTARRED]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)

          (let [proj-extra (load-project-extras* nil project-name :contributors)
              new-users (set (map :login users))
              all-contributors (set (concat (or (:contributors proj-extra) #{}) new-users))]
            (update-table :github_project_list :proj project-name
                  :contributors all-contributors)
                  )

          (insert-users users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))


(defn user-followers
  [user-login max-iter]
  (let [max-iter (or max-iter 1e5)
        start-url (format "%s/users/%s/followers?per_page=100"
                          ghub-root user-login)]
    (loop [url start-url
           looped 1]
      (log/warn (format "[FOLLOWERS]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
          (let [user-extra (user-extras nil user-login :followers)
              new-users (set (map :login users))
              all-users (set (concat (or (:followers user-extra) #{}) new-users))]
            (update-table :github_user_list :login user-login
                :followers all-users))

          (insert-users users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn project-readme
  [proj]
  (let [url (format "%s/repos/%s/readme?" ghub-root proj)
        req-header (merge {:accept "application/vnd.github.VERSION.html"}
                          header-settings)]
    (when-let [resp (get-url url :header req-header)]
      ; (log/warn resp)
      (:body resp))))

(defn user-following
  [user-login max-iter]
  (let [max-iter (or max-iter 1e5)
        start-url (format "%s/users/%s/following?per_page=100"
                          ghub-root user-login)]
    (loop [url start-url
           looped 1]
      (log/warn (format "[FOLLOWING]Loop %d. %s" looped url))
      (let [{:keys [success next-url data]} (fetch-url url)
            users (map #(select-keys % base-user-fields) data)]
        (when-not (empty? users)
          (let [user-extra (user-extras nil user-login :following)
              new-users (set (map :login users))
              all-users (set (concat (or (:following user-extra) #{}) new-users))]
            (update-table :github_user_list :login user-login
                :following all-users))

          (insert-users users)
          (when (and next-url (< looped max-iter))
            (recur next-url (inc looped))))))))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))
