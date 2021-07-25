(ns com.twt-dm-tools.core-test
  (:require [clojure.test :refer :all]
            [com.twt-dm-tools.core :as core])
  (:import (org.eclipse.jetty.server Server)))

(deftest server-test
  (let [^Server server (core/-main :port 3000)]
    ;; TODO: test some basic requests
    (.stop server)))
