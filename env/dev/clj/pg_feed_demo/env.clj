(ns pg-feed-demo.env
  (:require [selmer.parser :as parser]
            [clojure.tools.logging :as log]
            [pg-feed-demo.dev-middleware :refer [wrap-dev]]))

(def defaults
  {:init
   (fn []
     (parser/cache-off!)
     (log/info "\n-=[pg-feed-demo started successfully using the development profile]=-"))
   :stop
   (fn []
     (log/info "\n-=[pg-feed-demo has shut down successfully]=-"))
   :middleware wrap-dev})
