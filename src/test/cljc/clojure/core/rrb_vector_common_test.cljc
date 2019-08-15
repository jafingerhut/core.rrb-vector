(ns clojure.core.rrb-vector-common-test
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.core.rrb-test-infra
             :refer [full-debug-opts set-debug-opts! ex-message-copy
                     ex-cause-copy]]
            #?@(:clj ([clojure.java.io :as io]))
            [clojure.edn :as edn]
            [clojure.core.rrb-vector :as fv]
            [clojure.core.rrb-vector.rrbt :as rrbt]
            [clojure.core.rrb-vector.debug :as dv]
            [clojure.core.rrb-vector.debug-platform-dependent :as dpd]
            [clojure.core.reducers :as r])
  #?@(:clj ((:import (clojure.lang ExceptionInfo)))))


;; This is only intended to work on Node.js
#?(:cljs
   (do
     (def cwd (.cwd js/process))
     (def fs (js/require "fs"))))

(set-debug-opts! full-debug-opts)

(deftest test-slicing
  (testing "slicing"
    (is (dv/check-subvec 32000 10 29999 1234 18048 10123 10191))))

;; Moved deftest test-slicing-generative and test-splicing-generative
;; to a separate namespace, to more easily control when they are run
;; vs. when they are not.  They can take significantly longer than the
;; other tests in this namespace.

(deftest test-splicing
  (testing "splicing"
    (is (dv/check-catvec 1025 1025 3245 1025 32768 1025 1025 10123 1025 1025))
    (is (dv/check-catvec 10 40 40 40 40 40 40 40 40))
    (is (apply dv/check-catvec (repeat 30 33)))
    (is (dv/check-catvec 26091 31388 1098 43443 46195 4484 48099 7905
                         13615 601 13878 250 10611 9271 53170))

    ;; Order that catvec will perform splicev calls:
    (let [counts [26091 31388 1098 43443 46195 4484 48099 7905
                  13615 601 13878 250 10611 9271 53170]

          prefix-sums (reductions + counts)
          ranges (map range (cons 0 prefix-sums) prefix-sums)

          [v01 v02 v03 v04 v05 v06 v07 v08
           v09 v10 v11 v12 v13 v14 v15] (map fv/vec ranges)

          v01-02 (dv/dbg-splicev v01 v02)  ;; top level catvec call
          v03-04 (dv/dbg-splicev v03 v04)  ;; top level catvec call
          v01-04 (dv/dbg-splicev v01-02 v03-04)  ;; top level catvec call

          v05-06 (dv/dbg-splicev v05 v06)  ;; recurse level 1 catvec call
          v07-08 (dv/dbg-splicev v07 v08)  ;; recurse level 1 catvec call
          v05-08 (dv/dbg-splicev v05-06 v07-08)  ;; recurse level 1 catvec call

          v09-10 (dv/dbg-splicev v09 v10)  ;; recurse level 2 catvec call
          v11-12 (dv/dbg-splicev v11 v12)  ;; recurse level 2 catvec call
          v09-12 (dv/dbg-splicev v09-10 v11-12)  ;; recurse level 2 catvec call

          v13-14 (dv/dbg-splicev v13 v14)  ;; recurse level 3 catvec call
          v13-15 (dv/dbg-splicev v13-14 v15)  ;; recurse level 3 catvec call

          v09-15 (dv/dbg-splicev v09-12 v13-15)  ;; recurse level 2 catvec call

          v05-15 (dv/dbg-splicev v05-08 v09-15)  ;; recurse level 1 catvec call

          v01-15 (dv/dbg-splicev v01-04 v05-15)  ;; top level catvec call

          exp-val (range (last prefix-sums))]
      (is (= -1 (dv/first-diff v01-15 exp-val)))
      (is (= -1 (dv/first-diff (into v01-04 v05-15) exp-val))))))

(deftest test-reduce
  (let [v1 (vec (range 128))
        v2 (fv/vec (range 128))]
    (testing "reduce"
      (is (= (reduce + v1) (reduce + v2))))
    (testing "reduce-kv"
      (is (= (reduce-kv + 0 v1) (reduce-kv + 0 v2))))))

(deftest test-reduce-2
  (let [v1 (dv/dbg-subvec (vec (range 1003)) 500)
        v2 (vec (range 500 1003))]
    (is (= (reduce + 0 v1)
           (reduce + 0 v2)
           (reduce + 0 (r/map identity (seq v1)))
           (reduce + 0 (r/map identity (seq v2)))))))

(deftest test-seq
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
  (is (= (into (dv/dbg-catvec (vec (range 123)) (vec (range 68))) (range 64))
         (concat (range 123) (range 68) (range 64))))
  (is (= (dv/slow-into (fv/catvec (vec (range 123)) (vec (range 68)))
                       (range 64))
         (concat (range 123) (range 68) (range 64)))))

