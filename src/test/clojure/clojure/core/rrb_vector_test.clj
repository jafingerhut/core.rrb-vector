(ns clojure.core.rrb-vector-test
  (:require [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.reducers :as r]
            [clojure.test.check :as tc]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.generators :as gen])
  (:use clojure.test
        clojure.template)
  (:import (clojure.lang ExceptionInfo)
           (java.util NoSuchElementException)))

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
