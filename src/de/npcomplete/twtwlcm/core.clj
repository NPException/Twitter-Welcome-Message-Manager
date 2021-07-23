(ns de.npcomplete.twtwlcm.core
  (:require [de.npcomplete.twtwlcm.routing :as routing]
            [ring.middleware.params :as mw-params]
            [ring.middleware.cookies :as mw-cookies]
            [org.httpkit.server :as server])
  (:gen-class))

(defn -main
  "starts the server on a given :port (default 8080)"
  [& {:keys [port]
      :or {port 8080}}]
  (server/run-server
    ((comp mw-params/wrap-params
           mw-cookies/wrap-cookies)
     #'routing/ring-handler)
    {:port port}))
