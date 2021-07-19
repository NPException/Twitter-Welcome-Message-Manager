(ns de.npcomplete.twtwlcm.twitter-api
  (:require [ring.util.codec :as codec]
            [clojure.string :as str])
  (:import (javax.crypto.spec SecretKeySpec)
           (javax.crypto Mac)))

(set! *warn-on-reflection* true)

;; App Key == API Key == Consumer API Key == Consumer Key == Customer Key == oauth_consumer_key
(def ^:private api-key (System/getenv "TWITTER_API_KEY"))
;; App Key Secret == API Secret Key == Consumer Secret == Consumer Key == Customer Key == oauth_consumer_secret
(def ^:private api-secret (System/getenv "TWITTER_API_SECRET"))
;; Bearer token
(def ^:private api-token (System/getenv "TWITTER_API_TOKEN"))


(def ^:private oauth_consumer_key #_api-key "xvz1evFS4wEEPTGEFPHBog")
(def ^:private oauth_consumer_secret #_api-secret "kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw")


(def twt-request-token-url "https://api.twitter.com/oauth/request_token")


(defn hmac
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
        signing-key (str oauth_consumer_secret \& oauth-token-secret)]
    (hmac signing-key signature-base)))





(comment
  ;; signature test
  (= (sign-oauth1 :post "https://api.twitter.com/1.1/statuses/update.json"
                  {:oauth_consumer_key "xvz1evFS4wEEPTGEFPHBog",
                   :oauth_nonce "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg",
                   :oauth_signature_method "HMAC-SHA1",
                   :include_entities true
                   :status "Hello Ladies + Gentlemen, a signed OAuth request!"
                   :oauth_timestamp "1318622958",
                   :oauth_token "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb",
                   :oauth_version "1.0"}
                  "LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE")
     "hCtSmYh+iHYCEqBWrE7C7hYmtUk=")

  )

;include_entities=true&oauth_consumer_key=xvz1evFS4wEEPTGEFPHBog&oauth_nonce=kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg&oauth_signature_method=HMAC-SHA1&oauth_timestamp=1318622958&oauth_token=370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb&oauth_version=1.0&status=Hello%20Ladies%20%2B%20Gentlemen%2C%20a%20signed%20OAuth%20request%21
;include_entities=true&oauth_consumer_key=xvz1evFS4wEEPTGEFPHBog&oauth_nonce=kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg&oauth_signature_method=HMAC-SHA1&oauth_timestamp=1318622958&oauth_token=370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb&oauth_version=1.0&status=Hello%20Ladies%202B%20Gentlemen%2C%20a%20signed%20OAuth%20request%21