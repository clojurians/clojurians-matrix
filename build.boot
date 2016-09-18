(set-env!
 :source-paths    #{"src"}
 :resource-paths  #{"resources"}
 :dependencies '[[adzerk/boot-cljs          "1.7.228-1"  :scope "test"]
                 [adzerk/boot-cljs-repl     "0.3.0"      :scope "test"]
                 [adzerk/boot-reload        "0.4.8"      :scope "test"]
                 [pandeiro/boot-http        "0.7.2"      :scope "test"]
                 [deraen/boot-sass          "0.2.1"      :scope "test"]
                 [com.cemerick/piggieback   "0.2.1"      :scope "test"]
                 [org.clojure/tools.nrepl   "0.2.12"     :scope "test"]
                 [weasel                    "0.7.0"      :scope "test"]
                 [confetti/confetti   "0.1.2-SNAPSHOT"   :scope "test"]
                 [org.clojure/clojurescript "1.7.228"]
                 [rum "0.10.4"]])

(require
 '[adzerk.boot-cljs      :refer [cljs]]
 '[deraen.boot-sass      :refer [sass]]
 '[adzerk.boot-cljs-repl :refer [cljs-repl start-repl]]
 '[adzerk.boot-reload    :refer [reload]]
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
          ;; tgt-file (io/file dir "_matrix" "client" "r0" "publicRooms") ; perhaps for api
          tgt-file (io/file dir "rooms.json")
          ]
      (util/info "Downloaded info for %s rooms\n" (count rooms))
      (util/info "%s rooms found out of %s provided via rooms.edn\n" (count our-rooms) (count our?))
      (io/make-parents tgt-file)
      (spit tgt-file (json/write-str {:chunk our-rooms}))
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
     (show :fileset true)
     (sift :include #{#"^app\.js$" #"^app\.css$" #"^index\.html$" #"^rooms\.json$"})
     (show :fileset true)
     (sync-bucket :bucket (:bucket-name confetti-edn)
                  :prune true
                  :cloudfront-id (:cloudfront-id confetti-edn)
                  :access-key (:access-key confetti-edn)
                  :secret-key (:secret-key confetti-edn)))))
