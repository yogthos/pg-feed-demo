(ns pg-feed-demo.routes.home
  (:require [pg-feed-demo.layout :as layout]
            [compojure.core :refer [defroutes GET]]
            [pg-feed-demo.db.core :as db]
            [mount.core :refer [defstate]]
            [immutant.web.async :as async]
            [clojure.tools.logging :as log]))

(defstate channels
  :start (atom #{}))

(defstate ^{:on-reload :noop} event-listener
  :start (db/add-listener
           db/notifications-connection
           :events
           (fn [_ _ message]
             (doseq [channel @channels]
               (async/send! channel message))))
  :stop (db/remove-listener
          db/notifications-connection
          event-listener))

(defn persist-event! [_ event]
  (db/event! {:event event}))

(defn connect! [channel]
  (log/info "channel open")
  (swap! channels conj channel))

(defn disconnect! [channel {:keys [code reason]}]
  (log/info "close code:" code "reason:" reason)
  (swap! channels #(remove #{channel} %)))

(defn home-page []
  (layout/render "home.html"))

(defroutes home-routes
  (GET "/" []
    (home-page))
  (GET "/events" request
    (async/as-channel
      request
      {:on-open    connect!
       :on-close   disconnect!
       :on-message persist-event!})))
