(ns net.clojurians.matrix.search)

;; Search functionality is taken from metosin/komponentit's autocomplete functionality
;; https://github.com/metosin/komponentit/blob/master/src/cljs/komponentit/autocomplete.cljs

(def +create-item-index+ -1)

(defn query-match? [term-match-fn item query]
  (every? (partial term-match-fn item) query))

(defn sub-query-match? [term-match-fn item query]
  (let [m (group-by #(boolean (term-match-fn item %)) query)]
    [(get m true) (get m false)]))

(defn default->query [search]
  (some-> search
    (.toLowerCase)
    (.split #" ")
    (->> (remove empty?))
    vec))

(defn find-by-selection [results selected-index]
  (some (fn [item]
          ;; FIXME:
          (if (= (::i item) selected-index)
            item))
        results))

(defn create-matcher*
  "Fields can be either collection containing multiple key for map,
   or a single key.
   If collection is given, returned function will go through keys using some."
  [fields]
  (if (sequential? fields)
    (fn [item term]
      (some (fn [field]
              (some-> item (get field) (-> (.toLowerCase) (.indexOf term) (not= -1))))
            fields))
    (fn [item term]
      (some-> item (get fields) (-> (.toLowerCase) (.indexOf term) (not= -1))))))