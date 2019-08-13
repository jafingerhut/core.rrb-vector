(ns clojure.core.rrb-vector.debug
  (:require [clojure.core.rrb-vector.rrbt
             :refer [#?(:clj as-rrbt :cljs -as-rrbt)]]
            [clojure.core.rrb-vector :as fv]
            ;; This page:
            ;; https://clojure.org/guides/reader_conditionals refers
            ;; to code that can go into common cljc files as platform
            ;; independent, and the code in the clj or cljs files as
            ;; platform dependent, so I will use that terminology
            ;; here, too.
            [clojure.core.rrb-vector.debug-platform-dependent :as pd]))


;; Functions expected to be defined in the appropriate
;; clojure.core.rrb-vector.debug-platform-dependent namespace:

;; pd/internal-node?
;; pd/persistent-vector?
;; pd/transient-vector?
;; pd/is-vector?
;; pd/dbg-tailoff  (formerly debug-tailoff)
;; pd/dbg-tidx (formerly debug-tailoff for clj, debug-tidx for cljs)
;; pd/format
;; pd/printf
;; pd/unwrap-subvec-accessors-for
;; pd/abbrev-for-type-of [vec-or-node]   (formerly abbrev-type-name, but move type/class call inside)
;; pd/same-coll?   (written already for clj, TBD for cljs)

;; Functions returned from unwrap-subvec-accessors-for that have
;; platform-dependent definitions, but the same general 'kind'
;; arguments and return values, where 'kind' could be: any vector,
;; persistent or transient, or a vector tree node object:

;; get-root - All get-* fns formerly called extract-* in the Java
;;     platform dependent version of the debug namespace.
;; get-shift
;; get-tail
;; get-cnt
;; get-array [node]   - clj (.array nm node)   cljs (.-arr node)
;; get-ranges [node]  - clj (ranges nm node)   cljs (node-ranges node)
;; regular? [node]    - clj (.regular nm node) cljs (regular? node)
;; tail-len [tail]    - clj (.alength am tail) cljs (alength tail)

;; NO: nm am - cljs doesn't need them, and clj only uses them for the
;; last few functions above.

(defn dbg-vec [v]
  (let [{:keys [v subvector? subvec-start subvec-end get-root get-shift
                get-tail get-cnt get-array get-ranges regular? tail-len]}
        (pd/unwrap-subvec-accessors-for v)
        root  (get-root v)
        shift (get-shift v)
        tail  (get-tail v)
        cnt   (get-cnt v)]
    (when subvector?
      (pd/printf "SubVector from start %d to end %d of vector:\n"
                 subvec-start subvec-end))
    (letfn [(go [indent shift i node]
              (when node
                (dotimes [_ indent]
                  (print "  "))
                (pd/printf "%02d:%02d %s" shift i (pd/abbrev-for-type-of node))
                (if-not (or (zero? shift) (regular? node))
                  (print ":" (seq (get-ranges node))))
                (if (zero? shift)
                  (print ":" (vec (get-array node))))
                (println)
                (if-not (zero? shift)
                  (dorun
                   (map-indexed (partial go (inc indent) (- shift 5))
                                (let [arr (get-array node)]
                                  (if (regular? node)
                                    arr
                                    (butlast arr))))))))]
      (pd/printf "%s (%d elements):\n" (pd/abbrev-for-type-of v) (count v))
      (go 0 shift 0 root)
      (println (if (pd/transient-vector? v)
                 (pd/format "tail (tidx %d):" (pd/dbg-tidx v))
                 "tail:")
               (vec tail)))))

(defn first-diff [xs ys]
  (loop [i 0 xs (seq xs) ys (seq ys)]
    (if (try (and xs ys (= (first xs) (first ys)))
             (catch #?(:clj Exception :cljs js/Error) e
               (.printStackTrace e)
               i))
      (let [xs (try (next xs)
                    (catch #?(:clj Exception :cljs js/Error) e
                      (prn :xs i)
                      (throw e)))
            ys (try (next ys)
                    (catch #?(:clj Exception :cljs js/Error) e
                      (prn :ys i)
                      (throw e)))]
        (recur (inc i) xs ys))
      (if (or xs ys)
        i
        -1))))

(defn slow-into [to from]
  (reduce conj to from))

(defn all-vector-tree-nodes [v]
  (let [{:keys [v get-root get-shift get-array regular?]}
        (pd/unwrap-subvec-accessors-for v)
        root  (get-root v)
        shift (get-shift v)]
    (letfn [(go [depth shift node]
              (if node
                (if (not= shift 0)
                  (cons
                   {:depth depth :shift shift :kind :internal :node node}
                   (apply concat
                          (map (partial go (inc depth) (- shift 5))
                               (let [arr (get-array node)]
                                 (if (regular? node)
                                   arr
                                   (butlast arr))))))
                  (cons {:depth depth :shift shift :kind :internal :node node}
                        (map (fn [x]
                               {:depth (inc depth) :kind :leaf :value x})
                             (get-array node))))))]
      (cons {:depth 0 :kind :base :shift shift :value v}
            (go 1 shift root)))))

;; All nodes that should be internal nodes are one of the internal
;; node types satisfying internal-node?  All nodes that are less
;; than "leaf depth" must be internal nodes, and none of the ones
;; at "leaf depth" should be.  Probably the most general restriction
;; checking for leaf values should be simply that they are any type
;; that is _not_ an internal node type.  They could be objects that
;; return true for is-vector? for example, if a vector is an element
;; of another vector.

(defn leaves-with-internal-node-type [node-infos]
  (filter (fn [node-info]
            (and (= :leaf (:kind node-info))
                 (pd/internal-node? (:node node-info))))
          node-infos))

(defn non-leaves-not-internal-node-type [node-infos]
  (filter (fn [node-info]
            (and (= :internal (:kind node-info))
                 (not (pd/internal-node? (:node node-info)))))
          node-infos))

;; TBD: The definition of nth in deftype Vector seems to imply that
;; every descendant of a 'regular' node must also be regular.  That
;; would be a straightforward sanity check to make, to return an error
;; if a non-regular node is found with a regular ancestor in the tree.

(defn basic-node-errors [v]
  (let [{:keys [v get-shift]} (pd/unwrap-subvec-accessors-for v)
        shift (get-shift v)
        nodes (all-vector-tree-nodes v)
        by-kind (group-by :kind nodes)
        leaf-depths (set (map :depth (:leaf by-kind)))
        expected-leaf-depth (+ (quot shift 5) 2)
        max-internal-node-depth (->> (:internal by-kind)
                                     (map :depth)
                                     (apply max))
        ;; Be a little loose in checking here.  If we want to narrow
        ;; it down to one expected answer, we would need to look at
        ;; the tail to see how many elements it has, then use the
        ;; different between (count v) and that to determine how many
        ;; nodes are in the rest of the tree, whether it is 0 or
        ;; non-0.
        expected-internal-max-depths
        (cond
          (= (count v) 0) #{(- expected-leaf-depth 2)}
          (> (count v) 33) #{(dec expected-leaf-depth)}
          :else #{(dec expected-leaf-depth)
                  (- expected-leaf-depth 2)})]
    (cond
      (not= (mod shift 5) 0)
      {:error true
       :description (str "shift value in root must be a multiple of 5.  Found "
                         shift)
       :data shift}

      ;; It is OK for this set size to be 0 if no leaves, but if there
      ;; are leaves, they should all be at the same depth.
      (> (count leaf-depths) 1)
      {:error true
       :description (str "There are leaf nodes at multiple different depths: "
                         leaf-depths)
       :data leaf-depths}

      (and (= (count leaf-depths) 1)
           (not= (first leaf-depths) expected-leaf-depth))
      {:error true
       :description (str "Expecting all leaves to be at depth " expected-leaf-depth
                         " because root has shift=" shift
                         " but found leaves at depth " (first leaf-depths))
       :data leaf-depths}

      (not (contains? expected-internal-max-depths max-internal-node-depth))
      {:error true
       :description (str "Expecting there to be some internal nodes at one of"
                         " these depths: "
                         expected-internal-max-depths
                         " because count=" (count v)
                         " and root has shift=" shift
                         " but max depth among all internal nodes found was "
                         max-internal-node-depth)}

      (seq (leaves-with-internal-node-type nodes))
      {:error true
       :description "A leaf (at max depth) has one of the internal node types, returning true for internal-node?"
       :data (first (leaves-with-internal-node-type nodes))}

      (seq (non-leaves-not-internal-node-type nodes))
      {:error true
       :description "A non-leaf node has a type that returns false for internal-node?"
       :data (first (non-leaves-not-internal-node-type nodes))}

      :else
      {:error false})))

;; I believe that objects-in-slot-32-of-obj-arrays and
;; ranges-not-int-array are only called directly from one test
;; namespace right now.  Consider making a combined invariant checking
;; function in this debug namespace that can be used from any test
;; namespace (or other debug-time code) that a developer wants to.

(defn objects-in-slot-32-of-obj-arrays
  "Function to look for errors of the form where a node's node.array
  object, which is often an array of 32 or 33 java.lang.Object's, has
  an element at index 32 that is not nil, and refers to an object that
  is of any type _except_ an array of ints.  There appears to be some
  situation in which this can occur, but it seems to almost certainly
  be a bug if that happens, and we should be able to detect it
  whenever it occurs."
  [v]
  (let [{:keys [v get-array]} (pd/unwrap-subvec-accessors-for v)
        node-maps (all-vector-tree-nodes v)
        internal (filter #(= :internal (:kind %)) node-maps)]
    (keep (fn [node-info]
            ;; TBD: Is there a way to do ^objects type hint for clj,
            ;; but none for cljs?  Is it harmful for cljs to have such
            ;; a type hint?
            ;;(let [^objects arr (get-array (:node node-info))
            (let [arr (get-array (:node node-info))
                  n (count arr)]
              (if (== n 33)
                (aget arr 32))))
          internal)))

;; TBD: Should this function be defined in platform-specific file?
;;(defn ranges-not-int-array [x]
;;  (seq (remove int-array? (objects-in-slot-32-of-obj-arrays x))))


;; edit-nodes-errors is completely defined in platform-specific source
;; files.  It is simply quite different between clj/cljs.
(defn edit-nodes-errors [v]
  (pd/edit-nodes-errors v all-vector-tree-nodes))


(defn regular-node-errors [root-node? root-node-cnt children]
  ;; For regular nodes, there should be zero or more 'full' children,
  ;; followed optionally by one 'partial' child, followed by nils.
  (let [[full-children others] (split-with :full? children)
        [partial-children others] (split-with #(and (not (:full %))
                                                    (not= :nil (:kind %)))
                                              others)
        [nil-children others] (split-with #(= :nil (:kind %)) others)
        num-full (count full-children)
        num-partial (count partial-children)
        num-non-nil (+ num-full num-partial)]
    (cond
      (not= 0 (count others))
      {:error true, :kind :internal,
       :description (str "Found internal regular node with "
                         num-full " full, " num-partial " partial, "
                         (count nil-children) " nil, "
                         (count others) " 'other' children."
                         " - expected 0 children after nils.")}
      (> num-partial 1)
      {:error true, :kind :internal,
       :description (str "Found internal regular node with "
                         num-full " full, " num-partial " partial, "
                         (count nil-children) " nil children"
                         " - expected 0 or 1 partial.")}
      (not (or (and root-node?
                    (<= root-node-cnt 32)  ;; all elements in tail
                    (= 0 num-non-nil))
               (<= 1 num-non-nil 32)))
      {:error true, :kind :internal
       :description
       (str "Found internal regular node with # full + # partial=" num-non-nil
            " children outside of range [1, 32]."
            " root-node?=" root-node? " root-node-cnt=" root-node-cnt)
       :data children}
      :else
      {:error false, :kind :internal,
       :full? (= 32 (count full-children))
       :count (reduce + (map #(or (:count %) 0) children))})))


(defn non-regular-node-errors [node get-ranges children]
  (let [rng (get-ranges node)
        [non-nil-children others] (split-with #(not= :nil (:kind %)) children)
        [nil-children others] (split-with #(= :nil (:kind %)) others)
        num-non-nil (count non-nil-children)
        num-nil (count nil-children)
        expected-ranges (reductions + (map :count non-nil-children))]
    (cond
      (not= 0 (count others))
      {:error true, :kind :internal,
       :description (str "Found internal non-regular node with "
                         num-non-nil " non-nil, " num-nil " nil, "
                         (count others) " 'other' children."
                         " - expected 0 children after nils.")}
      (not= num-non-nil (aget rng 32))
      {:error true, :kind :internal,
       :description (str "Found internal non-regular node with "
                         num-non-nil " non-nil, " num-nil " nil children, and"
                         " last elem of ranges=" (aget rng 32)
                         " - expected it to match # non-nil children.")}
      (not= expected-ranges (take (count expected-ranges) (seq rng)))
      {:error true, :kind :internal,
       :description (str "Found internal non-regular node with "
                         num-non-nil " non-nil, " num-nil " nil children, and"
                         " # children prefix sums: " (seq expected-ranges)
                         " - expected that to match stored ranges: "
                         (seq rng))}
      ;; I believe that there must always be at least one
      ;; non-nil-child.  By checking for this condition, we will
      ;; definitely find out if it is ever violated.
      ;; TBD: What if we have a tree with ranges, and then remove all
      ;; elements?  Does the resulting tree triger this error?
      (not (<= 1 (aget rng 32) 32))
      {:error true, :kind :internal
       :description (str "Found internal non-regular node with (aget rng 32)"
                         "=" (aget rng 32) " outside of range [1, 32].")}
      :else
      {:error false, :kind :internal, :full? false,
       :count (last expected-ranges)})))


(defn max-capacity-over-1024 [root-shift]
  (let [shift-amount (max 0 (- root-shift 5))]
    (bit-shift-left 1 shift-amount)))


(defn fraction-full [v]
  (let [{:keys [v get-shift]} (pd/unwrap-subvec-accessors-for v)
        root-shift (get-shift v)
        tail-off (pd/dbg-tailoff v)
        max-tree-cap (bit-shift-left 1 (+ root-shift 5))]
    (/ (* 1.0 tail-off) max-tree-cap)))


(defn ranges-errors [v]
  (let [{:keys [v get-root get-shift get-tail get-cnt get-array get-ranges
                regular? tail-len]}
        (pd/unwrap-subvec-accessors-for v)
        root  (get-root v)
        root-node-cnt (count v)
        root-shift (get-shift v)
        tail-off (pd/dbg-tailoff v)
        tail (get-tail v)]
    (letfn [
      (go [shift node]
        (cond
          (nil? node) {:error false :kind :nil}
          (zero? shift) (let [n (count (get-array node))]
                          (merge {:error (zero? n), :kind :leaves,
                                  :full? (= n 32), :count n}
                                 (if (zero? n)
                                   {:description
                                    (str "Leaf array has 0 elements."
                                         "  Expected > 0.")})))
          :else ;; non-0 shift
          (let [children (map (partial go (- shift 5))
                              (let [arr (get-array node)]
                                (if (regular? node)
                                  arr
                                  (butlast arr))))
                errs (filter :error children)]
            (cond
              (seq errs) {:error true, :description "One or more errors found",
                          :data errs}
              (not= 32 (count children))
              {:error true, :kind :internal,
               :description (str "Found internal node that has "
                                 (count children) " children - expected 32.")}
              (regular? node) (regular-node-errors (= shift root-shift)
                                                   root-node-cnt children)
              :else (non-regular-node-errors node get-ranges children)))))]
      (let [x (go root-shift root)]
        (cond
          (:error x) x
          (not= tail-off (:count x))
          {:error true, :kind :root,
           :description (str "Found tail-off=" tail-off " != " (:count x)
                             "=count of values beneath internal nodes")
           :internal-node-leaf-count (:count x) :tail-off tail-off
           :cnt (get-cnt v)}
          (and (pd/transient-vector? v)
               (not= (tail-len tail) 32))
          {:error true, :kind :root,
           :description (str "Found transient vector with tail length "
                             (tail-len tail) " - expecting 32")}
          ;; It is always a bad thing if shift becomes more than 32,
          ;; because the bit-shift-left and bit-shift-right operations
          ;; on 32-bit ints actually behave like (bit-shift-left
          ;; x (mod shift-amount 32)) for shift-amount over 32.  It is
          ;; also likely a bug in the implementation if that happens.
          (>= root-shift 32)
          {:error true, :kind :root,
           :description (str "shift of root is " root-shift " >= 32,"
                             " which is not supported.")}
          ;; This is not necessarily a bug, but it seems likely to be
          ;; a bug if a tree is less than 1/1024 full compared to its
          ;; max capacity.  1/32 full is normal when a tree becomes 1
          ;; deeper than it was before.
          (< 0 (:count x) (max-capacity-over-1024 root-shift))
          {:error true, :kind :root,
           :description (str "For root shift=" root-shift " the maximum "
                             "capacity divided by 1024 is "
                             (max-capacity-over-1024 root-shift)
                             " but the tree contains only "
                             (:count x) " vector elements outside of the tail")}
          :else x)))))

(defn add-return-value-checks [f err-desc-str return-value-check-fn]
  (fn [& args]
    (let [ret (apply f args)]
      (apply return-value-check-fn err-desc-str ret args)
      ret)))

(defn copying-seq [v]
  (let [{:keys [v subvector? subvec-start subvec-end
                get-root get-shift get-tail get-array regular?]}
        (pd/unwrap-subvec-accessors-for v)
        root  (get-root v)
        shift (get-shift v)]
    (letfn [(go [shift node]
              (if node
                (if (not= shift 0)
                  (apply concat
                         (map (partial go (- shift 5))
                              (let [arr (get-array node)]
                                (if (regular? node)
                                  arr
                                  (butlast arr)))))
                  (seq (get-array node)))))]
      (doall  ;; always return a fully realized sequence.
       (let [all-elems (concat (go shift root)
                               (if (pd/transient-vector? v)
                                 (take (pd/dbg-tidx v) (get-tail v))
                                 (seq (get-tail v))))]
         (if subvector?
           (take (- subvec-end subvec-start) (drop subvec-start all-elems))
           all-elems))))))


;; functions to check:

;; conj - clj arities [] [coll] [coll x] [coll x & xs]
;; conj! - clj arities [] [coll] [coll x]
;; pop - clj arities [coll]
;; pop! - clj arities [coll]
;; assoc - clj arities [coll key val] [coll key val & kvs]
;; assoc! - clj arities [coll key val] [coll key val & kvs]
;; nth - persistent or transient
;;     - clj arities [coll index] [coll index not-found]
;; N/A dissoc! - clj not supported on vectors

;; note that if we wrap conj! transient persistent! and conj, but not
;; into, then into will in clj be direct-linked to unwrapped version
;; of those other functions, and will thus _not_ be checked.

;; peek - persistent only.  clj arities [coll]
;; 
;; catvec (persistent only) - clj arities [] [v1] [v1 v2] [v1 v2 & vn]
;; vector (persistent only) - clj arities [] [a] [a b] [a b & args] (same for fv/vector)
;; vec (persistent only) - clj arities [coll] (same for fv/vec)
;; vector-of -- persistent only, clj only, for primitive vectors  - same arities as vector, except first arg required and must be type

;; seq - persistent only clj arities [coll]
;; rseq - persistent only? clj arities [coll]
;; subvec - clj arities [v start] [v start end]  (uses slicev in rrbt)

;; It seems like a good idea to use the same data to describe this as
;; used by collection-check:

;; [:transient]
;; [:persistent!]
;; [:assoc idx val]
;; [:assoc! idx val]
;; [:pop]
;; [:pop!]
;; [:conj val]
;; [:conj! val]

;; [:seq] - vector->seq
;; [:rest] - only after [:seq] I believe, from collection-check at least
;; [into] - seq->vector


(def failure-data (atom []))

(defn clear-failure-data! []
  (reset! failure-data []))

(let [orig-conj clojure.core/conj]
  (defn record-failure-data [d]
    (swap! failure-data orig-conj d)))

(defn conj-err-check [call-desc-str args ret coll-seq ret-seq exp-ret-seq
                      err-desc-str]
  (when (not= ret-seq exp-ret-seq)
    (println (str "ERROR: " call-desc-str " returned incorrect value"))
    ;; TBD: error msg should include details of how the two
    ;; sequences are different, e.g. first-diff, lengths, how many
    ;; elements differ between the two, etc.
    (record-failure-data {:err-desc-str err-desc-str, :ret ret, :args args,
                          :coll-seq coll-seq, :ret-seq ret-seq,
                          :exp-ret-seq exp-ret-seq})))

(defn conj-validator [f err-desc-str]
  (fn validating-conj
    ([]
     (let [coll-seq nil
           exp-ret-seq (list)
           ret (f)
           ret-seq (copying-seq ret)]
       (conj-err-check "(conj)" (list)
                       ret coll-seq ret-seq exp-ret-seq err-desc-str)
       ret))
    ([coll]
     (println "validating-conj called with (type coll)=" (type coll) " no x")
     (let [coll-seq (copying-seq coll)
           exp-ret-seq coll-seq
           ret (f coll)
           ret-seq (copying-seq ret)]
       (conj-err-check "(conj coll)" (list coll)
                       ret coll-seq ret-seq exp-ret-seq err-desc-str)
       ret))
    ([coll x]
     (println "validating-conj called with (type coll)=" (type coll) "x=" x)
     (if-not (pd/is-vector? coll)
       (f coll x)
       (let [_ (println "validating-conj called with (type coll)=" (type coll) "x=" x)
             coll-seq (copying-seq coll)
             exp-ret-seq (concat coll-seq (list x))
             ret (f coll x)
             ret-seq (copying-seq ret)]
         (conj-err-check "(conj coll x)" (list coll x)
                         ret coll-seq ret-seq exp-ret-seq err-desc-str)
         ret)))
    ([coll x & xs]
     (println "validating-conj called with (type coll)=" (type coll) "x=" x
              "xs=" (seq xs))
     (if-not (pd/is-vector? coll)
       (apply f coll x xs)
       (let [_ (println "validating-conj called with (type coll)=" (type coll)
                        "x=" x)
             coll-seq (copying-seq coll)
             exp-ret-seq (concat coll-seq (cons x xs))
             ret (apply f coll x xs)
             ret-seq (copying-seq ret)]
         (conj-err-check "(conj coll x & xs)" (concat (list coll x) xs)
                         ret coll-seq ret-seq exp-ret-seq err-desc-str)
         ret)))))

(defn validating-conj!
  ([f err-desc-str]
   (let [coll-seq nil
         exp-ret-seq (list)
         ret (f)
         ret-seq (copying-seq ret)]
     (conj-err-check "(conj!)" (list)
                     ret coll-seq ret-seq exp-ret-seq err-desc-str)
     ret))
  ([f err-desc-str coll]
   (let [coll-seq (copying-seq coll)
         exp-ret-seq coll-seq
         ret (f coll)
         ret-seq (copying-seq ret)]
     (conj-err-check "(conj! coll)" (list coll)
                     ret coll-seq ret-seq exp-ret-seq err-desc-str)
     ret))
  ([f err-desc-str coll x]
   (if-not (pd/is-vector? coll)
     (f coll x)
     (let [_ (println "called validating-conj! with (type coll)=" (type coll)
                      "x=" x)
           coll-seq (copying-seq coll)
           exp-ret-seq (concat coll-seq (list x))
           ret (f coll x)
           ret-seq (copying-seq ret)]
       (conj-err-check "(conj! coll x)" (list coll x)
                       ret coll-seq ret-seq exp-ret-seq err-desc-str)
       ret))))

;; TBD: Keep this, or discard?
(defn vector-return-value-checks-print-and-record [err-desc-str ret & args]
  ;;(println "checking ret val from" err-desc-str)
  (let [i (edit-nodes-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args args, :edit-nodes-errors i})))
  ;; TBD: re-enable these sanity checks, after implementing them
  ;; similarly for both clj and cljs
;  (when-let [err (seq (ranges-not-int-array ret))]
;    (println "ERROR:" err-desc-str "ret has non int-array ranges")
;    (record-failure-data {:err-desc-str err-desc-str, :ret ret,
;                          :args args}))
  (let [i (basic-node-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args args, :basic-node-errors i})))
  (let [i (ranges-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args args, :ranges-errors i}))))

;; I would like to achieve a goal of providing an easy-to-use way that
;; a Clojure or ClojureScript developer could call a function, or
;; invoke their own code in a macro, and then within the run-time
;; scope of that, a selected set of calls to functions like conj,
;; conj!, pop, pop!, transient, subvec, slicev, catvec, splicev, and
;; perhaps others, would have extra checks enabled, such that if they
;; detected a bug, they would stop the execution immediately with a
;; lot of debug information recorded as near to the point of the
;; failure as can be achieved by checking the return values of such
;; function calls.

;; It would also be good if this goal could be achieved without having
;; a separate implementation of all of those functions, and/or custom
;; versions of Clojure, ClojureScript, or the core.rrb-vector library
;; to use.  Actually a separate implementation of core.rrb-vector
;; might be acceptable and reasonable to implement and maintain, but
;; separate versions of Clojure and ClojureScript seems like too much
;; effort for the benefits achieved.

;; I have investigated approaches that attempt to use with-redefs on
;; the 'original Vars' in Clojure, and also in a ClojureScript
;; Node-based REPL.

;; There are differences between with-redefs behavior on functions in
;; clojure.core between Clojure and ClojureScript, because
;; direct-linking seems to also include user code calling to
;; clojure.core functions with ClojureScript:
;; https://clojure.atlassian.net/projects/CLJS/issues/CLJS-3154

;; At least in Clojure, and perhaps also in ClojureScript, there is
;; sometimes an effect similar to direct linking involved when calling
;; protocol methods on objects defined via deftype.  That prevents
;; with-redefs, and any technique that changes the definition of a Var
;; with alter-var-root! or set!, from causing the alternate function
;; to be called.

;; Here are the code paths that I think are most useful for debug
;; checks of operations on vectors.

;; Functions in clojure.core:

;; Lower value, because they are simpler functions, and in particular
;; do not operate on RRB vector trees with ranges inside:
;; vec vector vector-of

;; Similarly the RRB vector variants of those functions create regular
;; RRB vectors, so not as likely to have bugs.

;; peek can operate on trees with ranges inside, but always accesses
;; the tail, so not nearly as likely to have bugs.

;; Higher value, because they can operate on RRB vectors with ranges
;; inside the tree:

;; conj pop assoc
;; conj! pop! assoc!
;; transient persistent!
;; seq rseq

;; Functions in clojure.core.rrb-vector namespace, and internal
;; implementation functions/protocol-methods that they use:

;; defn fv/catvec
;;   calls itself recursively for many args (clj and cljs versions)
;;   -splicev protocol function (splicev for clj)
;;     When -splicev is called on PersistentVector or Subvec, -as-rrbt
;;       converts it to Vector, then method below is called.
;;     deftype Vector -splicev / splicev method
;;       -as-rrbt (cljs) / as-rrbt (clj)
;;         -slicev (cljs) / slicev (clj) if used on a subvector object
;;       defn splice-rrbts
;;         Calls many internal implementation detail functions.

;; defn fv/subvec
;;   -slicev (cljs) / slicev (clj) protocol function
;;     deftype Vector -slicev method
;;       Calls many internal implementation detail functions,
;;       e.g. slice-left slice-right make-array array-copy etc.


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Supported keys of @debug-opts:

;; :catvec used by dbg-catvec
;; :splicev used by dbg-splicev
;; :slicev used by dbg-slicev

;; The value associated with each key is a submap that may have the
;; following keys.

;; :trace - logical true to enable some debug printing when dbg-*
;; function is called.

;; :validate - logical true to enable checking of a return value
;; against the expected return value, independently calculated via
;; operations on sequences.

;; :return-value-checks - a sequence of functions to perform
;; additional checks on the return value, e.g.

;; edit-nodes-error-checks
;; basic-node-error-checks
;; ranges-error-checks

;; See those functions for the arguments the function is called with.

(def debug-opts (atom {}))

(defn edit-nodes-error-checks [err-desc-str ret & args]
  (let [i (edit-nodes-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args args, :edit-nodes-errors i}))))

(defn basic-node-error-checks [err-desc-str ret & args]
  (let [i (basic-node-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args args, :basic-node-errors i}))))

(defn ranges-error-checks [err-desc-str ret & args]
  (let [i (ranges-errors ret)]
    (when (:error i)
      (println (str "ERROR: found problem with ret value from " err-desc-str
                    ": " (:description i)))
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args args, :ranges-errors i}))))

(defn validating-pop [f err-desc-str coll]
  (let [_ (println "called validating-pop #=" (count coll))
        coll-seq (copying-seq coll)
        exp-ret-seq (butlast coll-seq)
        ret (f coll)
        ret-seq (copying-seq ret)]
    (when (not= ret-seq exp-ret-seq)
      (println "ERROR: (pop coll) returned incorrect value")
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args (list coll),
                            :coll-seq coll-seq, :ret-seq ret-seq,
                            :exp-ret-seq exp-ret-seq}))
    ret))

(defn dbg-pop [coll]
  (if-not (pd/is-vector? coll)
    (clojure.core/pop coll)
    (let [opts (get @debug-opts :pop)
          err-desc-str "pop"]
      (when (:trace opts)
        (println "dbg-pop called with #v=" (count coll)
                 "(type v)=" (type coll)))
      (let [ret (if (:validate opts)
                  (validating-pop clojure.core/pop err-desc-str coll)
                  (clojure.core/pop coll))]
        (doseq [check-fn (:return-value-checks opts)]
          (check-fn err-desc-str ret coll))
        ret))))

(defn validating-pop! [f err-desc-str coll]
  (let [_ (println "called validating-pop! #=" (count coll))
        coll-seq (copying-seq coll)
        exp-ret-seq (butlast coll-seq)
        ret (f coll)
        ret-seq (copying-seq ret)]
    (when (not= ret-seq exp-ret-seq)
      (println "ERROR: (pop! coll) returned incorrect value")
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args (list coll),
                            :coll-seq coll-seq, :ret-seq ret-seq,
                            :exp-ret-seq exp-ret-seq}))
    ret))

(defn dbg-pop! [coll]
  (if-not (pd/is-vector? coll)
    (clojure.core/pop! coll)
    (let [opts (get @debug-opts :pop!)
          err-desc-str "pop!"]
      (when (:trace opts)
        (println "dbg-pop! called with #v=" (count coll)
                 "(type v)=" (type coll)))
      (let [ret (if (:validate opts)
                  (validating-pop! clojure.core/pop! err-desc-str coll)
                  (clojure.core/pop! coll))]
        (doseq [check-fn (:return-value-checks opts)]
          (check-fn err-desc-str ret coll))
        ret))))

(defn validating-transient [f err-desc-str coll]
  (let [_ (println "called validating-transient #=" (count coll))
        coll-seq (copying-seq coll)
        exp-ret-seq coll-seq
        ret (f coll)
        ret-seq (copying-seq ret)]
    (when (not= ret-seq exp-ret-seq)
      (println "ERROR: (transient coll) returned incorrect value")
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args (list coll),
                            :coll-seq coll-seq, :ret-seq ret-seq,
                            :exp-ret-seq exp-ret-seq}))
    ret))

