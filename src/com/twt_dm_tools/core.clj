(ns com.twt-dm-tools.core
  (:require [com.twt-dm-tools.routing :as routing]
            [ring.middleware.params :as mw-params]
            [ring.middleware.cookies :as mw-cookies]
            [org.httpkit.server :as server])
  (:gen-class))

;; TODO: move UI to CLJS

(defn -main
  "starts the server on a given :port (default 8080)"
  [& {:strs [port]
      :or {port "8080"} :as _args}]
  (server/run-server
    ((comp mw-params/wrap-params
           mw-cookies/wrap-cookies)
     #'routing/ring-handler)
    {:port (Integer/parseInt port)})
  (println "Started server at port" port))
