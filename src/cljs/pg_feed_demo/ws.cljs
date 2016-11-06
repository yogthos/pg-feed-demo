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
