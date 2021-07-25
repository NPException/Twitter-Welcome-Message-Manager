(ns de.npcomplete.twtwlcm.main-page
  (:require [cljstache.core :as stache]
            [clojure.java.io :as io]
            [de.npcomplete.twtwlcm.twitter-api :as twitter-api]))

(def ^:private main-template
  (slurp (io/resource "templates/main.mustache")))

(def ^:private landing-page
  (stache/render main-template {:authorized? false}))

(defn ^:private render-authorized
  [access-token]
  (let [user-name (:screen_name access-token)
        welcome-message (twitter-api/get-welcome-message! access-token)
        welcome-message-text (-> welcome-message :message_data :text)
        welcome-message-id (:id welcome-message)]
    (stache/render
      main-template
      {:authorized? true
       :user-name user-name
       :welcome-message-text welcome-message-text
       :welcome-message-id welcome-message-id})))

(defn render
  [{:keys [session] :as _request}]
  (if-let [access-token (:oauth/access-token @session)]
    (render-authorized access-token)
    landing-page))
