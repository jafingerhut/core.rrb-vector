(ns clojure.core.rrb-failing-tests
  (:require [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.reducers :as r])
  (:use clojure.test))


(deftest npe-for-1025-then-pop!
  (let [bfactor-squared (* 32 32)
        boundary 54
        v1 (-> (fv/vector)
               (into (range boundary))
               (into (range boundary (inc bfactor-squared))))
        v2 (-> (fv/vector)
               (into (range bfactor-squared))
               (transient)
               (pop!)
               (persistent!))
        v3 (-> (fv/vector)
               (into (range boundary))
               (into (range boundary (inc bfactor-squared)))
               (transient)
               (pop!)
               (persistent!))
        v4 (-> (fv/vector)
               (into (range (inc bfactor-squared)))
               (transient)
               (pop!)
               (persistent!))]
    ;; This test passes
    (is (= (seq v1) (range (inc bfactor-squared))))
    
    ;; This also passes
    (is (= (seq v2) (range (dec bfactor-squared))))
    
    ;; This fails with NullPointerException while traversing the seq
    (is (= (seq v3) (range (inc bfactor-squared))))
    
    ;; This one causes a NullPointerException while traversing the seq
    (is (= (seq v4) (range (inc bfactor-squared))))))
