(ns pg-feed-demo.app
  (:require [pg-feed-demo.core :as core]))

;;ignore println statements in prod
(set! *print-fn* (fn [& _]))

(core/init!)
