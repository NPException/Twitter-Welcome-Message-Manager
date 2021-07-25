(ns com.twt-dm-tools.page
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [reagent.dom :as dom]
            [re-frame.core :as rf]
            [cljs.core.async :refer [<!]]))

(def <sub  (comp deref re-frame.core/subscribe))

;; value retrieval of the welcome message string
(rf/reg-sub
  :twt-welcome/welcome-message-str
  (fn [db _]
    (get-in db [:inputs :welcome-message-str] "")))

;; event listener for changes to the welcome message string
(rf/reg-event-db
  :twt-welcome/welcome-message-str-change
  (fn [db [_ value]]
    (-> db
        (assoc-in [:inputs :welcome-message-str] value))))


(defn app-view
  []
  [:div
   [:form
    [:header [:h1 "Scramblies"]]
    [:div
     [:label {:for "welcome-message-str"} "Welcome text"]
     [:input {:type        "text"
              :id          "welcome-message-str"
              :name        "welcome-message-str"
              :placeholder "Welcome to my DMs ..."
              :value       (<sub [:twt-welcome/welcome-message-str])
              :on-change   #(rf/dispatch [:twt-welcome/welcome-message-str-change (-> % .-target .-value)])}]]]])


(dom/render
  [app-view]
  (js/document.getElementById "app"))
