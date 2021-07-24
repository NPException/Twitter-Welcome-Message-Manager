(ns de.npcomplete.twtwlcm.twitter-api
  (:require [ring.util.codec :as codec]
            [org.httpkit.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:import (javax.crypto.spec SecretKeySpec)
           (javax.crypto Mac)
           (java.util UUID)))

(set! *warn-on-reflection* true)

;; App Key == API Key == Consumer API Key == Consumer Key == Customer Key == oauth_consumer_key
(def ^:private api-key (System/getenv "TWITTER_API_KEY"))
;; App Key Secret == API Secret Key == Consumer Secret == Consumer Key == Customer Key == oauth_consumer_secret
(def ^:private api-secret (System/getenv "TWITTER_API_SECRET"))
;; Bearer token
(def ^:private api-oauth-token (System/getenv "TWITTER_API_TOKEN"))

(def ^:private oauth-callback-url (System/getenv "TWITTER_CALLBACK_URL"))


(defn ^:private hmac
  "Calculate HMAC signature for given data."
  [^String key ^String data]
  (let [signing-key (SecretKeySpec. (.getBytes key) "HmacSHA1")
        mac (doto (Mac/getInstance "HmacSHA1") (.init signing-key))]
    (codec/base64-encode (.doFinal mac (.getBytes data)))))


(defn ^:private percent-encode
  [s]
  (-> (codec/url-encode s)
      (str/replace "+" "%2B")))


(defn ^:private build-param-string
  [params]
  (->> params
       (map (fn [[k v]] [(name k) (str v)]))
       (map #(mapv percent-encode %))
       (sort-by first)
       (map (fn [[k v]] (str k \= v)))
       (str/join \&)))


(defn ^:private sign-oauth1
  "Create an OAuth1.0 signature for the given method, url, and parameters"
  [http-method base-url params oauth_token_secret]
  (let [param-string (build-param-string params)
        signature-base (str (-> http-method name str/upper-case)
                            \& (percent-encode base-url)
                            \& (percent-encode param-string))
        signing-key (str api-secret \& oauth_token_secret)]
    (hmac signing-key signature-base)))


(defn ^:private build-oauth-header
  [oauth-params]
  (->> oauth-params
       (map (fn [[k v]] [(name k) (str v)]))
       (map #(mapv percent-encode %))
       (sort-by first)                                      ;; sort is needed for testability
       (map (fn [[k v]] (str k \= \" v \")))
       (str/join ", ")
       (str "OAuth ")))


(defn ^:private oauth-nonce []
  (.toString (UUID/randomUUID)))

(defn ^:private oauth-timestamp []
  (quot (System/currentTimeMillis) 1000))


(defn ^:private authorize
  "Associates a valid OAuth Authorization header onto the request"
  ([request]
   (authorize request nil))
  ([{:keys [method url query-params form-params oauth-params] :as request}
    {:keys [oauth_token oauth_token_secret] :as _token}]
   (let [oauth-params (-> {:oauth_consumer_key api-key
                           :oauth_nonce (oauth-nonce)
                           :oauth_signature_method "HMAC-SHA1"
                           :oauth_timestamp (oauth-timestamp)
                           :oauth_version "1.0"}
                          (cond-> oauth-params (merge oauth-params))
                          (cond-> oauth_token (assoc :oauth_token oauth_token)))
         params (merge query-params form-params oauth-params)
         oauth-signature (sign-oauth1 method url params oauth_token_secret)]
     (assoc-in request [:headers "Authorization"]
               (build-oauth-header (assoc oauth-params :oauth_signature oauth-signature))))))


(defn ^:private response-string->map
  "Takes one of Twitter's weird 'query-string-like' response strings
   and converts it to a proper map."
  [s]
  (reduce
    (fn [m kv-str]
      (let [[k v] (str/split kv-str #"=")]
        (assoc m (keyword k) v)))
    {}
    (str/split s #"&")))


(defn ^:private acquire-request-token!
  "Calls Twitter's API to retrieve a new set of request-token credentials"
  []
  (let [request {:method :post
                 :url "https://api.twitter.com/oauth/request_token"
                 :oauth-params {:oauth_callback oauth-callback-url}}
        response @(http/request (authorize request))]
    (if-not (= 200 (:status response))
      (println (str "Failed acquire-request-token call with status " (:status response) ". Body: " (:body response)))
      (response-string->map (:body response)))))


(defn start-oauth-flow!
  "Starts Twitter's 3-legged OAuth flow.
  First tries to acquire a request token. If successful, adds the token to the session and returns the
  redirect url to continue the authorization workflow on Twitter."
  [{:keys [session] :as _request}]
  (if-let [request-token (acquire-request-token!)]
    (do
      (swap! session assoc :oauth/request-token request-token)
      (str "https://api.twitter.com/oauth/authorize?oauth_token=" (:oauth_token request-token)))
    "/"))


(defn ^:private acquire-access-token!
  [{:keys [oauth_token] :as request-token} oauth_verifier]
  (let [request {:method :post
                 :url "https://api.twitter.com/oauth/access_token"
                 :query-params {:oauth_token oauth_token
                                :oauth_verifier oauth_verifier}}
        response @(http/request (authorize request request-token))]
    (if-not (= 200 (:status response))
      (println (str "Failed acquire-access-token call with status " (:status response) ". Body: " (:body response)))
      (response-string->map (:body response)))))


(defn finish-oauth-flow!
  "Finishes Twitter's 3-legged OAuth flow, by using the received token verifier to
  acquire an access token and add it to the session."
  [{:keys [session query-params] :as _request}]
  (let [request-token (:oauth/request-token @session)
        {:strs [oauth_token oauth_verifier]} query-params
        access-token (when (= oauth_token (:oauth_token request-token))
                       (acquire-access-token! request-token oauth_verifier))]
    (when access-token
      (swap! session #(-> (assoc % :oauth/access-token access-token)
                          (dissoc :oauth/request-token))))))


(def ^:private welcome-message-name "NPE twt-dm-tool welcome message")

;; message structure
(comment
  {:name #_optional "simple_welcome-message 01",
   :message_data {:text "Welcome!",
                  :attachment #_optional {:type "media",
                                          :media {:id "48909183894931"}}}})

(defn ^:private get-welcome-messages!
  "Twitter API endpoint to fetch welcome messages.
  See https://developer.twitter.com/en/docs/twitter-api/v1/direct-messages/welcome-messages/api-reference/list-welcome-messages"
  [access-token]
  (let [request {:method :get
                 :url "https://api.twitter.com/1.1/direct_messages/welcome_messages/list.json"}
        response @(http/request (authorize request access-token))]
    (if-not (= 200 (:status response))
      (println (str "Failed get-welcome-messages call with status " (:status response) ". Body: " (:body response)))
      (json/read-str (:body response) :key-fn keyword))))


(defn get-welcome-message!
  "Retrieves the welcome message that was created by this tool."
  [access-token]
  (some->> (get-welcome-messages! access-token)
           :welcome_messages
           (filter #(= (:name %) welcome-message-name))
           first))


;; TODO: support image/gif/video attachments
(defn ^:private create-welcome-message!
  "Twitter API endpoint to create a new welcome message.
  See https://developer.twitter.com/en/docs/twitter-api/v1/direct-messages/welcome-messages/api-reference/new-welcome-message"
  [access-token text]
  (let [request {:method :post
                 :url "https://api.twitter.com/1.1/direct_messages/welcome_messages/new.json"
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str
                         {:welcome_message
                          {:name welcome-message-name
                           :message_data {:text text}}})}
        response @(http/request (authorize request access-token))]
    (if-not (= 200 (:status response))
      (println (str "Failed create-welcome-message call with status " (:status response) ". Body: " (:body response)))
      (json/read-str (:body response) :key-fn keyword))))


(defn ^:private update-welcome-message!
  "Twitter API endpoint to update a welcome message.
  See https://developer.twitter.com/en/docs/twitter-api/v1/direct-messages/welcome-messages/api-reference/update-welcome-message"
  [access-token welcome-message-id text]
  (let [request {:method :put
                 :url "https://api.twitter.com/1.1/direct_messages/welcome_messages/update.json"
                 :headers {"Content-Type" "application/json"}
                 :query-params {:id welcome-message-id}
                 :body (json/write-str
                         {:message_data {:text text}})}
        response @(http/request (authorize request access-token))]
    (if-not (= 200 (:status response))
      (println (str "Failed update-welcome-message call with status " (:status response) ". Body: " (:body response)))
      (json/read-str (:body response) :key-fn keyword))))


(defn ^:private get-welcome-message-rules!
  "Twitter API endpoint to fetch welcome message rules.
  See https://developer.twitter.com/en/docs/twitter-api/v1/direct-messages/welcome-messages/api-reference/list-welcome-message-rules"
  [access-token]
  (let [request {:method :get
                 :url "https://api.twitter.com/1.1/direct_messages/welcome_messages/rules/list.json"}
        response @(http/request (authorize request access-token))]
    (if-not (= 200 (:status response))
      (println (str "Failed get-welcome-message-rules call with status " (:status response) ". Body: " (:body response)))
      (json/read-str (:body response) :key-fn keyword))))


(defn ^:private create-welcome-message-rule!
  "Twitter API endpoint to create a new welcome message rule.
  See https://developer.twitter.com/en/docs/twitter-api/v1/direct-messages/welcome-messages/api-reference/new-welcome-message-rule"
  [access-token welcome_message_id]
  (let [request {:method :post
                 :url "https://api.twitter.com/1.1/direct_messages/welcome_messages/rules/new.json"
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str
                         {:welcome_message_rule
                          {:welcome_message_id welcome_message_id}})}
        response @(http/request (authorize request access-token))]
    (if-not (= 200 (:status response))
      (println (str "Failed create-welcome-message-rule call with status " (:status response) ". Body: " (:body response)))
      (json/read-str (:body response) :key-fn keyword))))


(defn new-welcome-message!
  "Creates a new welcome message, if none is set yet. Returns true message was created successfully."
  [{:keys [session params] :as _request}]
  (boolean
    (when-let [access-token (:oauth/access-token @session)]
      (let [{:strs [welcome-message-text welcome-message-id]} params]
        (if welcome-message-id
          (update-welcome-message! access-token welcome-message-id welcome-message-text)
          (some->> (create-welcome-message! access-token welcome-message-text)
                   :welcome_message :id
                   (create-welcome-message-rule! access-token)))))))


(defn ^:private delete-welcome-message!
  "Deletes the welcome message with the given id. Returns true when successful.
  See https://developer.twitter.com/en/docs/twitter-api/v1/direct-messages/welcome-messages/api-reference/delete-welcome-message"
  [access-token welcome-message-id]
  (let [request {:method :delete
                 :url "https://api.twitter.com/1.1/direct_messages/welcome_messages/destroy.json"
                 :query-params {:id welcome-message-id}}
        response @(http/request (authorize request access-token))]
    (or (= 204 (:status response))
        (boolean (println (str "Failed delete-welcome-message call with status " (:status response) ". Body: " (:body response)))))))


(defn remove-welcome-message!
  [{:keys [session path-params] :as _request}]
  (when-let [access-token (:oauth/access-token @session)]
    (delete-welcome-message! access-token (:id path-params))))


(comment

  ;; STEP 1: get a new request token via
  (def request-token (acquire-request-token!))
  ;; STEP 2: redirect the user to Twitter
  (println (str "https://api.twitter.com/oauth/authorize?oauth_token=" (:oauth_token request-token)))
  ;; user is redirected to me. Collect query parameters:
  (def authorize-response {:oauth_token "same-as-in-request-token"
                           :oauth_verifier "abcdefghijk"})
  (def oauth_verifier (:oauth_verifier authorize-response))
  ;; STEP 3: convert request-token to access-token
  (acquire-access-token! request-token oauth_verifier)

  )
