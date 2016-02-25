(ns puppetlabs.missing-objects-as-a-service-web-core-test
  (:require [clojure.test :refer :all]
            [puppetlabs.missing-objects-as-a-service-service :as hello-svc]
            [puppetlabs.missing-objects-as-a-service-web-core :refer :all]
            [ring.mock.request :as mock]))

(deftest hello-web-test
  (testing "says hello to caller"
    (let [hello-service (reify hello-svc/HelloService
                          (hello [this caller] (format "Testing, %s." caller)))
          ring-app (app hello-service)
          response (ring-app (mock/request :get "/foo"))]
      (is (= 200 (:status response)))
      (is (= "Testing, foo." (:body response))))))
