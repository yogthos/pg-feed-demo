(ns pg-feed-demo.subscriptions
  (:require [re-frame.core :refer [reg-sub]]))

(reg-sub
  :events
  (fn [db _]
    (:events db)))
