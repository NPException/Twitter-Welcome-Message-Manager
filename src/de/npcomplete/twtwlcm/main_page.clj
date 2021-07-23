(ns de.npcomplete.twtwlcm.main-page
  (:require [cljstache.core :as stache]
            [clojure.java.io :as io]))

(def ^:private main-template
  (slurp (io/resource "templates/main.mustache")))

;; TODO remove
(def atoken (atom nil))

(defn render
  [{:keys [session] :as _request}]
  (let [session-data @session
        access-token (:oauth/access-token session-data)
        authorized? (boolean access-token)
        user-name (when authorized?
                    (-> session-data :oauth/access-token :screen_name))
        welcome-message-text (when authorized?
                               ;; TODO fetch message
                               )]
    (reset! atoken access-token)
    (stache/render
      main-template
      {:authorized? authorized?
       :user-name user-name
       :welcome-message-text welcome-message-text})))
