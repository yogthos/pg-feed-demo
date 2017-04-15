# pg-feed-demo

generated using Luminus version "2.9.11.11"

*prerequisites:*

* [JDK](http://www.azul.com/downloads/zulu/)
* [Leiningen](http://leiningen.org/)
* [PostgreSQL](https://www.postgresql.org/)



Let's start by creating a new project for our app:

```
lein new luminus pg-feed-demo +postgres +re-frame
```

### The database

The first step is to create a schema for the app, and set the connection URL in the `profiles.clj`, e.g:

```clojure
{:profiles/dev
 {:env
  {:database-url
   "jdbc:pgsql://localhost:5432/feeds_dev?user=feeds&password=feeds"}}
```

#### Migrations

Once the schema is ready, we can write a migrations script that creates a table called `events`, and sets up a notification trigger on it. Let's run the following command in the project root folder to create the migration files:

```
lein migratus create events-table
```

Next, add the following script as the `up` migration:

```sql
CREATE TABLE events
(id SERIAL PRIMARY KEY,
 event TEXT);
--;;
CREATE FUNCTION notify_trigger() RETURNS trigger AS $$
DECLARE
BEGIN
 -- TG_TABLE_NAME - name of the table that was triggered
 -- TG_OP - name of the trigger operation
 -- NEW - the new value in the row
 IF TG_OP = 'INSERT' or TG_OP = 'UPDATE' THEN
   execute 'NOTIFY '
   || TG_TABLE_NAME
   || ', '''
   || TG_OP
   || ' '
   || NEW
   || '''';
 ELSE
   execute 'NOTIFY '
   || TG_TABLE_NAME
   || ', '''
   || TG_OP
   || '''';
 END IF;
 return new;
END;
$$ LANGUAGE plpgsql;
--;;
CREATE TRIGGER event_trigger
AFTER INSERT or UPDATE or DELETE ON events
FOR EACH ROW EXECUTE PROCEDURE notify_trigger();
``` 

The`notify_trigger` function will broadcast a notification with the table name, the operation, and the parameters when available. The `event_trigger` will run it whenever `insert`, `update`, or `delete` operations are performed on the `messages` table.

We'll also add the `down` migration for posterity:

```
DROP FUNCTION notify_trigger() CASCADE;
DROP TABLE events;
```

We can now run migrations as follows:

```
lein run migrate
```

#### Queries

Let's open the `resources/sql/queries.sql` file and replace the default queries with the following:

```sql
-- :name event! :! :n
-- :doc insert a new event
INSERT INTO events (event) VALUES (:event)
```

### The server

Unfortunately, the official Postgres JDBC driver cannot receive asynchronous notifications, and uses polling to check if any notifications were issued. Instead, we'll use the [pgjdbc-ng](http://impossibl.github.io/pgjdbc-ng/) driver that provides support for many Postgres specific features, including async notifications. Let's update our app to use this driver instead by swapping the dependency in `project.clj`:

```clojure
;[org.postgresql/postgresql "9.4.1211"]
[com.impossibl.pgjdbc-ng/pgjdbc-ng "0.6"]
```                 
                          
#### Notification listener

Let's open up the `pg-feed-demo.db.core` namespace and update it to fit our purposes. Since we're no longer using the official Postgres driver, we'll need to update the namespace declaration to remove any references to it. We'll also add the import for the `PGNotificationListener` class that will be used to add listeners to the connection. To keep things simple, we'll also remove any protocol extensions declared there. The resulting namespace should look as follows:

```clojure
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
```

In order to add a notification listener, we first have to create a connection. Let's create a [Mount](https://github.com/tolitius/mount) `defstate` called `notifications-connection` to hold it:

```clojure
(defstate notifications-connection
  :start (jdbc/get-connection {:connection-uri (env :database-url)})
  :stop (.close notifications-connection))
```

Next, we'll add functions that will allow us to add and remove listeners for a given connection:

```clojure
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
```

Let's start the application by running `lein run` in the terminal. Once it starts, the nREPL will become available at `localhost:7000` (run `lein repl :connect 7000` in another terminal to connect). When the REPL is connected, run the following code in it to start the database connection and register a listener:

```clojure
(require :reload 'pg-feed-demo.db.core)

(in-ns 'pg-feed-demo.db.core)

(mount.core/start
  #'*db*
  #'notifications-connection)
                  
(add-listener
  notifications-connection
  "events" ;; maps to the TG_TABLE_NAME in the trigger above
  (fn [& args]
    (apply println "got message:" args)))
```

We can now test that adding a new message produces the notification:

```
(event! {:event "hello world"})
```

One the function runs, we should see something like the following printed in the terminal as the message is added to the database:

```
got message: 32427 messages INSERT (0,"hello world")
```

#### WebSocket connection

We're now ready to setup the WebSocket connection that will be used to push notifications to the clients. We'll update the `pg-feed-demo.routes.home`namespace to look as follows:

```clojure
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

```

The `channels` state will contain a set of all the channels for the currently connected clients.

The `event-listener` will create a new listener that's triggered when events are stored in the database. The handler function will broadcast each event to all the connected clients. Note that we need `^{:on-reload :noop}` metadata on the listener to prevent it being registered multiple times in case the namespace is reloaded during development.

Whenever the server receives a message from a client, the message will be persisted to the database by the `persist-event!` function.

Finally, we'll create the `/events` route that will be used to manage WebSocket communication with the clients.

### The client

The client will need to track the currently available messages, allow the user to send new messages to the server, and update the available messages based on server WebSocket notifications.

Let's run Figwheel to start the ClojureScript compiler before we start working on the client-side code by running the following command:

    lein figwheel

#### Re-frame events

We'll start by adding a handler for adding messages in the `pg-feed-demo.handlers` namespace:

```clojure
(reg-event-db
  :event
  (fn [db [_ event]]
    (update db :events (fnil conj []) event)))
```

Next, we'll add a corresponding subscription to see the current messages in the `pg-feed-demo.subscriptions` namespace:

```clojure    
(reg-sub
  :events
  (fn [db _]
    (:events db)))
```

#### WebSocket connection

We can now add a `pg-feed-demo.ws` namespace to manage the client-side of the WebSocket connection:

```clojure
(ns pg-feed-demo.ws)

(defonce ws-chan (atom nil))

(defn send
  [message]
  (if @ws-chan
    (.send @ws-chan message)
    (throw (js/Error. "Websocket is not available!"))))

(defn connect-ws [url handler]
  (if-let [chan (js/WebSocket. url)]
    (do
      (set! (.-onmessage chan) #(-> % .-data handler))
      (reset! ws-chan chan))
    (throw (js/Error. "Websocket connection failed!"))))
```

#### User interface

Finally, we'll update the `pg-feed-demo.core` namespace to list incoming events and allow the user to generate an event. To do that, We'll update the namespace to look as follows:

```clojure
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
```

That's all there is to it. We should now be able to send events to the server and see the notifications in the browser (open http://localhost:3000). We should also be able to generate events by running queries directly in the database, or in another instance of the application.
