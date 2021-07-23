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
  [http-method base-url params oauth-token-secret]
  (let [param-string (build-param-string params)
        signature-base (str (-> http-method name str/upper-case)
                            \& (percent-encode base-url)
                            \& (percent-encode param-string))
        signing-key (str api-secret \& oauth-token-secret)]
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
   (authorize request nil nil))
  ([{:keys [method url query-params form-params oauth-params] :as request}
    oauth-token oauth-token-secret]
   (let [oauth-params (-> {:oauth_consumer_key api-key
                           :oauth_nonce (oauth-nonce)
                           :oauth_signature_method "HMAC-SHA1"
                           :oauth_timestamp (oauth-timestamp)
                           :oauth_version "1.0"}
                          (cond-> oauth-params (merge oauth-params))
                          (cond-> oauth-token (assoc :oauth_token oauth-token)))
         params (merge query-params form-params oauth-params)
         oauth-signature (sign-oauth1 method url params oauth-token-secret)]
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
  (let [req (authorize {:method :post
                        :url "https://api.twitter.com/oauth/request_token"
                        :oauth-params {:oauth_callback oauth-callback-url}})
        resp @(http/request req)]
    (if-not (= 200 (:status resp))
      (println (str "Failed acquire-request-token call with status " (:status resp) ". Body: " (:body resp)))
      (response-string->map (:body resp)))))


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
  [{:keys [oauth_token oauth_token_secret] :as _request-token} oauth_verifier]
  (let [req (authorize {:method :post
                        :url "https://api.twitter.com/oauth/access_token"
                        :query-params {:oauth_token oauth_token
                                       :oauth_verifier oauth_verifier}}
                       oauth_token
                       oauth_token_secret)
        resp @(http/request req)]
    (if-not (= 200 (:status resp))
      (println (str "Failed acquire-access-token call with status " (:status resp) ". Body: " (:body resp)))
      (response-string->map (:body resp)))))


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
