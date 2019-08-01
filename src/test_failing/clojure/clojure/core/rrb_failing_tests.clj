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


(def extra-check-failures (atom []))

(defn wrap-fn-with-ret-checks [orig-fn err-desc-str ret-check-fn]
  (fn [& args]
    (let [ret (apply orig-fn args)]
      (apply ret-check-fn err-desc-str ret args)
      ret)))

(defn vector-ret-checks1 [err-desc-str ret & args]
  ;;(println "checking ret val from" err-desc-str)
  (when-let [err (seq (dv/ranges-not-int-array ret))]
    (println "ERROR:" err-desc-str "ret has non int-array ranges")
    (swap! extra-check-failures conj {:err-desc-str err-desc-str
                                      :ret ret
                                      :args args})))


(deftest npe-for-1025-then-pop!
  (let [extra-checks vector-ret-checks1]
    (with-redefs [vector (wrap-fn-with-ret-checks
                          fv/vector "clojure.core.rrb-vector/vector"
                          extra-checks)
                  into (wrap-fn-with-ret-checks
                        clojure.core/into "clojure.core/into"
                        extra-checks)
                  transient (wrap-fn-with-ret-checks
                             clojure.core/transient "clojure.core/transient"
                             extra-checks)
                  pop! (wrap-fn-with-ret-checks
                        clojure.core/pop! "clojure.core/pop!"
                        extra-checks)
                  persistent! (wrap-fn-with-ret-checks
                               clojure.core/persistent! "clojure.core/persistent!"
                               extra-checks)]
      (let [bfactor-squared (* 32 32)
            boundary 54
            v1 (-> (vector)
                   (into (range boundary))
                   (into (range boundary (inc bfactor-squared))))
            v2 (-> (vector)
                   (into (range bfactor-squared))
                   (transient)
                   (pop!)
                   (persistent!))
            v3 (-> (vector)
                   (into (range boundary))
                   (into (range boundary (inc bfactor-squared)))
                   (transient)
                   (pop!)
                   (persistent!))
            v4 (-> (vector)
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
          (is (= (seq v4) (range (inc bfactor-squared)))))))))


;; This problem reproduction code is from a comment by Mike Fikes on
;; 2018-Dec-09 for this issue:
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-20

;; icatvec is intended to be first rebound using with-redefs before
;; calling any of the functions below.

(def catvec nil)

(defn swap
  "If there are at least 'split-ndx' elements in the vector 'marbles',
  take the first 'split-ndx' of them and put them at the end, moving
  the remaining ones to the beginning.  Returns a new vector with
  those contents.  Returns a vector with elements in the original
  order if 'split-ndx' is 0."
  [marbles split-ndx]
  (catvec
    (subvec marbles split-ndx)
    (subvec marbles 0 split-ndx)))

(defn rotl
  "Throws an exception if the 'marbles' vector is empty.  If
  non-empty, moves the first (mod n (count marbles)) elements to the
  end using swap."
  [marbles n]
  (swap marbles (mod n (count marbles))))

(defn rotr [marbles n]
  (swap marbles (mod (- (count marbles) n) (count marbles))))

(defn place-marble
  [marbles marble]
  (let [marbles (rotl marbles 2)]
    [(catvec (vector marble) marbles) 0]))

(defn remove-marble [marbles marble]
  (let [marbles (rotr marbles 7)
        first-marble (nth marbles 0)]
    [(subvec marbles 1) (+ marble first-marble)]))

(defn play-round [marbles round]
  (if (zero? (mod round 23))
    (remove-marble marbles round)
    (place-marble marbles round)))

(defn add-score [scores player round-score]
  (if (zero? round-score)
    scores
    (assoc scores player (+ (get scores player 0) round-score))))

(defn play [players rounds]
  (loop [marbles (vector 0)
         round   1
         player  1
         scores  {}
         ret     []]
    (let [[marbles round-score] (play-round marbles round)
          scores (add-score scores player round-score)]
      (if (> round rounds)
        (conj ret {:round round :marbles marbles})
        (recur marbles
               (inc round)
               (if (= player players) 1 (inc player))
               scores
               (conj ret {:round round :marbles marbles}))))))

(defn play-core-plus-checks [& args]
  (let [extra-checks vector-ret-checks1]
    (with-redefs [catvec (wrap-fn-with-ret-checks
                          clojure.core/into "clojure.core/into"
                          extra-checks)
                  subvec (wrap-fn-with-ret-checks
                          clojure.core/subvec "clojure.core/subvec"
                          extra-checks)
                  vector (wrap-fn-with-ret-checks
                          clojure.core/vector "clojure.core/vector"
                          extra-checks)]
      (apply play args))))

(defn play-rrbv-plus-checks [& args]
  (let [extra-checks vector-ret-checks1]
    (with-redefs [catvec (wrap-fn-with-ret-checks
                          fv/catvec "clojure.core.rrb-vector/catvec"
                          extra-checks)
                  subvec (wrap-fn-with-ret-checks
                          fv/subvec "clojure.core.rrb-vector/subvec"
                          extra-checks)
                  vector (wrap-fn-with-ret-checks
                          fv/vector "clojure.core.rrb-vector/vector"
                          extra-checks)]
      (apply play args))))

(deftest many-subvec-and-catvec-leads-to-exception
  ;; This one passes
  (is (= (play-core-plus-checks 10 1128)
         (play-rrbv-plus-checks 10 1128)))
  ;; This ends up with (play :rrb 10 1129) throwing an exception
  (if expect-failures
    (is (thrown-with-msg? ClassCastException
                          #"clojure.lang.PersistentVector\$Node cannot be cast to \[I"
                          (= (play-core-plus-checks 10 1129)
                             (play-rrbv-plus-checks 10 1129))))
    (is (= (play-core-plus-checks 10 1129)
           (play-rrbv-plus-checks 10 1129)))))
