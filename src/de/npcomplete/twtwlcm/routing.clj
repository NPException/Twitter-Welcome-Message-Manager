(ns de.npcomplete.twtwlcm.routing
  (:require [de.npcomplete.twtwlcm.middleware :as middleware]
            [de.npcomplete.twtwlcm.twitter-api :as twitter-api]
            [clojure.java.io :as io]
            [reitit.ring :as ring]
            [reitit.core :as reitit]
            [reitit.coercion.spec :as rspec]
            [reitit.ring.coercion :as coercion]))

(declare path-for-route)


(defn home-handler
  [{:keys [session] :as _request}]
  {:status 200
   :headers {"Content-Type" "text/html; charset=UTF-8"}
   :body (str
           (if-let [access-token (:oauth/access-token @session)]
             (str "Welcome <b>" (:screen_name access-token) "</b>!")
             (str "<a href='" (path-for-route :route/authenticate) "'>Click me!</a>")))})


(defn authenticate-handler
  [{:keys [session] :as _request}]
  (if-let [request-token (twitter-api/acquire-request-token!)]
    (do
      (swap! session assoc :oauth/request-token request-token)
      {:status 302
       :headers {"Location" (str "https://api.twitter.com/oauth/authorize?oauth_token=" (:oauth_token request-token))}})
    {:status 302
     :headers {"Location" "/"}}))


(defn oauth-callback-handler
  [{:keys [session query-params] :as _request}]
  (let [request-token (:oauth/request-token @session)
        {:strs [oauth_token oauth_verifier]} query-params
        access-token (when (= oauth_token (:oauth_token request-token))
                       (twitter-api/acquire-access-token! request-token oauth_verifier))]
    (when access-token
      (swap! session #(-> (assoc % :oauth/access-token access-token)
                          (dissoc :oauth/request-token))))
    {:status 302
     :headers {"Location" "/"}}))


(def ^:private router
  (ring/router
    [["/favicon.ico" {:get {:handler (constantly {:status 200
                                                  :body (io/file (io/resource "favicon.ico"))})}}]
     ["/" {:middleware [middleware/wrap-session]}
      ["" {:get {:handler home-handler}}]
      ["authenticate" {:name :route/authenticate
                       :get {:handler authenticate-handler}}]
      ["oauth" {:get {:parameters {:query {:oauth_token string?
                                           :oauth_verifier string?}}
                      :response {302 {:headers {"Location" string?}}}
                      :handler oauth-callback-handler}}]]]
    {:data {:coercion rspec/coercion
            :middleware [coercion/coerce-exceptions-middleware
                         coercion/coerce-request-middleware
                         coercion/coerce-response-middleware]}}))


(def path-for-route
  (comp :path (partial reitit/match-by-name router)))


(def ring-handler
  (ring/ring-handler router))
