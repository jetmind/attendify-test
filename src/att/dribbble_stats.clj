(ns att.dribbble-stats
  (:require [clojure.data.json :as json]
            [clojure.core.async :as async :refer [go-loop go <! >! timeout]]
            [clojure.pprint :refer [pprint]]
            [org.httpkit.client :as http]
            [muse.core :refer [fmap traverse DataSource LabeledSource run!! flat-map]]))

(def token "4c631d538f25e2faf58a8b6b2cac53b0fd4e99f7d25c480a39ea695b7c6bb987")
(def api-url "https://api.dribbble.com/v1/")

(def throttler (async/chan (async/dropping-buffer 1)))

(defonce start-throttler
  (go
    (while (>! throttler :tick)
      ;; Dribbble allows up to 60 req/sec => make a tick every 1 second
      (<! (timeout 1000)))))

(defn next-page [headers]
  (some->> (:link headers)
           (re-find #"<(.*)>; rel=\"next\"")
           second))

(defn http-get [url out-chan]
  (println "--> " url)
  (http/get url {:oauth-token token}
            (fn [result]
              (println "<-- " url)
              (async/put! out-chan result))))

(defn req [url]
  (let [out-chan (async/chan 1)]
    (http-get url out-chan)
    out-chan))

(defn get-all-pages [url]
  (go-loop [next-page-url (str url "?per_page=100")
            result []]
    (<! throttler)
    (let [resp (<! (req next-page-url))
          next (next-page (:headers resp))
          next-res (into result (json/read-str (:body resp) :key-fn keyword))]
      (if (some? next)
        (recur next next-res)
        next-res))))

(defrecord Followers [user-id-or-name]
  DataSource
  (fetch [_]
    (get-all-pages (str api-url "users/" user-id-or-name "/followers")))

  LabeledSource
  (resource-id [_] user-id-or-name))

(defrecord Shots [follower]
  DataSource
  (fetch [_]
    (let [shots-url (get-in follower [:follower :shots_url])]
      (when shots-url (get-all-pages shots-url))))

  LabeledSource
  (resource-id [_]
    (get-in follower [:follower :id])))

(defrecord Likers [shot]
  DataSource
  (fetch [_]
    (let [likes-url (:likes_url shot)]
      (when likes-url (get-all-pages likes-url))))

  LabeledSource
  (resource-id [_] (:id shot)))

(defn likers-rating [likers]
  (->> (group-by :id likers)
       (into [])
       (map (fn [[id users]] (assoc (first users) :likes (count users))))
       (sort-by :likes >)
       (take 10)))

(defn transform-likers [likers]
  (map #(select-keys (:user %) [:id :username]) likers))

(defn run [user-id-or-name]
  (run!!
   (->> (Followers. user-id-or-name)
        (traverse #(Shots. %))
        (fmap (partial apply concat))
        (traverse #(Likers. %))
        (fmap (partial apply concat))
        (fmap transform-likers)
        (fmap likers-rating))))

(comment

  (let [result (run "EgorKovalchuk")]
    (pprint result)
    result)

  )

