(ns de.npcomplete.twtwlcm.middleware
  (:import (com.github.benmanes.caffeine.cache Cache Caffeine)
           (java.util.concurrent TimeUnit)
           (java.util UUID)
           (java.util.function Function)))

(def ^:private session-duration-minutes 30)

(def ^:private ^Cache sessions
  "Cache which holds session atoms."
  (-> (Caffeine/newBuilder)
      (.expireAfterAccess session-duration-minutes TimeUnit/MINUTES)
      (.build)))


(defn ^:private cookie-value [req key]
  (-> (:cookies req) (get key) :value))


(defn ^:private init-session
  "Returns an active session or initializes a new one if not present."
  [req]
  (let [session-id (-> (cookie-value req "session_id")
                       (or (.toString (UUID/randomUUID))))]
    (.get sessions session-id
          (reify Function
            (apply [_ id] (atom {:session/id id}))))))


(defn ^:private add-session-cookie-to-non-empty-response
  [response session-id]
  (if (seq response)
    (assoc-in response [:cookies "session_id"]
              {:value session-id,
               :max-age (* session-duration-minutes 60)})
    response))


(defn wrap-session
  "Makes sure that the request has its session attached,
  and also the session cookie is updated with every response."
  [handler]
  (fn
    ([request]
     (let [session (init-session request)
           session-id (:session/id @session)]
       (-> (assoc request :session session)
           (handler)
           (add-session-cookie-to-non-empty-response session-id))))
    ([request respond raise]
     (let [session (init-session request)
           session-id (:session/id @session)]
       (handler (assoc request :session session)
                (comp respond #(add-session-cookie-to-non-empty-response % session-id))
                raise)))))


(defn ^:private replace-empty-response
  [response]
  (if (seq response)
    response
    {:status 400 :body "bad request"}))


(defn wrap-empty-response
  "Ensures that instead of empty responses, a 400 bad request is sent back."
  [handler]
  (fn
    ([request]
     (replace-empty-response (handler request)))
    ([request respond raise]
     (handler request
              (comp respond replace-empty-response)
              raise))))
