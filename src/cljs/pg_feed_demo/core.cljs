(ns pg-feed-demo.core
  (:require [reagent.core :as r]
            [re-frame.core :as rf]
            [pg-feed-demo.handlers]
            [pg-feed-demo.subscriptions]
            [pg-feed-demo.ws :as ws]))

(defn home-page []
  [:div.container
   [:div.navbar]
   [:div.row>div.col-sm-12>div.card
    [:div.card-header>h4 "Events"]
    [:div.card-block>ul
     (for [event @(rf/subscribe [:events])]
       ^{:key event}
       [:li event])]]
   [:hr]
   [:div.row>div.col-sm-12>span.btn-primary.input-group-addon
    {:on-click #(ws/send (str "user event " (js/Date.)))}
    "generate event"]])

(defn mount-components []
  (r/render [#'home-page] (.getElementById js/document "app")))

(defn init! []
  (rf/dispatch-sync [:initialize-db])
  (ws/connect-ws
    (str "ws://" (.-host js/location) "/events")
    #(rf/dispatch [:event %]))
  (mount-components))
