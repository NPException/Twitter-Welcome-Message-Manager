(ns user
  (:require [com.twt-dm-tools.util :as u]))

(def ^:private server (atom nil))

(defn start-server []
  (swap! server #(do (when % (%))
                     (u/rr> com.twt-dm-tools.core/-main))))

(defn stop-server []
  (swap! server #(when % (%) nil)))
