(ns clojure.core.rrb-vector.debug
  (:require clojure.core.rrb-vector.rrbt
            [clojure.core.rrb-vector :as fv]
            ;; This page:
            ;; https://clojure.org/guides/reader_conditionals refers
            ;; to code that can go into common cljc files as platform
            ;; independent, and the code in the clj or cljs files as
            ;; platform dependent, so I will use that terminology
            ;; here, too.
            [clojure.core.rrb-vector.debug-platform-dependent :as pd]))


;; Functions expected to be defined in the appropriate
;; .debug-platform-dependent namespace:

;; pd/internal-node?
;; pd/persistent-vector?
;; pd/transient-vector?
;; pd/is-vector?
;; pd/dbg-tailoff  (formerly debug-tailoff)
;; pd/dbg-tidx (formerly debug-tailoff for clj, debug-tidx for cljs)
;; pd/format
;; pd/printf
;; pd/subvector-data
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

;; TBD: Needs a little reader conditional magic for catch Exception
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

(defn check-subvec [init & starts-and-ends]
  (let [v1 (loop [v   (vec (range init))
                  ses (seq starts-and-ends)]
             (if ses
               (let [[s e] ses]
                 (recur (subvec v s e) (nnext ses)))
               v))
        v2 (loop [v   (fv/vec (range init))
                  ses (seq starts-and-ends)]
             (if ses
               (let [[s e] ses]
                 (recur (fv/subvec v s e) (nnext ses)))
               v))]
    (pd/same-coll? v1 v2)))

(defn check-catvec [& counts]
  (let [ranges (map range counts)
        v1 (apply concat ranges)
        v2 (apply fv/catvec (map fv/vec ranges))]
    (pd/same-coll? v1 v2)))

;; TBD: Need to make (catch Exception e ...) somehow common for this
;; cljc file.  Use conditional reader code?
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


;; edit-node-errors is completely defined in platform-specific source
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
