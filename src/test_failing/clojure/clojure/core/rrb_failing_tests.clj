(ns clojure.core.rrb-failing-tests
  (:require [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.reducers :as r])
  (:use clojure.test))


;; Setting expect-failures to true causes some of the tests below to
;; expect a particular kind of failure mode, e.g. an exception to be
;; thrown.  This setting is useful if you want to add a new test, or
;; try out a code change to core.rrb-vector, and see whether it
;; changes the behavior of any of the failing tests.

;; Setting expect-failures to false, and then changing core.rrb-vector
;; so that all tests pass, is the desirable outcome.

(def expect-failures true)
;;(def expect-failures false)



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
    (if expect-failures
      (is (thrown? NullPointerException
                   (= (seq v3) (range (inc bfactor-squared)))))
      (is (= (seq v3) (range (inc bfactor-squared)))))
    
    ;; This one causes a NullPointerException while traversing the seq
    (if expect-failures
      (is (thrown? NullPointerException
                   (= (seq v4) (range (inc bfactor-squared)))))
      (is (= (seq v4) (range (inc bfactor-squared)))))))



;; This problem reproduction code is from a comment by Mike Fikes on
;; 2018-Dec-09 for this issue:
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-20

(def my-vector-bad-rets (atom []))
(def my-catvec-bad-rets (atom []))
(def my-subvec-bad-rets (atom []))

(defn my-vector [vectype & more]
  (case vectype
    :rrb (let [ret (apply fv/vector more)]
           (when-let [err (seq (dv/ranges-not-int-array ret))]
             (println "ERROR: ret from my-vector has non int-array ranges")
             (swap! my-vector-bad-rets conj ret))
           ret)
    :core (apply clojure.core/vector more)))

(defn my-catvec [vectype v1 v2]
  (case vectype
    :rrb (let [ret (fv/catvec v1 v2)]
           (when-let [err (seq (dv/ranges-not-int-array ret))]
             (println "ERROR: ret from my-catvec has non int-array ranges")
             (swap! my-catvec-bad-rets conj ret))
           ret)
    :core (clojure.core/into v1 v2)))

(defn my-subvec
  ([vectype v x]
   (case vectype
     :rrb (let [ret (fv/subvec v x)]
            (when-let [err (seq (dv/ranges-not-int-array ret))]
              (println "ERROR: ret from my-subvec has non int-array ranges")
              (swap! my-subvec-bad-rets conj ret))
            ret)
     :core (clojure.core/subvec v x)))
  ([vectype v x y]
   (case vectype
     :rrb (let [ret (fv/subvec v x y)]
            (when-let [err (seq (dv/ranges-not-int-array ret))]
              (println "ERROR: ret from my-subvec has non int-array ranges")
              (swap! my-subvec-bad-rets conj ret))
            ret)
     :core (clojure.core/subvec v x y))))

(defn swap
  "If there are at least 'split-ndx' elements in the vector 'marbles',
  take the first 'split-ndx' of them and put them at the end, moving
  the remaining ones to the beginning.  Returns a new vector with
  those contents.  Returns a vector with elements in the original
  order if 'split-ndx' is 0."
  [vectype marbles split-ndx]
  (my-catvec vectype
    (my-subvec vectype marbles split-ndx)
    (my-subvec vectype marbles 0 split-ndx)))

(defn rotl
  "Throws an exception if the 'marbles' vector is empty.  If
  non-empty, moves the first (mod n (count marbles)) elements to the
  end using swap."
  [vectype marbles n]
  (swap vectype marbles (mod n (count marbles))))

(defn rotr
  [vectype marbles n]
  (swap vectype marbles (mod (- (count marbles) n) (count marbles))))

(defn place-marble
  [vectype marbles marble]
  (let [marbles (rotl vectype marbles 2)]
    (when-let [err (seq (dv/ranges-not-int-array marbles))]
      (println "ERROR: ret from rotl has non int-array ranges")
      (swap! my-catvec-bad-rets conj marbles))
    [(my-catvec vectype (my-vector vectype marble) marbles) 0]))

(defn remove-marble
  [vectype marbles marble]
  (let [marbles (rotr vectype marbles 7)
        first-marble (nth marbles 0)]
    [(my-subvec vectype marbles 1) (+ marble first-marble)]))

(defn play-round
  [vectype marbles round]
  (if (zero? (mod round 23))
    (remove-marble vectype marbles round)
    (place-marble vectype marbles round)))

(defn add-score [scores player round-score]
  (if (zero? round-score)
    scores
    (assoc scores player (+ (get scores player 0) round-score))))

(defn play [vectype players rounds]
  (loop [marbles (my-vector vectype 0)
         round   1
         player  1
         scores  {}
         ret     []]
    (let [[marbles round-score] (play-round vectype marbles round)
          scores (add-score scores player round-score)]
      (if (> round rounds)
        (conj ret {:round round :marbles marbles})
        (recur marbles
               (inc round)
               (if (= player players) 1 (inc player))
               scores
               (conj ret {:round round :marbles marbles}))))))

(deftest many-subvec-and-catvec-leads-to-exception
  ;; This one passes
  (is (= (play :core 10 1128) (play :rrb 10 1128)))
  ;; This ends up with (play :rrb 10 1129) throwing an exception
  (if expect-failures
    (is (thrown-with-msg? ClassCastException
                          #"clojure.lang.PersistentVector\$Node cannot be cast to \[I"
                          (= (play :core 10 1129) (play :rrb 10 1129))))
    (is (= (play :core 10 1129) (play :rrb 10 1129)))))
