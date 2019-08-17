(ns clojure.core.rrb-long-tests
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.core.rrb-test-infra
             :refer [full-debug-opts set-debug-opts! ex-message-copy
                     ex-cause-copy print-event-counts]]
            [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.rrbt :as rrbt]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.rrb-vector.debug-platform-dependent :as dpd]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen])
  #?@(:clj ((:import (clojure.lang ExceptionInfo)))))


;;(def longer-generative-tests true)
(def longer-generative-tests false)

(set-debug-opts! full-debug-opts)

(deftest test-slicing-generative
  (testing "slicing (generative)"
    ;; TBD: What does dv/generative-check-subvec return on success?
    (is (try (if longer-generative-tests
               (dv/generative-check-subvec 250 200000 20)
               (dv/generative-check-subvec 125 100000 10))
             (catch ExceptionInfo e
               (throw (ex-info (dpd/format "%s: %s %s"
                                           (ex-message-copy e)
                                           (:init-cnt (ex-data e))
                                           (:s&es (ex-data e)))
                               {}
                               (ex-cause-copy e))))))))

(deftest test-splicing-generative
  (testing "splicing (generative)"
    (is (try (if longer-generative-tests
               (dv/generative-check-catvec 250 30 10 60000)
               (dv/generative-check-catvec 125 15 10 30000))
             (catch ExceptionInfo e
               (throw (ex-info (dpd/format "%s: %s"
                                           (ex-message-copy e)
                                           (:cnts (ex-data e)))
                               {}
                               (ex-cause-copy e))))))))


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

(defn vector-push-f [v]
  (loop [v v
         i 0]
    (let [check? (or (zero? (mod i 10000))
                     (and (> i 99000) (zero? (mod i 100)))
                     (and (> i 99900)))]
      (when check?
        (print "i=" i " ")
        (print-event-counts))
      (if (< i benchmark-size)
        (recur (if check?
                 (dv/dbg-catvec (fv/vector i) v)
                 (fv/catvec (fv/vector i) v))
               (inc i))
        v))))

(deftest test-crrbv-17
  (is (= (reverse (range benchmark-size))
         (vector-push-f (fv/vector)))))