(defn dbg-transient [coll]
  (if-not (pd/is-vector? coll)
    (clojure.core/transient coll)
    (let [opts (get @debug-opts :transient)
          err-desc-str "transient"]
      (when (:trace opts)
        (println "dbg-transient called with #v=" (count coll)
                 "(type v)=" (type coll)))
      (let [ret (if (:validate opts)
                  (validating-transient clojure.core/transient err-desc-str
                                        coll)
                  (clojure.core/transient coll))]
        (doseq [check-fn (:return-value-checks opts)]
          (check-fn err-desc-str ret coll))
        ret))))

;; Note: One possible advantage to having a validator for splice-rrbts
;; is that fv/catvec can call splice-rrbts multiple times, any one of
;; which can have an error in its return value, so wrapping validation
;; around splice-rrbts should be able to catch any errors closer to
;; the source of the problem.

;; The only disadvantage I can think of is that it will be slower if
;; there are many catvec calls on 3 or more vectors, since
;; splice-rrbts will be called once for every pair of vectors, then in
;; a lg(N) depth tree of calls on intermediate results.

(defn validating-splice-rrbts #?(:clj [err-desc-str nm am v1 v2]
                                 :cljs [err-desc-str v1 v2])
  ;;(println "validating-splice-rrbts called")
  (let [orig-fn clojure.core.rrb-vector.rrbt/splice-rrbts
        v1-seq (copying-seq v1)
        v2-seq (copying-seq v2)
        exp-ret-seq (concat v1-seq v2-seq)
        ret #?(:clj (orig-fn nm am v1 v2)
               :cljs (orig-fn v1 v2))
        ret-seq (copying-seq ret)]
    (when (not= ret-seq exp-ret-seq)
      (println "ERROR: splice-rrbts returned incorrect value")
      (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                            :args #?(:clj (list nm am v1 v2)
                                     :cljs (list v1 v2)),
                            :v1-seq v1-seq, :v2-seq v2-seq, :ret-seq ret-seq,
                            :exp-ret-seq exp-ret-seq}))
    ret))

