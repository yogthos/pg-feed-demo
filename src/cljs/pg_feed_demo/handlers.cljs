(ns pg-feed-demo.handlers
  (:require [pg-feed-demo.db :as db]
            [re-frame.core :refer [dispatch reg-event-db]]))

(reg-event-db
  :initialize-db
  (fn [_ _]
    db/default-db))

(reg-event-db
  :event
  (fn [db [_ event]]
    (update db :events (fnil conj []) event)))


