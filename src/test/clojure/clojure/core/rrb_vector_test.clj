(ns clojure.core.rrb-vector-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.template :refer [do-template]]
            [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.reducers :as r]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen])
  (:import (java.util NoSuchElementException)))

(deftest test-iterators
  (let [v (fv/catvec (vec (range 1000)) (vec (range 1000 2048)))]
    (is (= (iterator-seq (.iterator ^Iterable v))
           (iterator-seq (.iterator ^Iterable (seq v)))
           (iterator-seq (.listIterator ^java.util.List v))
           (iterator-seq (.listIterator ^java.util.List (seq v)))
           (range 2048)))
    (is (= (iterator-seq (.listIterator ^java.util.List v 100))
           (iterator-seq (.listIterator ^java.util.List (seq v) 100))
           (range 100 2048)))
    (letfn [(iterator [xs]
              (.iterator ^Iterable xs))
            (list-iterator
              ([xs]
                 (.listIterator ^java.util.List xs))
              ([xs start]
                 (.listIterator ^java.util.List xs start)))]
      (do-template [iexpr cnt]
        (is (thrown? NoSuchElementException
              (let [iter iexpr]
                (dotimes [_ (inc cnt)]
                  (.next ^java.util.Iterator iter)))))
        (iterator v)                2048
        (iterator (seq v))          2048
        (list-iterator v)           2048
        (list-iterator (seq v))     2048
        (list-iterator v 100)       1948
        (list-iterator (seq v) 100) 1948))))

(deftest test-reduce-subvec-catvec-generative
  (letfn [(insert-by-sub-catvec [v n]
            (fv/catvec (fv/subvec v 0 n) (fv/vec ['x]) (fv/subvec v n)))
          (repeated-subvec-catvec [i]
            (reduce insert-by-sub-catvec (vec (range i)) (range i 0 -1)))]
    (is (tc/quick-check 100
          (prop/for-all [cnt (gen/fmap
                               (comp inc #(mod % 60000))
                               gen/pos-int)]
            (= (repeated-subvec-catvec cnt)
               (interleave (range cnt) (repeat 'x))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This code was copied from the issue:
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-13

(defn assoc-in-bytevec [my-vector-of use-transient? n indices]
  (let [coll (into (my-vector-of :byte) (range n))
        coll2 (reduce (fn [coll i]
                        (if use-transient?
                          (assoc! coll i -1)
                          (assoc coll i -1)))
                      (if use-transient?
                        (transient coll)
                        coll)
                      indices)]
    (if use-transient?
      (persistent! coll2)
      coll2)))

(defn assoc-in-bytevec-core [& args]
  (apply assoc-in-bytevec clojure.core/vector-of args))

(defn assoc-in-bytevec-rrbv [& args]
  (apply assoc-in-bytevec fv/vector-of args))

(deftest test-crrbv-13
  (println "deftest test-crrbv-13")
  ;; Some cases work, probably the ones where the tail is being
  ;; updated.
  (doseq [use-transient? [false true]]
    (doseq [args [[10 [5]]
                  [32 [0]]
                  [32 [32]]
                  [64 [32]]
                  [64 [64]]]]
      (is (= (apply assoc-in-bytevec-core false args)
             (apply assoc-in-bytevec-rrbv use-transient? args))
          (str "args=" (cons use-transient? args))))
    (doseq [args [[64 [0]]
                  [64 [1]]
                  [64 [31]]]]
      (is (= (apply assoc-in-bytevec-core false args)
             (apply assoc-in-bytevec-rrbv use-transient? args))
          (str "args=" (cons use-transient? args))))))

;; See comment block at end of namespace
;; clojure.core.rrb-vector-common-test for instructions on how to run
;; selected tests in a REPL session.
