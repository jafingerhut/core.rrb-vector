(ns clojure.core.rrb-vector-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.rrb-vector.debug-platform-dependent :as dpd]))

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
