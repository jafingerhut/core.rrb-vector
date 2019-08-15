(ns clojure.core.rrb-vector-check
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.rrb-vector :as fv]
            [clojure.test.check.generators :as gen]
            [collection-check.core :refer [assert-vector-like]]
            [clojure.core.rrb-test-infra]))

;; On this platform:
;; 2015 era MacBook Pro, macOS 10.13.6
;; VirtualBox Ubuntu 18.04.3 Desktop Linux
;; OpenJDK 11.0.4

;; the tests below with first parameter to assert-vector-like of
;; 10,000 took about 2 minutes to run.

;; 1000 - ~ 13 33sec
;; 10000 - ~ 2 mins

;; After updating to com.gredericks/test.chuck version 0.2.10, the
;; latest as of 2019-Aug-14, using a parameter of 250 causes the test
;; to run for at least 2 hours (I have not let it finish after letting
;; it run that long).

;; 50 - 0.2 sec
;; 100 - 0.6 sec
;; 150 - 1.5 sec
;; 200 - ??? > 13 mins
;; 250 - ??? > 2 hours - I have not finished a run yet with that value

(deftest collection-check
  (assert-vector-like 150 (fv/vector) gen/int)
  (is (every? nil? (.-array ^clojure.lang.PersistentVector$Node
                            (.-root ^clojure.lang.PersistentVector (vector))))))
