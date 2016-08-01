(ns att.matcher
  (:require [clojure.string :as str]))

(defn binding? [s]
  (some-> s (str/starts-with? "?")))

(defn binding-kw [s]
  (when (binding? s)
    (keyword (.substring s 1 (count s)))))

(defn- match* [patterns parts bindings]
  (if (empty? patterns)
    bindings
    (let [[pat & pats] patterns
          [part & parts] parts]
      (if (binding? pat)
        (recur pats parts (conj bindings [(binding-kw pat) part]))
        (when (= pat part)
          (recur pats parts bindings))))))

(defn match [patterns parts]
  (when (= (count patterns) (count parts))
    (match* patterns parts [])))

(defn first-not-nil [seq]
  (some #(when (some? %) %) seq))

(defn match-or [patterns s]
  (if-not (empty? patterns)
    (first-not-nil (map match patterns (repeat s)))
    []))

(defn match-and [patterns strings]
  (let [match-single-pattern (fn [p] (first-not-nil (map (partial match p) strings)))
        matches (map match-single-pattern patterns)]
    (when (every? some? matches)
      (apply concat matches))))

(defn split [s re]
  (some-> s (str/split re)))

(defn split-path [s]
  (let [s (if (str/starts-with? s "/") (.substring s 1 (count s)) s)]
    (split s #"/")))

(defn split-query-params [p]
  (map #(split % #"=") (split p #"&")))

(defn match-type? [type match]
  (when match (str/starts-with? (first match) type)))

(defn build-pattern [built pattern]
  (let [regex #"^host\(([^)]+)\)$|^path\(([^)]+)\)$|^queryparam\(([^)]+)=([^)]+)\)$"
        match (re-find regex pattern)]
    (condp match-type? match
      "host" (update built :host conj (second match))
      "path" (update built :path conj (split-path (nth match 2)))
      "queryparam" (update built :query-params conj [(nth match 3) (nth match 4)])
      (throw (ex-info "Can't parse pattern" {:pattern pattern})))))

(defn build-patterns [pattern-string]
  (reduce build-pattern {:host [] :path [] :query-params []}
          (->> (split pattern-string #";")
               (map str/trim)
               (remove str/blank?))))

(defn parse-url [url]
  (let [uri (java.net.URI. url)]
    [(.getHost uri)
     (split-path (.getPath uri))
     (split-query-params (.getQuery uri))]))

(defprotocol IRecognize
  (recognize [pattern url]))

(defrecord Pattern [pattern]
  IRecognize
  (recognize [_ url]
    (let [patterns (build-patterns pattern)
          [host path query-params] (parse-url url)
          matches [(match-or  (:host patterns) host)
                   (match-or  (:path patterns) path)
                   (match-and (:query-params patterns) query-params)]]
      (when (every? some? matches)
        (apply concat matches)))))


(comment

  (def twitter (Pattern. "host(twitter.com); path(?user/status/?id);"))
  (recognize twitter "http://twitter.com/bradfitz/status/562360748727611392")
  ;; => [[:id 562360748727611392] [:user "bradfitz"]]

  (def dribbble (Pattern. "host(dribbble.com); path(shots/?id); queryparam(offset=?offset);"))
  (recognize dribbble "https://dribbble.com/shots/1905065-Travel-Icons-pack?list=users&offset=1")
  ;; => [[:id "1905065-Travel-Icons-pack"] [:offset "1"]]
  (recognize dribbble "https://twitter.com/shots/1905065-Travel-Icons-pack?list=users&offset=1")
  ;; => nil ;; host mismatch
  (recognize dribbble "https://dribbble.com/shots/1905065-Travel-Icons-pack?list=users")
  ;; => nil ;; offset queryparam missing

  (def dribbble2 (Pattern. "host(dribbble.com); host(twitter.com); path(shots/?id); queryparam(offset=?offset); queryparam(list=?type); queryparam(per_page=100);"))
  (recognize dribbble2 "https://twitter.com/shots/1905065-Travel-Icons-pack?list=users&offset=1&per_page=30")
  ;; => nil ;; per_page mismatch
  (recognize dribbble2 "https://twitter.com/shots/1905065-Travel-Icons-pack?list=users&offset=1&per_page=100")

)
