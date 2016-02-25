(ns puppetlabs.missing-objects-as-a-service-core-test
  (:require [clojure.test :refer :all]
            [puppetlabs.missing-objects-as-a-service-core :refer :all]))

(deftest hello-test
  (testing "says hello to caller"
    (is (= "Hello, foo!" (hello "foo")))))
