(ns user
  (:require [mount.core :as mount]
            [pg-feed-demo.figwheel :refer [start-fw stop-fw cljs]]
            pg-feed-demo.core))

(defn start []
  (mount/start-without #'pg-feed-demo.core/repl-server))

(defn stop []
  (mount/stop-except #'pg-feed-demo.core/repl-server))

(defn restart []
  (stop)
  (start))


