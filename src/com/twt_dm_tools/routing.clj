(ns com.twt-dm-tools.routing
  (:require [com.twt-dm-tools.middleware :as middleware]
            [com.twt-dm-tools.twitter-api :as twitter-api]
            [com.twt-dm-tools.main-page :as main-page]
            [clojure.java.io :as io]
            [spec-tools.data-spec :as ds]
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


(declare oauth-callback-route)

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
                                         :headers {"Location" (twitter-api/start-oauth-flow! request oauth-callback-route)}})}}]
      ["logout" {:name :route/logout
                 :get {:handler (fn [request]
                                  (some-> request :session deref :oauth/access-token
                                          (twitter-api/invalidate-access-token!))
                                  (swap! (:session request) select-keys [:session/id]) ;; reset the session
                                  {:status 302, :headers {"Location" "/"}})}}]
      ["oauth" {:name :route/oauth-callback
                :get {:parameters {:query {(ds/opt :oauth_token) string?
                                           (ds/opt :oauth_verifier) string?
                                           (ds/opt :denied) string?}}
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


(def ^:private oauth-callback-route (path-for-route :route/oauth-callback))
