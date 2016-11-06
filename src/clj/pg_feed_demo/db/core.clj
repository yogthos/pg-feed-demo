(ns pg-feed-demo.db.core
  (:require
    [cheshire.core :refer [generate-string parse-string]]
    [clojure.java.jdbc :as jdbc]
    [conman.core :as conman]
    [pg-feed-demo.config :refer [env]]
    [mount.core :refer [defstate]])
  (:import
    com.impossibl.postgres.api.jdbc.PGNotificationListener))

(defstate ^:dynamic *db*
  :start (conman/connect! {:jdbc-url (env :database-url)})
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(defstate notifications-connection
  :start (jdbc/get-connection {:connection-uri (env :database-url)})
  :stop (.close notifications-connection))

(defn add-listener [conn id listener-fn]
  (let [listener (proxy [PGNotificationListener] []
                   (notification [chan-id channel message]
                     (listener-fn chan-id channel message)))]
    (.addNotificationListener conn listener)
    (jdbc/db-do-commands
      {:connection notifications-connection}
      (str "LISTEN " (name id)))
    listener))

(defn remove-listener [conn listener]
  (.removeNotificationListener conn listener))
