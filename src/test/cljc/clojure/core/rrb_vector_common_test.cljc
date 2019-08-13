(ns clojure.core.rrb-vector-common-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.rrb-vector.debug-platform-dependent :as dpd]
            [clojure.core.reducers :as r]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen])
  #?@(:clj ((:import (clojure.lang ExceptionInfo)))))


(def full-debug-opts {
                      ;;:trace true
                      :trace false
                      :validate true
                      :return-value-checks
                      [dv/edit-nodes-error-checks
                       dv/basic-node-error-checks
                       dv/ranges-error-checks]})

(reset! dv/debug-opts {:catvec full-debug-opts
                       :splice-rrbts full-debug-opts
                       :slicev full-debug-opts})


;;(def longer-generative-tests true)
(def longer-generative-tests false)

(deftest test-slicing
  (println "deftest test-slicing")
  (testing "slicing"
    (is (dv/check-subvec 32000 10 29999 1234 18048 10123 10191))))

(deftest test-slicing-generative
  (println "deftest test-slicing-generative")
  (testing "slicing (generative)"
    ;; TBD: What does dv/generative-check-subvec return on success?
    (is (try (if longer-generative-tests
               (dv/generative-check-subvec 250 200000 20)
               (dv/generative-check-subvec 125 100000 10))
             (catch ExceptionInfo e
               (throw (ex-info (dpd/format "%s: %s %s"
                                           (ex-message e)
                                           (:init-cnt (ex-data e))
                                           (:s&es (ex-data e)))
                               {}
                               (ex-cause e))))))))

(deftest test-splicing
  (println "deftest test-splicing")
  (testing "splicing"
    (is (dv/check-catvec 1025 1025 3245 1025 32768 1025 1025 10123 1025 1025))
    (is (dv/check-catvec 10 40 40 40 40 40 40 40 40))
    (is (apply dv/check-catvec (repeat 30 33)))))

(deftest test-splicing-generative
  (println "deftest test-splicing-generative")
  (testing "splicing (generative)"
    (is (try (if longer-generative-tests
               (dv/generative-check-catvec 250 30 10 60000)
               (dv/generative-check-catvec 125 15 10 30000))
             (catch ExceptionInfo e
               (throw (ex-info (dpd/format "%s: %s"
                                           (ex-message e)
                                           (:cnts (ex-data e)))
                               {}
                               (ex-cause e))))))))

(deftest test-reduce
  (println "deftest test-reduce")
  (let [v1 (vec (range 128))
        v2 (fv/vec (range 128))]
    (testing "reduce"
      (is (= (reduce + v1) (reduce + v2))))
    (testing "reduce-kv"
      (is (= (reduce-kv + 0 v1) (reduce-kv + 0 v2))))))

(deftest test-reduce-2
  (println "deftest test-reduce-2")
  (let [v1 (dv/dbg-subvec (vec (range 1003)) 500)
        v2 (vec (range 500 1003))]
    (is (= (reduce + 0 v1)
           (reduce + 0 v2)
           (reduce + 0 (r/map identity (seq v1)))
           (reduce + 0 (r/map identity (seq v2)))))))

(deftest test-seq
  (println "deftest test-seq")
  (let [v (fv/vec (range 128))
        s (seq v)]
    (testing "seq contents"
      (is (= v s)))
    (testing "chunked-seq?"
      (is (chunked-seq? s)))
    (testing "internal-reduce"
      (is (satisfies? #?(:clj clojure.core.protocols/InternalReduce
                         :cljs IReduce)
                      s)))))

(deftest test-assoc
  (println "deftest test-assoc")
  (let [v1 (fv/vec (range 40000))
        v2 (reduce (fn [out [k v]]
                     (assoc out k v))
                   (assoc v1 40000 :foo)
                   (map-indexed vector (rseq v1)))]
    (is (= (concat (rseq v1) [:foo]) v2)))
  (are [i] (= :foo
              (-> (range 40000)
                  (fv/vec)
                  (dv/dbg-subvec i)
                  (assoc 10 :foo)
                  (nth 10)))
       1 32 1024 32768))

(deftest test-assoc!
  (println "deftest test-assoc!")
  (let [v1 (fv/vec (range 40000))
        v2 (persistent!
            (reduce (fn [out [k v]]
                      (assoc! out k v))
                    (assoc! (transient v1) 40000 :foo)
                    (map-indexed vector (rseq v1))))]
    (is (= (concat (rseq v1) [:foo]) v2)))
  (are [i] (= :foo
              (-> (range 40000)
                  (fv/vec)
                  (dv/dbg-subvec i)
                  (transient)
                  (assoc! 10 :foo)
                  (persistent!)
                  (nth 10)))
       1 32 1024 32768))

(deftest test-relaxed
  (println "deftest test-relaxed")
  (is (= (into (dv/dbg-catvec (vec (range 123)) (vec (range 68))) (range 64))
         (concat (range 123) (range 68) (range 64))))
  (is (= (dv/slow-into (fv/catvec (vec (range 123)) (vec (range 68)))
                       (range 64))
         (concat (range 123) (range 68) (range 64)))))

(deftest test-hasheq
  (println "deftest test-hasheq")
  (let [v1 (vec (range 1024))
        v2 (vec (range 1024))
        v3 (dv/dbg-catvec (vec (range 512)) (vec (range 512 1024)))
        s1 (seq v1)
        s2 (seq v2)
        s3 (seq v3)]
    (is (= (hash v1) (hash v2) (hash v3) (hash s1) (hash s2) (hash s3)))
    (is (= (hash (nthnext s1 120))
           (hash (nthnext s2 120))
           (hash (nthnext s3 120))))))

(deftest test-reduce-subvec-catvec
  (println "deftest test-reduce-subvec-catvec")
  (letfn [(insert-by-sub-catvec [v n]
            (dv/dbg-catvec (dv/dbg-subvec v 0 n) (fv/vec ['x])
                           (dv/dbg-subvec v n)))
          (repeated-subvec-catvec [i]
            (reduce insert-by-sub-catvec (vec (range i)) (range i 0 -1)))]
    (is (= (repeated-subvec-catvec 2371)
           (interleave (range 2371) (repeat 'x))))))

(deftest test-splice-high-subtree-branch-count
  (println "deftest test-splice-high-subtree-branch-count")
  (let [x        (fv/vec (repeat 1145 \a))
        y        (dv/dbg-catvec (dv/dbg-subvec x 0 778) (dv/dbg-subvec x 778 779) [1] (dv/dbg-subvec x 779))
        z        (dv/dbg-catvec (dv/dbg-subvec y 0 780) [2] (dv/dbg-subvec y 780 781) (dv/dbg-subvec y 781))
        res      (dv/dbg-catvec (dv/dbg-subvec z 0 780) [] [3] (dv/dbg-subvec z 781))
        expected (concat (repeat 779 \a) [1] [3] (repeat 366 \a))]
    (is (= res expected))))

(comment
(require '[clojure.test :as t]
         '[clojure.core.rrb-vector-common-test :as ct])

;; To run individual test cases at REPL, just call the deftest name as
;; if it is a 0-arg function.
(ct/test-assoc!)
(ct/test-reduce-subvec-catvec)

;; To run all tests in one namespace, use clojure.test/run-tests on
;; the namespace name, as a symbol.
(t/run-tests 'clojure.core.rrb-vector-common-test)
)
