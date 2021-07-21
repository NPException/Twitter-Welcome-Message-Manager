(ns de.npcomplete.twtwlcm.core
  (:require [de.npcomplete.twtwlcm.twitter-api :as twitter-api]
            [reitit.ring :as ring]
            [reitit.coercion.spec :as rspec]
            [reitit.ring.coercion :as coercion]
            [ring.middleware.params :as mw-params]
            [ring.middleware.cookies :as mw-cookies]
            [org.httpkit.server :as server])
  (:gen-class)
  (:import (com.github.benmanes.caffeine.cache Cache Caffeine)
           (java.util.concurrent TimeUnit)
           (java.util UUID)
           (java.util.function Function)))

(def session-duration-minutes 30)

(def ^Cache sessions (-> (Caffeine/newBuilder)
                         (.expireAfterAccess session-duration-minutes TimeUnit/MINUTES)
                         (.build)))

(defn ^:private cookie-value [req key]
  (-> (:cookies req) (get key) :value))

;; TODO wrap session in middleware, but maybe don't create it by default
(defn ^:private get-session
  "Returns an active session if present."
  [req]
  (when-let [session-id (cookie-value req "session_id")]
    (.getIfPresent sessions session-id)))

(defn ^:private init-session
  "Returns an active session or initializes a new one if not present."
  [req]
  (let [session-id (-> (cookie-value req "session_id")
                       (or (.toString (UUID/randomUUID))))]
    (.get sessions session-id
          (reify Function
            (apply [_ id] {:id id})))))

(defn ^:private store-session!
  [session]
  (.put sessions (:id session) session))


(defn home-handler
  [req]
  (println "Home")
  (let [session (init-session req)]
    ;; TODO: check if session has twitter access-token. If not, show link to /authenticate
    {:status 200
     :headers {"Content-Type" "text/html; charset=UTF-8"}
     :cookies {"session_id" {:value (:id session)
                             :max-age (* session-duration-minutes 60)
                             :http-only true}}
     :body (str "<a href=\"/authenticate\">Click me!</a>")}))


(defn oauth-callback-handler
  [req]
  (let [session (init-session req)
        request-token (:oauth-request-token session)
        {:strs [oauth_token oauth_verifier]} (:query-params req)]
    (when (= oauth_token (:oauth_token request-token))
      (let [access-token (twitter-api/acquire-access-token! request-token oauth_verifier)
            session (-> session
                        (assoc :oauth-access-token access-token)
                        (dissoc :oauth-request-token))]
        (store-session! session)))
    {:status 302
     :headers {"Location" "/"}}))


(def ^:private router
  (ring/ring-handler
    (ring/router
      [["/" {:get {:handler home-handler}}]
       ;; TODO: start 3-legged oauth, and redirect to twitter
       ["/authenticate"]
       ["/oauth" {:get {:parameters {:query {:oauth_token string?
                                             :oauth_verifier string?}}
                        :response {302 {:headers {"Location" string?}}}
                        :handler oauth-callback-handler}}]]
      {:data {:coercion rspec/coercion
              :middleware [coercion/coerce-exceptions-middleware
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware]}})))


(defn ^:private wrap-empty-response
  [handler]
  (fn
    ([request]
     (let [resp (handler request)]
       (if (seq resp)
         resp
         {:status 400 :body "bad request"})))
    ([request respond raise]
     (handler request
              (fn [response]
                (respond response {:status 400 :body "bad request"}))
              raise))))


(defn -main
  "starts the server on a given :port (default 8080)"
  [& {:keys [port]
      :or {port 8080}}]
  (server/run-server
    ((comp mw-params/wrap-params
           mw-cookies/wrap-cookies
           wrap-empty-response)
     #'router)
    {:port port}))

;; development REPL snippets
(comment
  ;; start
  (def server (-main))
  ;; stop
  (server)
  )