(deftest test-hasheq
  (is (= (hash []) (fv/vector)))
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
  (letfn [(insert-by-sub-catvec [v n]
            (dv/dbg-catvec (dv/dbg-subvec v 0 n) (fv/vec ['x])
                           (dv/dbg-subvec v n)))
          (repeated-subvec-catvec [i]
            (reduce insert-by-sub-catvec (vec (range i)) (range i 0 -1)))]
    (is (= (repeated-subvec-catvec 2371)
           (interleave (range 2371) (repeat 'x))))))

(deftest test-splice-high-subtree-branch-count
  (let [x        (fv/vec (repeat 1145 \a))
        y        (dv/dbg-catvec (dv/dbg-subvec x 0 778) (dv/dbg-subvec x 778 779) [1] (dv/dbg-subvec x 779))
        z        (dv/dbg-catvec (dv/dbg-subvec y 0 780) [2] (dv/dbg-subvec y 780 781) (dv/dbg-subvec y 781))
        res      (dv/dbg-catvec (dv/dbg-subvec z 0 780) [] [3] (dv/dbg-subvec z 781))
        expected (concat (repeat 779 \a) [1] [3] (repeat 366 \a))]
    (is (= res expected))))


;; This problem reproduction code is from CRRBV-12 ticket:
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-12

(defn quicksort [v]
  (if (<= (count v) 1)
    v
    (let [[x & xs] v]
      (dv/dbg-catvec (quicksort (filterv #(<= % x) xs))
                     [x]
                     (quicksort (filterv #(> % x) xs))))))

(defn ascending? [coll]
  (every? (fn [[a b]] (<= a b))
          (partition 2 1 coll)))

(deftest test-crrbv-12
  (let [base-fname "crrbv-12-data.edn"
        fname #?(:clj (str "clojure/core/" base-fname)
                 :cljs (str cwd "/src/test/cljc/clojure/core/" base-fname))
        _ (println "fname=" fname)
        file-content-str #?(:clj (slurp (io/resource fname))
                            :cljs (.readFileSync fs fname "utf8"))
        v (edn/read-string file-content-str)]
    (testing "Ascending order after quicksort"
      (is (ascending? (quicksort v)))))
  (testing "Repeated catvec followed by pop"
      (is (= [] (nth (iterate pop
                              (nth (iterate #(dv/dbg-catvec [0] %) [])
                                   963))
                     963)))))



(defn npe-for-1025-then-pop! [kind]
  (let [bfactor-squared (* 32 32)
        mk-vector (case kind
                    :object-array fv/vector
                    #?@(:clj (:long-array #(fv/vector-of :long))))
        boundary 54
        v1 (-> (mk-vector)
               (into (range boundary))
               (into (range boundary (inc bfactor-squared))))
        v2 (-> (mk-vector)
               (into (range bfactor-squared))
               (transient)
               (dv/dbg-pop!)
               (persistent!))
        v3 (-> (mk-vector)
               (into (range boundary))
               (into (range boundary (inc bfactor-squared)))
               (transient)
               (dv/dbg-pop!)
               (persistent!))
        v4 (-> (mk-vector)
               (into (range (inc bfactor-squared)))
               (transient)
               (dv/dbg-pop!)
               (persistent!))]
    ;; This test passes
    (is (= (seq v1) (range (inc bfactor-squared))))
    ;; This also passes
    (is (= (seq v2) (range (dec bfactor-squared))))
    ;; This fails with NullPointerException while traversing the seq
    ;; on clj.  It gets a different kind of error with cljs.
    (is (= (seq v3) (range bfactor-squared)))
    ;; This one causes a NullPointerException while traversing the seq
    (is (= (seq v4) (range bfactor-squared)))))

(deftest test-npe-for-1025-then-pop!
  (doseq [kind #?(:clj [:object-array :long-array]
                  :cljs [:object-array])]
    (npe-for-1025-then-pop! kind)))


;; This problem reproduction code is from a comment by Mike Fikes on
;; 2018-Dec-09 for this issue:
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-20

(defn play [my-vector my-catvec my-subvec players rounds]
  (letfn [(swap [marbles split-ndx]
            (my-catvec
             (my-subvec marbles split-ndx)
             (my-subvec marbles 0 split-ndx)))
          (rotl [marbles n]
            (swap marbles (mod n (count marbles))))
          (rotr [marbles n]
            (swap marbles (mod (- (count marbles) n) (count marbles))))
          (place-marble
            [marbles marble]
            (let [marbles (rotl marbles 2)]
              [(my-catvec (my-vector marble) marbles) 0]))
          (remove-marble [marbles marble]
            (let [marbles (rotr marbles 7)
                  first-marble (nth marbles 0)]
              [(my-subvec marbles 1) (+ marble first-marble)]))
          (play-round [marbles round]
            (if (zero? (mod round 23))
              (remove-marble marbles round)
              (place-marble marbles round)))
          (add-score [scores player round-score]
            (if (zero? round-score)
              scores
              (assoc scores player (+ (get scores player 0) round-score))))]
    (loop [marbles (my-vector 0)
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
                 (conj ret {:round round :marbles marbles})))))))

(defn play-core [& args]
  (apply play clojure.core/vector clojure.core/into clojure.core/subvec args))

(defn play-rrbv [& args]
  (apply play fv/vector dv/dbg-catvec dv/dbg-subvec args))

(deftest test-crrbv-20
  ;; This one passes
  (is (= (play-core 10 1128)
         (play-rrbv 10 1128)))
  ;; This ends up with (play-rrbv 10 1129) throwing an exception
  (is (= (play-core 10 1129)
         (play-rrbv 10 1129)))

  ;; The previous test demonstrates a bug in the transient RRB vector
  ;; implementation.  The one below demonstrates a similar bug in the
  ;; persistent RRB vector implementation.
  (let [v1128 (:marbles (last (play-rrbv 10 1128)))
        v1129-pre (-> v1128
                      (fv/subvec 2)
                      (conj 2001))]
    (is (every? integer? (conj v1129-pre 2002)))))

(deftest test-crrbv-21
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
  (let [v1128 (:marbles (last (play-rrbv 10 1128)))
        vpop1 (reduce (fn [v i] (pop v))
                      v1128 (range 1026))]
    (is (every? integer? (pop vpop1)))
    ;; The transient version below gives a similar exception, but the
    ;; call stack goes through the transient version of popTail,
    ;; rather than the persistent version of popTail that the one
    ;; above does.  It seems likely that both versions of popTail have
    ;; a similar bug.
    (is (every? integer? (persistent! (pop! (transient vpop1)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This code was copied from
;; https://github.com/mattiasw2/adventofcode1/blob/master/src/adventofcode1/nineteen_b.clj

;; mentioned in issue
;; https://clojure.atlassian.net/projects/CRRBV/issues/CRRBV-14

(defn puzzle-b [n my-vec my-catvec my-subvec]
  (letfn [(remove-at [arr idx]
            (my-catvec (my-subvec arr 0 idx) (my-subvec arr (inc idx))))
          (create-arr [size]
            (my-vec (range 1 (inc size))))
          (fv-rest [arr]
            (my-subvec arr 1))
          (calculate-opposite [n]
            (int (/ n 2)))
          (move [elfs]
            (let [lc (count elfs)]
              (if (= 1 lc)
                {:ok (first elfs)}
                (let [current      (first elfs)
                      opposite-pos (calculate-opposite lc)
                      _ (assert (> opposite-pos 0))
                      _ (assert (< opposite-pos lc))
                      opposite-elf (nth elfs opposite-pos)
                      other2       (fv-rest (remove-at elfs opposite-pos))]
                  (my-catvec other2 [current])))))
          (puzzle-b-sample [elfs round]
            (let [elfs2 (move elfs)]
              ;;(println "round=" round "# elfs=" (count elfs))
              (if (:ok elfs2)
                (:ok elfs2)
                (recur elfs2 (inc round)))))]
    (puzzle-b-sample (create-arr n) 1)))

(defn puzzle-b-core [n]
  (puzzle-b n clojure.core/vec clojure.core/into clojure.core/subvec))

(defn vstats [v]
  (str "cnt=" (count v)
       " shift=" (.-shift v)
       " %=" (dpd/format "%5.1f" (* 100.0 (dv/fraction-full v)))))

(def custom-catvec-data (atom []))

(defn custom-catvec [& args]
  (doall (map-indexed
          (fn [idx v]
            (println (str "custom-catvec ENTER v" idx "  " (vstats v))))
          args))
  (let [n (count @custom-catvec-data)
        ret (apply dv/dbg-catvec args)]
    (println (str "custom-catvec LEAVE ret " (vstats ret)))
    ;;(swap! custom-catvec-data conj {:args args :ret ret})
    ;;(println "custom-catvec RECRD in index" n "of @custom-catvec-data")
    ret))

(defn puzzle-b-rrbv [n]
  (puzzle-b n fv/vec dv/dbg-catvec dv/dbg-subvec))

;;(puzzle-b-rrbv 977)
;;(puzzle-b-rrbv 978)
;;(count @extra-check-failures)
;;(def a1 (nth @extra-check-failures 0))
;;(use 'clojure.pprint)
;;(pprint a1)

;;(def x (mapv (fn [i]
;;               (let [ret (puzzle-b-rrbv i)]
;;                 {:i i :ret ret :good? (every? integer? ret)}))
;;             (range 1 700)))

;;(every? :good? x)

(deftest test-crrbv-14
  ;; This one passes
  (is (= (puzzle-b-core 977)
         (puzzle-b-rrbv 977)))
  ;; (puzzle-b-rrbv 978) throws
  ;; ArrayIndexOutOfBoundsException
  (is (integer? (puzzle-b-rrbv 978))))


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
