(set-env!
 :source-paths    #{"src"}
 :resource-paths  #{"resources"}
 :dependencies '[[adzerk/boot-cljs          "1.7.228-1"  :scope "test"]
                 [adzerk/boot-cljs-repl     "0.3.3"      :scope "test"]
                 [adzerk/boot-reload        "0.4.12"      :scope "test"]
                 [pandeiro/boot-http        "0.7.3"      :scope "test"]
                 [deraen/boot-sass          "0.2.1"      :scope "test"]
                 [com.cemerick/piggieback   "0.2.1"      :scope "test"]
                 [org.clojure/tools.nrepl   "0.2.12"     :scope "test"]
                 [weasel                    "0.7.0"      :scope "test"]
                 [confetti/confetti   "0.1.2-SNAPSHOT"   :scope "test"]
                 [binaryage/devtools        "0.8.0"      :scope "test"]
                 [binaryage/dirac           "0.6.3"      :scope "test"]
                 [powerlaces/boot-cljs-devtools "0.1.1"  :scope "test"]
                 [org.clojure/clojurescript "1.9.229"]
                 [rum "0.10.6"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[adzerk.boot-reload    :refer [reload]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[powerlaces.boot-cljs-devtools :refer [cljs-devtools]]
 '[deraen.boot-sass      :refer [sass]]
 '[pandeiro.boot-http    :refer [serve]]
 '[confetti.boot-confetti :refer [sync-bucket create-site]]
 '[boot.util :as util]
 '[clojure.java.io :as io]
 '[clojure.data.json :as json])

(deftask fetch-rooms
  "Download a page (TODO multipage support) of the Matrix.org publicRooms list and filter it
   so that only rooms whose ID can be found in `rooms.edn` are left. Put a file `rooms.json`
   into the root of the Fileset containing these rooms."
  []
  (with-pre-wrap fs
    (let [our?  (set (map :matrix/internal-id (read-string (slurp (io/resource "rooms.edn")))))
          rooms (-> (slurp "https://matrix.org/_matrix/client/r0/publicRooms")
                    (json/read-str :key-fn keyword)
                    :chunk)
          our-rooms (filter (comp our? :room_id) rooms)
          dir      (tmp-dir!)
          ds-file  (io/file dir "_matrix" "client" "r0" "publicRooms") ; perhaps for api
          tgt-file (io/file dir "rooms.json")
          json-str (json/write-str {:chunk our-rooms})]
      (util/info "Downloaded info for %s rooms\n" (count rooms))
      (util/info "%s rooms found out of %s provided via rooms.edn\n" (count our-rooms) (count our?))
      (io/make-parents tgt-file)
      (spit tgt-file json-str)
      (io/make-parents ds-file)
      (spit ds-file json-str)
      (-> fs (add-resource dir) commit!))))

(deftask build []
  (comp (speak)
        (sass)
        (cljs)))

(deftask run []
  (comp (serve)
        (watch)
        (cljs-repl)
        (reload)
        (cljs-devtools)
        (build)))

(deftask production []
  (task-options! cljs {:optimizations :advanced}
                 sass {:output-style :compressed})
  identity)

(deftask development []
  (task-options! cljs {:optimizations :none :source-map true}
                 reload {:on-jsload 'net.clojurians.matrix.app/init})
  identity)

(deftask dev
  "Simple alias to run application in development mode"
  []
  (comp (development)
        (run)))

(when (.exists (io/file "clojurians-martinklepsch-com.confetti.edn"))
  (def confetti-edn
    (read-string (slurp "clojurians-martinklepsch-com.confetti.edn")))

  (deftask deploy []
    (comp
     (build)
     (sift :include #{#"^app\.js$" #"^app\.css$" #"^index\.html$" #"^rooms\.json$"
                      #"^_matrix\/client\/r0\/publicRooms$"})
     (sync-bucket :bucket (:bucket-name confetti-edn)
                  :prune true
                  :cloudfront-id (:cloudfront-id confetti-edn)
                  :access-key (:access-key confetti-edn)
                  :secret-key (:secret-key confetti-edn)))))
