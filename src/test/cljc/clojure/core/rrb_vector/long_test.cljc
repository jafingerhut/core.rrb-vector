(ns clojure.core.rrb-vector.long-test
  (:require [clojure.test :as test :refer [deftest testing is are]]
            [clojure.core.rrb-vector.test-infra :as infra
             :refer [ex-message-copy ex-cause-copy print-event-counts]]
            [clojure.core.rrb-vector.test-utils :as u]
            [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.rrb-vector.debug-platform-dependent :as dpd])
  #?@(:clj ((:import (clojure.lang ExceptionInfo)))))

(dv/set-debug-opts! dv/full-debug-opts)

(def generative-test-length :short)

(def check-subvec-params (case generative-test-length
                           :short  [125 100000 10]
                           :medium [250 200000 20]
                           :long   [250 200000 20]))

(deftest test-slicing-generative
  (testing "slicing (generative)"
    ;; TBD: What does dv/generative-check-subvec return on success?
    (is (try
          (apply dv/generative-check-subvec u/extra-checks? check-subvec-params)
          (catch ExceptionInfo e
            (throw (ex-info (dpd/format "%s: %s %s"
                                        (ex-message-copy e)
                                        (:init-cnt (ex-data e))
                                        (:s&es (ex-data e)))
                            {}
                            (ex-cause-copy e))))))))

;; short: 2 to 3 sec
;; medium: 50 to 60 sec
(def check-catvec-params (case generative-test-length
                           :short  [ 10 30 10 60000]
                           :medium [250 30 10 60000]
                           :long   [250 30 10 60000]))

(deftest test-splicing-generative
  (testing "splicing (generative)"
    (is (try
          (apply dv/generative-check-catvec u/extra-checks? check-catvec-params)
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

(defn vector-push-f [v my-catvec extra-checks-catvec]
  (loop [v v
         i 0]
    (let [check? (or (zero? (mod i 10000))
                     (and (> i 99000) (zero? (mod i 100)))
                     (and (> i 99900) (zero? (mod i 10))))]
      (when check?
        (print "i=" i " ")
        (print-event-counts))
      (if (< i benchmark-size)
        (recur (if check?
                 (extra-checks-catvec (fv/vector i) v)
                 (my-catvec (fv/vector i) v))
               (inc i))
        v))))

;; Approximate run times for this test on a 2015 MacBook Pro
;;  36 sec - clj 1.10.1, OpenJDK 11.0.4
;; 465 sec - cljs 1.10.439, OpenJDK 11.0.4, Nashorn JS runtime
;; 138 sec - cljs 1.10.238, OpenJDK 11.0.4, nodejs 8.10.0
;; 137 sec - cljs 1.10.238, OpenJDK 11.0.4, Spidermonkey JavaScript-C52.9.1
(deftest test-crrbv-17
  (u/reset-optimizer-counts!)
  (is (= (reverse (range benchmark-size))
         (vector-push-f (fv/vector) fv/catvec dv/checking-catvec))))
