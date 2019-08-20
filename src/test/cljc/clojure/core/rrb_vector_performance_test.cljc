(ns clojure.core.rrb-vector-performance-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.core.rrb-test-infra
             :refer [ex-message-copy ex-cause-copy peephole-opt-debug-fn
                     print-event-counts]]
            #?@(:clj ([clojure.java.io :as io]))
            [clojure.edn :as edn]
            [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.rrbt :as rrbt]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.rrb-vector.debug-platform-dependent :as dpd]
            [clojure.core.reducers :as r])
  #?@(:clj ((:import (clojure.lang ExceptionInfo)))))


(def plus-infinity #?(:clj Double/POSITIVE_INFINITY
                      :cljs ##Inf))

(deftest test-reduce-subvec-catvec2
  (letfn [(insert-by-sub-catvec [v n]
            (fv/catvec (fv/subvec v 0 n) (fv/vec ['x]) (fv/subvec v n)))
          (repeated-subvec-catvec [i]
            (reduce insert-by-sub-catvec
                    (vec (range i))
                    (take i (interleave (range (quot i 2) plus-infinity)
                                        (range (quot i 2) plus-infinity)))))]
    (let [n 22371
          v (repeated-subvec-catvec n)]
      (is (every? #(or (integer? %) (= 'x %)) v)))))


;; This problem reproduction code is from CRRBV-17 ticket:
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-17

(def benchmark-size 100000)

;; This small variation of the program in the ticket simply does
;; progress debug printing occasionally, as well as extra debug
;; checking of the results occasionally.

;; If you enable the printing of the message that begins
;; with "splice-rrbts result had shift" in function
;; fallback-to-slow-splice-if-needed, then run this test, you will see
;; it called hundreds or perhaps thousands of times.  The fallback
;; approach is effective at avoiding a crash for this scenario, but at
;; a dramatic extra run-time cost.

(defn vector-push-f [v n]
  (loop [v v
         i 0]
    (let [check? (zero? (mod i 10000))]
      (when check?
        (print "i=" i " ")
        (print-event-counts))
      (if (< i n)
        (recur (fv/catvec (fv/vector i) v)
               (inc i))
        v))))

(deftest test-crrbv-17
  (is (= (reverse (range benchmark-size))
         (vector-push-f (fv/vector) benchmark-size))))
