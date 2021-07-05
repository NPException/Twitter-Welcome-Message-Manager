(ns de.npcomplete.twtwlcm.core
  (:require [reitit.ring :as ring]
            [reitit.coercion.spec :as rspec]
            [reitit.ring.coercion :as coercion]
            [ring.middleware.json :as mw-json]
            [ring.middleware.params :as mw-params]
            [ring.adapter.jetty :as jetty])
  (:gen-class))

(defn ^:private get-welcome-messages
  "Loads and returns the user's DM welcome messages"
  [user]
  ["I'm just a placeholder."])


(def ^:private router
  (ring/ring-handler
    (ring/router
      ["/get-welcome-messages"
       {:get {:parameters {:query {:user string?}}
              :responses {200 {:body vector?}}
              :handler (fn [{{{:keys [user]} :query} :parameters}]
                         {:status 200
                          :headers {"Access-Control-Allow-Origin" "*"}
                          :body (get-welcome-messages user)})}}]
      {:data {:coercion rspec/coercion
              :middleware [coercion/coerce-exceptions-middleware
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware]}})))


(defn ^:private wrap-empty-response
  [handler]
  (fn
    ([request]
     (or (handler request)
         {:status 400 :body "bad request"}))
    ([request respond raise]
     (handler request
              (fn [response]
                (respond response {:status 400 :body "bad request"}))
              raise))))


(defn -main
  "starts the server on a given :port (default 8080)"
  [& {:keys [port]
      :or   {port 8080}}]
  (jetty/run-jetty
    ((comp mw-params/wrap-params
           wrap-empty-response
           mw-json/wrap-json-response)
     #'router)
    {:port  port
     :join? false}))

;; development REPL snippets
(comment
  (def server (-main))
  (.stop server)
  )