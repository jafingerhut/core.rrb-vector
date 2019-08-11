(ns clojure.core.rrb-vector-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.rrb-vector.debug-platform-dependent :as dpd]))

(deftest test-slicing
  (is (= true (dv/check-subvec 32000 10 29999 1234 18048 10123 10191))))

(deftest test-slicing-generative
  ;; TBD: What does dv/generative-check-subvec return on success?
  (is (try (dv/generative-check-subvec 125 100000 10)
           (catch ExceptionInfo e
             (throw (ex-info (dpd/format "%s: %s %s"
                                         (ex-message e)
                                         (:init-cnt (ex-data e))
                                         (:s&es (ex-data e)))
                             {}
                             (ex-cause e)))))))

(deftest test-splicing
  (is (= true (dv/check-catvec 1025 1025 3245 1025 32768 1025 1025 10123 1025 1025)))
  (is (= true (dv/check-catvec 10 40 40 40 40 40 40 40 40)))
  (is (= true (apply dv/check-catvec (repeat 30 33)))))

(deftest test-splicing-generative
  (is (try (dv/generative-check-catvec 125 15 10 30000)
           (catch ExceptionInfo e
             (throw (ex-info (dpd/format "%s: %s"
                                         (.getMessage e)
                                         (:cnts (ex-data e)))
                             {}
                             (.getCause e)))))))

(deftest test-reduce
  (let [v1 (vec (range 128))
        v2 (fv/vec (range 128))]
    (is (= (reduce + v1) (reduce + v2)))
    (is (= (reduce-kv + 0 v1) (reduce-kv + 0 v2)))))

(deftest test-seq
  (let [v (fv/vec (range 128))
        s (seq v)]
    (is (= v s))
    (is (chunked-seq? s))
    (is (satisfies? IReduce s))))

(deftest test-assoc
  (let [v1 (fv/vec (range 40000))
        v2 (reduce (fn [out [k v]]
                     (assoc out k v))
                   (assoc v1 40000 :foo)
                   (map-indexed vector (rseq v1)))]
    (is (= (concat (rseq v1) [:foo]) v2)))
  (loop [i 1]
    (if (< i 35000)
      (let [v (-> (range 40000)
                  (fv/vec)
                  (fv/subvec i)
                  (assoc 10 :foo))]
        (is (= :foo (nth v 10)))
        (recur (* i 32))))))

(deftest test-assoc!
  (let [v1 (fv/vec (range 40000))
        v2 (persistent!
            (reduce (fn [out [k v]]
                      (assoc! out k v))
                    (assoc! (transient v1) 40000 :foo)
                    (map-indexed vector (rseq v1))))]
    (is (= (concat (rseq v1) [:foo]) v2)))
  (loop [i 1]
    (if (< i 35000)
      (let [v (-> (range 40000)
                  (fv/vec)
                  (fv/subvec i)
                  (transient)
                  (assoc! 10 :foo)
                  (persistent!))]
        (is (= :foo (nth v 10)))
        (recur (* i 32))))))

(deftest test-relaxed
  (is (= (into (fv/catvec (vec (range 123)) (vec (range 68))) (range 64))
         (concat (range 123) (range 68) (range 64))))
  (is (= (dpd/slow-into (fv/catvec (vec (range 123)) (vec (range 68)))
                        (range 64))
         (concat (range 123) (range 68) (range 64)))))

(def test-splice-high-subtree-branch-count
  (let [x        (fv/vec (repeat 1145 \a))
        y        (fv/catvec (fv/subvec x 0 778) (fv/subvec x 778 779) [1] (fv/subvec x 779))
        z        (fv/catvec (fv/subvec y 0 780) [2] (fv/subvec y 780 781) (fv/subvec y 781))
        res      (fv/catvec (fv/subvec z 0 780) [] [3] (fv/subvec z 781))
        expected (concat (repeat 779 \a) [1] [3] (repeat 366 \a))]
    (is (= res expected))))

(deftest test-reduce-subvec-catvec
  (letfn [(insert-by-sub-catvec [v n]
            (fv/catvec (fv/subvec v 0 n) (fv/vec ['x]) (fv/subvec v n)))
          (repeated-subvec-catvec [i]
            (reduce insert-by-sub-catvec (fv/vec (range i)) (range i 0 -1)))]
    (is (= (repeated-subvec-catvec 2371)
           (interleave (range 2371) (repeat 'x))))))

(comment
(require '[clojure.test :as t]
         '[clojure.core.rrb-vector-test :as ft])

;; To run individual test cases at REPL, just call the deftest name as
;; if it is a 0-arg function.
(ft/test-assoc!)
(ft/test-reduce-subvec-catvec)

;; To run all tests in one namespace, use clojure.test/run-tests on
;; the namespace name, as a symbol.
(t/run-tests 'clojure.core.rrb-vector-test)
)
