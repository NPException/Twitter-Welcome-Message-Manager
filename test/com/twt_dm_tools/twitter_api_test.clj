(ns com.twt-dm-tools.twitter-api-test
  (:require [clojure.test :refer :all]
            [com.twt-dm-tools.twitter-api :as twt]))

;; Keys are from Twitters documentation. So they are not usable in the real world.

(def oauth-consumer-key "xvz1evFS4wEEPTGEFPHBog")
(def oauth-consumer-secret "kAcSOqF21Fu85e7zjz7ZN2U4ZRhfV3WpwPAoE3Z7kBw")

(def oauth-token "370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb")
(def oauth-token-secret "LswwdoUaIvS8ltyTt5jkRh4J50vUPVVHtR2YPi5kE")

(use-fixtures :once (fn [f]
                      (with-redefs
                        [twt/api-key oauth-consumer-key
                         twt/api-secret oauth-consumer-secret]
                        (f))))


(deftest create-oauth1.0-signature
  (is (= (#'twt/sign-oauth1
           :post "https://api.twitter.com/1.1/statuses/update.json"
           {:oauth_consumer_key oauth-consumer-key,
            :oauth_nonce "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg",
            :oauth_signature_method "HMAC-SHA1",
            :include_entities true
            :status "Hello Ladies + Gentlemen, a signed OAuth request!"
            :oauth_timestamp "1318622958",
            :oauth_token oauth-token,
            :oauth_version "1.0"}
           oauth-token-secret)
         "hCtSmYh+iHYCEqBWrE7C7hYmtUk=")))


(deftest add-authorization-header-to-request
  (with-redefs
    [twt/oauth-nonce (constantly "kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg")
     twt/oauth-timestamp (constantly 1318622958)]
    (let [request {:method :post
                   :url "https://api.twitter.com/1.1/statuses/update.json"
                   :query-params {:include_entities true}
                   :form-params {:status "Hello Ladies + Gentlemen, a signed OAuth request!"}}]
      (is (= (#'twt/authorize request oauth-token oauth-token-secret)
             (assoc request
               :headers {"Authorization" "OAuth oauth_consumer_key=\"xvz1evFS4wEEPTGEFPHBog\", oauth_nonce=\"kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg\", oauth_signature=\"hCtSmYh%2BiHYCEqBWrE7C7hYmtUk%3D\", oauth_signature_method=\"HMAC-SHA1\", oauth_timestamp=\"1318622958\", oauth_token=\"370773112-GmHxMAgYyLbNEtIKZeRNFsMKPR9EyMZeS9weJAEb\", oauth_version=\"1.0\""})))
      ;; TODO: this might be wrong. if it is, fix it. Else remove this comment.
      (is (= (#'twt/authorize request nil nil)
             (assoc request
               :headers {"Authorization" "OAuth oauth_consumer_key=\"xvz1evFS4wEEPTGEFPHBog\", oauth_nonce=\"kYjzVBB8Y0ZFabxSWbWovY3uYSQ2pTgmZeNu2VS4cg\", oauth_signature=\"SeAnFOsJg0uDVE8Coxfv5QdLNII%3D\", oauth_signature_method=\"HMAC-SHA1\", oauth_timestamp=\"1318622958\", oauth_version=\"1.0\""}))))))
