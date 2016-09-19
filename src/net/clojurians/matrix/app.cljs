(ns net.clojurians.matrix.app
  (:require [net.clojurians.matrix.search :as search]
            [goog.net.XhrIo :as xhr]
            [goog.string :as gstring]
            [goog.object :as gobj]
            [goog.functions :as gfn]
            [clojure.string :as string]
            [rum.core :as rum]))

(defonce *app-state (atom {}))

(defonce fetch-rooms
  (xhr/send "rooms.json"
            (fn [r]
              (let [json (-> r .-target .getResponseJson (gobj/get "chunk"))]
                (swap! *app-state assoc :rooms (js->clj json :keywordize-keys true))))))

(def term-match-fn (search/create-matcher* [:name :topic]))

(defonce *rooms
  (rum/derived-atom
   [*app-state]
   ::rooms
   (fn [{:keys [query rooms]}]
     (cond->> rooms
       query (filter #(search/query-match? term-match-fn % (search/default->query query)))))))

(defn room-name [room-data]
  (or (:name room-data)
      (when-let [alias (or (:canonical_alias room-data) (first (:aliases room-data)))]
        (-> alias (gstring/splitLimit ":" 1) first))
      (do #_(js/console.warn "No room name data found" room-data) "Unknown")))

(defn join-uri [room-id]
  (str "https://vector.im/develop/#/room/" room-id))

(defn avatar [mxc-uri]
  (let [size 56]
    [:img.br-100.pa1.ba.b--black-20.h3.w3
     {:src (if mxc-uri
             (str "https://matrix.org/_matrix/media/v1/thumbnail/"
                  (string/replace mxc-uri #"^mxc://" "")
                  "?width=" size "&height=" size "")
             (str "http://placehold.it/" size "x" size))}]))

(rum/defc room < rum/static [room-data]
  [:div.pa3.dt
   [:div.dtc.v-mid.tc.w3.border-box
    (avatar (:avatar_url room-data))
    [:a.db.link.blue.ttu.dim.f6.b.mt2 {:href (join-uri (:room_id room-data))} "Join"]]
   [:div.dtc.v-mid.pl4
    [:h4.ma0.f5.f4-ns.fw6.mid-gray
     (room-name room-data)
     #_(case (:matrix/room-type room-data)
         :matrix/native [:span.ml2.gray.f6.normal. "Matrix"]
         :matrix.bridge/irc [:span.ml2.gray.f6.normal "IRC Bridge"])
     [:span.ml2.dib.light-silver.normal.f6 (or (:canonical_alias room-data) (first (:aliases room-data)))]]
    (when (:topic room-data)
      [:p.break.mb0.mt3 (gstring/truncate (:topic room-data) 140)])
    [:p.f6.mb0.mt3 [:span.ttu.mid-gray "Members: "] [:span.b (:num_joined_members room-data)]]]])

(rum/defc room-list < rum/reactive []
  [:div.ph4.cf.mw8.center
   (for [r (rum/react *rooms)]
     [:div.mb4.br2.ba.b--black-20 {:key (:room_id r)} (room r)])])

(defn update-query! [v]
  (js/console.info "Updating Query" v)
  (swap! *app-state assoc :query v))

(defn debounce-mx [k]
  {:will-mount (fn [state]
                (let [*debounced-fns (atom {})]
                  (assoc state k (fn [k interval f]
                                   (if-let [already-debounced (get @*debounced-fns k)]
                                     already-debounced
                                     (let [new-debounced (gfn/debounce f interval)]
                                       (js/console.info "Creating new debounced fn" k)
                                       (swap! *debounced-fns assoc k new-debounced)
                                       new-debounced))))))})

(rum/defcs search < (debounce-mx ::debounce) [state]
  [:div.pa3.mw8.center
   [:input.f3.bn.db.pa3.w-100.border-box
    {:on-change  (fn [e]
                   (let [v (.. e -target -value)]
                     (((::debounce state) :search-input 300 update-query!) v)))
     :placeholder "Search existing rooms..."}]])

(rum/defc footer []
  [:div.pa4.pv5.mid-gray.bt.b--black-20
   [:a.link.b.f3.f2-ns.dim.black-70.lh-solid {:href "http://clojurians.net"} "clojurians.net"]
   [:p.f6.db.lh-solid "An inclusive community for people interested in functional programming and Clojure/Script."]

   [:div.mt5
    [:a.f6.dib.pr2.mid-gray.dim {:href "/code-of-conduct/" :title "Code of Conduct"} "Code of Conduct"]
    [:a.f6.dib.pr2.mid-gray.dim {:href "/about/" :title "About"} "About"]
    [:a.f6.dib.pr2.mid-gray.dim {:href "/contact/" :title "Contact"} "Contact"]]])

(rum/defc app []
  [:div.cf
   [:div.w-40-ns.fl
    (footer)]
   [:div.w-60-ns.fl
    (search)
    (room-list)]])

(defn init []
  (rum/mount (app) (. js/document (getElementById "container"))))
