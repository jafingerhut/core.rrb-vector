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
  (let [i (dv/edit-nodes-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (swap! extra-check-failures conj {:err-desc-str err-desc-str
                                        :ret ret
                                        :args args
                                        :edit-nodes-errors i})))
  (when-let [err (seq (dv/ranges-not-int-array ret))]
    (println "ERROR:" err-desc-str "ret has non int-array ranges")
    (swap! extra-check-failures conj {:err-desc-str err-desc-str
                                      :ret ret
                                      :args args}))
  (let [i (dv/basic-node-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (swap! extra-check-failures conj {:err-desc-str err-desc-str
                                        :ret ret
                                        :args args
                                        :basic-node-errors i})))
  (let [i (dv/ranges-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (swap! extra-check-failures conj {:err-desc-str err-desc-str
                                        :ret ret
                                        :args args
                                        :ranges-errors i}))))


(defn npe-for-1025-then-pop! []
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
      (is (= (seq v4) (range (inc bfactor-squared)))))))

(deftest npe-for-1025-then-pop!-tests
  (doseq [kind [:object-array :long-array]]
    (let [extra-checks vector-ret-checks1]
      (with-redefs [vector (case kind
                             :object-array
                             (wrap-fn-with-ret-checks
                              fv/vector "clojure.core.rrb-vector/vector"
                              extra-checks)
                             :long-array
                             (wrap-fn-with-ret-checks
                              #(fv/vector-of :long) "clojure.core.rrb-vector/vector-of :long"
                              extra-checks))
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
        (npe-for-1025-then-pop!)))))


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
  ;; This ends up with (play-rrbv-plus-checks 10 1129) throwing an exception
  (if expect-failures
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Assigning index 32 of vector object array to become a node"
                          (= (play-core-plus-checks 10 1129)
                             (play-rrbv-plus-checks 10 1129))))
    (is (= (play-core-plus-checks 10 1129)
           (play-rrbv-plus-checks 10 1129))))

  ;; The previous test demonstrates a bug in the transient RRB vector
  ;; implementation.  The one below demonstrates a similar bug in the
  ;; persistent RRB vector implementation.
  (let [v1128 (:marbles (last (play-rrbv-plus-checks 10 1128)))
        v1129-pre (-> v1128
                      (fv/subvec 2)
                      (conj 2001))]
    (if expect-failures
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"Assigning index 32 of vector object array to become a node"
                            (conj v1129-pre 2002)))
      (is (every? integer? (conj v1129-pre 2002)))))

  ;; The following sequence of operations gives a different exception
  ;; than the above, and I suspect is probably a different root cause
  ;; with a distinct fix required.  It might be the same root cause as
  ;; npe-for-1025-then-pop! but I will add a separate test case until
  ;; I know for sure.  Even if they are the same root cause, it does
  ;; not take long to run.

  ;; Note: Even once this bug is fixed, I want to know the answer to
  ;; whether starting from v1128 and then pop'ing off each number of
  ;; elements, until it is down to empty or very nearly so, causes any
  ;; of the error checks within the current version of ranges-errors
  ;; to give an error.  It may require some correcting.
  (let [v1128 (:marbles (last (play-rrbv-plus-checks 10 1128)))
        vpop1 (reduce (fn [v i] (pop v))
                      v1128 (range 1026))]
    (if expect-failures
      (is (thrown? NullPointerException
                   (pop vpop1)))
      (is (every? integer? (pop vpop1))))
    ;; The transient version below gives a similar exception, but the
    ;; call stack goes through the transient version of popTail,
    ;; rather than the persistent version of popTail that the one
    ;; above does.  It seems likely that both versions of popTail have
    ;; a similar bug.
    (if expect-failures
      (is (thrown? NullPointerException
                   (pop! (transient vpop1))))
      (is (every? integer? (pop! (transient vpop1)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This code was copied from
;; https://github.com/mattiasw2/adventofcode1/blob/master/src/adventofcode1/nineteen_b.clj

;; mentioned in issue
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-14

(defn remove-at
  "Remove cell at idx in arr."
  [arr idx]
  (catvec (subvec arr 0 idx) (subvec arr (inc idx))))


(defn create-arr
  "Return a vector with pair [1 idx] where idx starts at 1...size (incl)."
  [size]
  (vec (for [x (range 1 (inc size))]
         [1 x])))

(defn fv-rest
  [arr]
  (subvec arr 1))

(defn calculate-opposite
  "n is the number of elfs incl me. Im a at pos 0.
   Return the opposite position."
  [n]
  (int (/ n 2)))

(defn move
  [elfs]
  (let [lc (count elfs)]
    (if (= 1 lc)
      {:ok (first elfs)}
      (let [current      (first elfs)
            opposite-pos (calculate-opposite lc)
            _ (assert (> opposite-pos 0))
            _ (assert (< opposite-pos lc))
            opposite-elf (nth elfs opposite-pos)
            other2       (fv-rest (remove-at elfs opposite-pos))
            current2     [(+ (first current) (first opposite-elf))
                          (second current)]]
        (catvec other2 [current2])))))


(defn puzzle-b-sample
  ([] (puzzle-b-sample (create-arr 5)))
  ([elfs] (let [elfs2 (move elfs)]
            (if (:ok elfs2)
              (:ok elfs2)
              ;;(println elfs2)
              (recur elfs2)))))

#_(s/fdef puzzle-b
        :args (s/cat :n (s/and int? pos?))
        :ret  (s/coll-of int?))

(defn puzzle-b
  ([] (puzzle-b 3014603))
  ([n] (puzzle-b-sample (create-arr n))))

(defn puzzle-b-core-plus-checks [& args]
  (let [extra-checks vector-ret-checks1]
    (with-redefs [catvec (wrap-fn-with-ret-checks
                          clojure.core/into "clojure.core/into"
                          extra-checks)
                  subvec (wrap-fn-with-ret-checks
                          clojure.core/subvec "clojure.core/subvec"
                          extra-checks)
                  vec    (wrap-fn-with-ret-checks
                          clojure.core/vec "clojure.core/vec"
                          extra-checks)]
      (apply puzzle-b args))))

(defn puzzle-b-rrbv-plus-checks [& args]
  (let [extra-checks vector-ret-checks1]
    (with-redefs [catvec (wrap-fn-with-ret-checks
                          fv/catvec "clojure.core.rrb-vector/catvec"
                          extra-checks)
                  subvec (wrap-fn-with-ret-checks
                          fv/subvec "clojure.core.rrb-vector/subvec"
                          extra-checks)
                  vec    (wrap-fn-with-ret-checks
                          fv/vec "clojure.core.rrb-vector/vec"
                          extra-checks)]
      (apply puzzle-b args))))

;;(puzzle-b-rrbv-plus-checks 977)
;;(puzzle-b-rrbv-plus-checks 978)

;;(def x (mapv (fn [i]
;;               (let [ret (puzzle-b-rrbv-plus-checks i)]
;;                 {:i i :ret ret :good? (every? integer? ret)}))
;;             (range 1 700)))

;;(every? :good? x)

(deftest crrbv-14-tests
  ;; This one passes
  (is (= (puzzle-b-core-plus-checks 977)
         (puzzle-b-rrbv-plus-checks 977)))
  ;; (puzzle-b-rrbv-plus-checks 978) throws
  ;; ArrayIndexOutOfBoundsException
  (if expect-failures
    (is (thrown-with-msg? ArrayIndexOutOfBoundsException
                          #"^(33|Index 33 out of bounds for length 33)$"
                          (every? integer? (puzzle-b-rrbv-plus-checks 978))))
    (is (every? integer? (puzzle-b-rrbv-plus-checks 978)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This code was copied from the issue:
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-13

(defn assoc-in-bytevec [use-transient? n indices]
  (let [coll (into (vector-of :byte) (range n))
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

(defn assoc-in-bytevec-core-plus-checks [& args]
  (let [extra-checks vector-ret-checks1]
    (with-redefs [vector-of (wrap-fn-with-ret-checks
                             clojure.core/vector-of
                             "clojure.core/vector-of"
                             extra-checks)]
      (apply assoc-in-bytevec args))))

(defn assoc-in-bytevec-rrbv-plus-checks [& args]
  (let [extra-checks vector-ret-checks1]
    (with-redefs [vector-of (wrap-fn-with-ret-checks
                             clojure.core.rrb-vector/vector-of
                             "clojure.core.rrb-vector/vector-of"
                             extra-checks)]
      (apply assoc-in-bytevec args))))

(deftest crrbv-13-tests
  ;; Some cases work, probably the ones where the tail is being
  ;; updated.
  (doseq [use-transient? [false true]]
    (doseq [args [[10 [5]]
                  [32 [0]]
                  [32 [32]]
                  [64 [32]]
                  [64 [64]]]]
      (is (= (apply assoc-in-bytevec-core-plus-checks false args)
             (apply assoc-in-bytevec-rrbv-plus-checks use-transient? args))
          (str "args=" (cons use-transient? args))))
    (doseq [args [[64 [0]]
                  [64 [1]]
                  [64 [31]]]]
      (if expect-failures
        (is (thrown-with-msg?
             ClassCastException
             #"\[B cannot be cast to( class)? \[Ljava\.lang\.Object;"
             (= (apply assoc-in-bytevec-core-plus-checks false args)
                (apply assoc-in-bytevec-rrbv-plus-checks use-transient? args)))
            (str "args=" (cons use-transient? args)))
        (is (= (apply assoc-in-bytevec-core-plus-checks false args)
               (apply assoc-in-bytevec-rrbv-plus-checks use-transient? args))
            (str "args=" (cons use-transient? args)))))))
