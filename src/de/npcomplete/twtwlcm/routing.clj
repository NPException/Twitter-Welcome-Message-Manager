(ns de.npcomplete.twtwlcm.routing
  (:require [de.npcomplete.twtwlcm.middleware :as middleware]
            [de.npcomplete.twtwlcm.twitter-api :as twitter-api]
            [de.npcomplete.twtwlcm.main-page :as main-page]
            [clojure.java.io :as io]
            [reitit.ring :as ring]
            [reitit.core :as reitit]
            [reitit.coercion.spec :as rspec]
            [reitit.ring.coercion :as coercion])
  (:import (java.io ByteArrayOutputStream)))

;; TODO: add some more request validation

(defn ^:private stream-bytes [is]
  (let [baos (ByteArrayOutputStream.)]
    (io/copy is baos)
    (.toByteArray baos)))

(def ^:private router
  (ring/router
    [["/favicon.ico" {:get {:handler (constantly {:status 200
                                                  :body (-> (io/resource "favicon.ico")
                                                            (io/input-stream)
                                                            (stream-bytes))})}}]
     ["/" {:middleware [middleware/wrap-session]}
      ["" {:name :route/main
           :get {:handler (fn [request]
                            {:status 200
                             :headers {"Content-Type" "text/html; charset=UTF-8"}
                             :body (main-page/render request)})}}]
      ["save-welcome-message" {:post {:handler (fn [request]
                                                 (twitter-api/new-welcome-message! request)
                                                 {:status 302, :headers {"Location" "/"}})}}]
      ["delete-welcome-message/:id" {:get {:handler (fn [request]
                                                      (twitter-api/remove-welcome-message! request)
                                                      {:status 302, :headers {"Location" "/"}})}}]
      ["authenticate" {:name :route/authenticate
                       :get {:handler (fn [request]
                                        {:status 302
                                         :headers {"Location" (twitter-api/start-oauth-flow! request)}})}}]
      ["logout" {:name :route/logout
                 :get {:handler (fn [request]
                                  ;; TODO un-authorize app at twitter if possible
                                  (swap! (:session request) select-keys [:session/id]) ;; reset the session
                                  {:status 302, :headers {"Location" "/"}})}}]
      ["oauth" {:name :route/oauth-callback
                :get {:parameters {:query {:oauth_token string?
                                           :oauth_verifier string?}}
                      :response {302 {:headers {"Location" string?}}}
                      :handler (fn [request]
                                 (twitter-api/finish-oauth-flow! request)
                                 {:status 302
                                  :headers {"Location" "/"}})}}]]]
    {:data {:coercion rspec/coercion
            :middleware [coercion/coerce-exceptions-middleware
                         coercion/coerce-request-middleware
                         coercion/coerce-response-middleware]}}))


(def path-for-route
  (comp :path (partial reitit/match-by-name router)))


(def ring-handler
  (ring/ring-handler router))
