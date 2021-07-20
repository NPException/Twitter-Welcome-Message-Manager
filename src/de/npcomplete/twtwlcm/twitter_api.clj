(ns de.npcomplete.twtwlcm.twitter-api
  (:require [ring.util.codec :as codec]
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


(def ^:private twt-request-token-url "https://api.twitter.com/oauth/request_token")


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
       #_(map (fn [[k v]]
                (str (percent-encode (name k)) \= \" (percent-encode (str v)) \")))
       (map (fn [[k v]] [(name k) (str v)]))
       (map #(mapv percent-encode %))
       (sort-by first)
       (map (fn [[k v]] (str k \= \" v \")))
       (str/join ", ")
       (str "OAuth ")))


(defn ^:private oauth-nonce []
  (.toString (UUID/randomUUID)))

(defn ^:private oauth-timestamp []
  (quot (System/currentTimeMillis) 1000))


(defn ^:private authorize
  [{:keys [method url query-params form-params] :as request}
   oauth-token oauth-token-secret]
  (let [oauth-params (cond->
                       {:oauth_consumer_key api-key
                        :oauth_nonce (oauth-nonce)
                        :oauth_signature_method "HMAC-SHA1"
                        :oauth_timestamp (oauth-timestamp)
                        :oauth_version "1.0"}
                       oauth-token (assoc :oauth_token oauth-token))
        params (merge query-params form-params oauth-params)
        oauth-signature (sign-oauth1 method url params oauth-token-secret)]
    (assoc-in request [:headers "Authorization"]
              (build-oauth-header (assoc oauth-params :oauth_signature oauth-signature)))))
