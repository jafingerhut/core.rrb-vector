(ns clojure.core.rrb-vector-check
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.rrb-vector :as fv]
            [clojure.test.check.generators :as gen]
            [collection-check.core :refer [assert-vector-like]]
            [clojure.core.rrb-test-infra]))

(deftest collection-check
  (let [p 50000]
    (println "assert-vector-like" p "(fv/vector) gen/int)")
    (assert-vector-like p (fv/vector) gen/int))
  (is (every? nil? (.-array ^clojure.lang.PersistentVector$Node
                            (.-root ^clojure.lang.PersistentVector (vector))))))

;; Run time measurements for running (assert-vector-like p (fv/vector)
;; gen/int) on various platforms and with several values of p.

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; On this platform:
;; 2015 era MacBook Pro, macOS 10.13.6
;; VirtualBox Ubuntu 18.04.3 Desktop Linux
;; OpenJDK 11.0.4
;; Clojure 1.10.1

;; 1000 - ~ 13 ssec
;; 10000 - ~ 2 mins

;; After updating to com.gredericks/test.chuck version 0.2.10, the
;; latest as of 2019-Aug-14, using a parameter of 250 causes the test
;; to run for at least 2 hours (I have not let it finish after letting
;; it run that long).

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 2015 era MacBook Pro, macOS 10.13.6
;; VirtualBox Ubuntu 18.04.3 Desktop Linux
;; OpenJDK 11.0.4
;; Clojure 1.10.1

;; 200 - ??? > 13 mins
;; 250 - ??? > 2 hours - I have not finished a run yet with that value

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The run times below are with:
;; 2015 era MacBook Pro, macOS 10.13.6
;; Oracle JDK 1.8.0_192
;; Clojure 1.10.1

;; lower than 500 was less than 5 sec
;; 500 - 5 sec
;; 1000 - 12 sec

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The run times below are with:
;; 2015 era MacBook Pro, macOS 10.13.6
;; AdoptOpenJDK 12.0.1
;; Clojure 1.10.1

;; 250 - 3.8 sec
;; 1000 - 13 sec
;; 2000 - 25 sec
;; 5000 - ~ 60 sec
;; 10000 - ~ 120 sec
;; 20000 - ~ 230 sec
;; 30000 - ~ 360 sec
;; 40000 - ~ 440 sec
;; 50000 - ~ 560 sec
;; 100000 - ~ 1200 sec

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ClojureScript results:
;; 2015 era MacBook Pro, macOS 10.13.6
;; Oracle JDK 1.8.0_192
;; Node.js version 10.16.0
;; ClojureScript 1.10.520

;; 200 - ~ 22 sec
;; 300 - ~ 28 sec
;; 500 - ~ 51 sec
;; 1000 - ~ 105 sec
;; 2000 - ~ 190 sec
;; 5000 - ~ 485 sec
;; 10000 - ~ 1070 sec
;; 50000 - ~ 5273 sec

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ClojureScript results:
;; 2015 era MacBook Pro, macOS 10.13.6
;; VirtualBox Ubuntu 18.04.3 Desktop Linux
;; OpenJDK 11.0.4
;; Node.js version 8.10.0
;; ClojureScript 1.10.520

;; 250 - ~ 42 sec
;; 10000 - ~ 1540 sec