(defn dbg-splice-rrbts [& args]
  (let [opts (get @debug-opts :splice-rrbts)
        err-desc-str "splice-rrbts"]
    (when (:trace opts)
      (let [#?(:clj [_ _ v1 v2]
               :cljs [v1 v2]) args]
        (println "dbg-splice-rrbts called with #v1=" (count v1)
                 "#v2=" (count v2)
                 "(type v1)=" (type v1)
                 "(type v2)=" (type v2))))
    (let [ret (if (:validate opts)
                (apply validating-splice-rrbts err-desc-str args)
                (apply clojure.core.rrb-vector.rrbt/splice-rrbts args))]
      (doseq [check-fn (:return-value-checks opts)]
        (apply check-fn err-desc-str ret args))
      ret)))

(defn dbg-splicev [v1 v2]
  (let [rv1 (#?(:clj as-rrbt :cljs -as-rrbt) v1)]
    (dbg-splice-rrbts #?@(:clj ((.-nm rv1) (.-am rv1)))
                      rv1 (#?(:clj as-rrbt :cljs -as-rrbt) v2))))

(defn dbg-catvec-impl
  ([]
     [])
  ([v1]
     v1)
  ([v1 v2]
     (dbg-splicev v1 v2))
  ([v1 v2 v3]
     (dbg-splicev (dbg-splicev v1 v2) v3))
  ([v1 v2 v3 v4]
     (dbg-splicev (dbg-splicev v1 v2) (dbg-splicev v3 v4)))
  ([v1 v2 v3 v4 & vn]
     (dbg-splicev (dbg-splicev (dbg-splicev v1 v2) (dbg-splicev v3 v4))
                  (apply dbg-catvec-impl vn))))

(defn validating-catvec [err-desc-str & vs]
  (let [orig-fn dbg-catvec-impl  ;; clojure.core.rrb-vector/catvec
        vs-seqs (doall (map copying-seq vs))
        exp-ret-seq (apply concat vs-seqs)
        ret (apply orig-fn vs)
        ret-seq (copying-seq ret)]
    (when (not= ret-seq exp-ret-seq)
      (println "ERROR: catvec returned incorrect value")
      (record-failure-data {:err-desc-str err-desc-str, :ret ret, :args vs,
                            :vs-seqs vs-seqs, :ret-seq ret-seq,
                            :exp-ret-seq exp-ret-seq}))
    ret))

(defn dbg-catvec [& args]
  (let [opts (get @debug-opts :catvec)
        err-desc-str "catvec"]
    (when (:trace opts)
      (println "dbg-catvec called with" (count args) "args:")
      (dorun (map-indexed (fn [idx v]
                            (println "    arg" (inc idx) " count=" (count v)
                                     "type=" (type v)))
                          args)))
    (let [ret (if (:validate opts)
                (apply validating-catvec err-desc-str args)
                (apply dbg-catvec-impl ;; clojure.core.rrb-vector/catvec
                       args))]
      (doseq [check-fn (:return-value-checks opts)]
        (apply check-fn err-desc-str ret args))
      ret)))

(defn validating-slicev
  ([err-desc-str coll start]
   (validating-slicev err-desc-str coll start (count coll)))
  ([err-desc-str coll start end]
   (let [coll-seq (copying-seq coll)
         exp-ret-seq (take (- end start) (drop start coll-seq))
         ret (#?(:clj clojure.core.rrb-vector.protocols/slicev
                 :cljs clojure.core.rrb-vector.protocols/-slicev)
              coll start end)
         ret-seq (copying-seq ret)]
     (when (not= ret-seq exp-ret-seq)
       (println "ERROR: (slicev coll start end) returned incorrect value")
       (record-failure-data {:err-desc-str err-desc-str, :ret ret,
                             :args (list coll start end),
                             :coll-seq coll-seq, :ret-seq ret-seq,
                             :exp-ret-seq exp-ret-seq}))
     ret)))

(defn dbg-slicev [& args]
  (let [opts (get @debug-opts :slicev)
        err-desc-str "slicev"]
    (when (:trace opts)
      (let [[v start end] args]
        (println "dbg-slicev #v=" (count v) "start=" start "end=" end
                 "type=" (type v))))
    (let [ret (if (:validate opts)
                (apply validating-slicev err-desc-str args)
                (apply #?(:clj clojure.core.rrb-vector.protocols/slicev
                          :cljs clojure.core.rrb-vector.protocols/-slicev)
                       args))]
      (doseq [check-fn (:return-value-checks opts)]
        (apply check-fn err-desc-str ret args))
      ret)))

(defn dbg-subvec
  ([v start]
   (dbg-slicev v start (count v)))
  ([v start end]
   (dbg-slicev v start end)))

(defn check-subvec [init & starts-and-ends]
  (let [v1 (loop [v   (vec (range init))
                  ses (seq starts-and-ends)]
             (if ses
               (let [[s e] ses]
                 (recur (dbg-subvec v s e) (nnext ses)))
               v))
        v2 (loop [v   (fv/vec (range init))
                  ses (seq starts-and-ends)]
             (if ses
               (let [[s e] ses]
                 (recur (dbg-subvec v s e) (nnext ses)))
               v))]
    (pd/same-coll? v1 v2)))

(defn check-catvec [& counts]
  (let [ranges (map range counts)
        v1 (apply concat ranges)
        v2 (apply dbg-catvec (map fv/vec ranges))]
    (pd/same-coll? v1 v2)))

(defn generative-check-subvec [iterations max-init-cnt slices]
  (dotimes [_ iterations]
    (let [init-cnt (rand-int (inc max-init-cnt))
          s1       (rand-int init-cnt)
          e1       (+ s1 (rand-int (- init-cnt s1)))]
      (loop [s&es [s1 e1] cnt (- e1 s1) slices slices]
        (if (or (zero? cnt) (zero? slices))
          (if-not (try (apply check-subvec init-cnt s&es)
                       (catch #?(:clj Exception :cljs js/Error) e
                         (throw
                          (ex-info "check-subvec failure w/ Exception"
                                   {:init-cnt init-cnt :s&es s&es}
                                   e))))
            (throw
             (ex-info "check-subvec failure w/o Exception"
                      {:init-cnt init-cnt :s&es s&es})))
          (let [s (rand-int cnt)
                e (+ s (rand-int (- cnt s)))
                c (- e s)]
            (recur (conj s&es s e) c (dec slices)))))))
  true)

(defn generative-check-catvec [iterations max-vcnt min-cnt max-cnt]
  (dotimes [_ iterations]
    (let [vcnt (inc (rand-int (dec max-vcnt)))
          cnts (vec (repeatedly vcnt
                                #(+ min-cnt
                                    (rand-int (- (inc max-cnt) min-cnt)))))]
      (if-not (try (apply check-catvec cnts)
                   (catch #?(:clj Exception :cljs js/Error) e
                     (throw
                      (ex-info "check-catvec failure w/ Exception"
                               {:cnts cnts}
                               e))))
        (throw
         (ex-info "check-catvec failure w/o Exception" {:cnts cnts})))))
  true)