(ns pg-feed-demo.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[pg-feed-demo started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[pg-feed-demo has shut down successfully]=-"))
   :middleware identity})
