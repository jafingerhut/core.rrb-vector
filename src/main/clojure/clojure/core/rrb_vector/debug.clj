(ns clojure.core.rrb-vector.debug
  (:require clojure.core.rrb-vector.rrbt
            [clojure.core.rrb-vector.nodes
             :refer [ranges object-nm primitive-nm object-am int-array?
                     branch-factor log-branch-factor branch-factor-plus-one]]
            [clojure.core.rrb-vector :as fv])
  (:import (clojure.lang PersistentVector PersistentVector$TransientVector
                         PersistentVector$Node APersistentVector$SubVector)
           (java.util.concurrent.atomic AtomicReference)
           (java.lang.reflect Field Method)
           (clojure.core Vec VecNode ArrayManager)
           (clojure.core.rrb_vector.rrbt Vector Transient)
           (clojure.core.rrb_vector.nodes NodeManager)))

(def int-width-bits 32)

;; Work around the fact that several fields of type
;; PersistentVector$TransientVector are private, but note that this is
;; only intended for debug use.
(def ^Class transient-core-vec-class (class (transient (vector))))
(def ^Field transient-core-root-field (.getDeclaredField transient-core-vec-class "root"))
(.setAccessible transient-core-root-field true)
(def ^Field transient-core-shift-field (.getDeclaredField transient-core-vec-class "shift"))
(.setAccessible transient-core-shift-field true)
(def ^Field transient-core-tail-field (.getDeclaredField transient-core-vec-class "tail"))
(.setAccessible transient-core-tail-field true)
(def ^Field transient-core-cnt-field (.getDeclaredField transient-core-vec-class "cnt"))
(.setAccessible transient-core-cnt-field true)

(def transient-core-vec-tailoff-methods
  (filter #(= "tailoff" (.getName %))
          (.getDeclaredMethods transient-core-vec-class)))
(assert (= (count transient-core-vec-tailoff-methods) 1))
(def ^Method transient-core-vec-tailoff-method
  (first transient-core-vec-tailoff-methods))
(.setAccessible transient-core-vec-tailoff-method true)


(def ^Class persistent-core-vec-class (class (vector)))
(def persistent-core-vec-tailoff-methods
  (filter #(= "tailoff" (.getName %))
          (.getDeclaredMethods persistent-core-vec-class)))
(assert (= (count persistent-core-vec-tailoff-methods) 1))
(def ^Method persistent-core-vec-tailoff-method
  (first persistent-core-vec-tailoff-methods))
(.setAccessible persistent-core-vec-tailoff-method true)


(defn internal-node-type? [obj]
  (contains? #{PersistentVector$Node VecNode} (class obj)))

(defn persistent-vector-type? [obj]
  (contains? #{PersistentVector Vec Vector}
             (class obj)))

(defn transient-vector-type? [obj]
  (contains? #{PersistentVector$TransientVector Transient}
             (class obj)))

(defn vector-type? [obj]
  (contains? #{PersistentVector Vec Vector
               PersistentVector$TransientVector Transient}
             (class obj)))

(defn debug-tailoff [v]
  (cond
    (instance? PersistentVector v)
    (.invoke persistent-core-vec-tailoff-method v (object-array 0))

    (= PersistentVector$TransientVector (class v))
    (.invoke transient-core-vec-tailoff-method v (object-array 0))

    :else
    (.tailoff v)))

(defn subvector-data [v]
  (if (instance? APersistentVector$SubVector v)
    (let [^APersistentVector$SubVector v v]
      {:orig-v v
       :subvector? true
       :v (.v v)
       :subvec-start (.start v)
       :subvec-end (.end v)})
    {:orig-v v
     :subvector? false
     :v v}))

;; All of the classes below have a .tailoff method implementation that
;; works correctly for that class.  You can use the debug-tailoff
;; function to work around the fact that this method is not public for
;; some of the vector classes.

(defn accessors-for [v]
  (condp identical? (class v)
    PersistentVector [#(.-root ^PersistentVector %)
                      #(.-shift ^PersistentVector %)
                      #(.-tail ^PersistentVector %)
                      object-nm
                      #(.-cnt ^PersistentVector %)
                      object-am]
    PersistentVector$TransientVector
                     [#(.get transient-core-root-field ^PersistentVector$TransientVector %)
                      #(.get transient-core-shift-field ^PersistentVector$TransientVector %)
                      #(.get transient-core-tail-field ^PersistentVector$TransientVector %)
                      object-nm
                      #(.get transient-core-cnt-field ^PersistentVector$TransientVector %)
                      object-am]
    Vec              [#(.-root ^Vec %)
                      #(.-shift ^Vec %)
                      #(.-tail ^Vec %)
                      primitive-nm
                      #(.-cnt ^Vec %)
                      #(.-am ^Vec %)]
    Vector           [#(.-root ^Vector %)
                      #(.-shift ^Vector %)
                      #(.-tail ^Vector %)
                      (.-nm ^Vector v)
                      #(.-cnt ^Vector %)
                      #(.-am ^Vector %)]
    Transient        [#(.debugGetRoot ^Transient %)
                      #(.debugGetShift ^Transient %)
                      #(.debugGetTail ^Transient %)
                      (.-nm ^Transient v)
                      #(.debugGetCnt ^Transient %)
                      (.-am ^Transient v)]))

(defn unwrap-subvec-accessors-for [v]
  (let [{:keys [v] :as m} (subvector-data v)
        [extract-root extract-shift extract-tail ^NodeManager nm extract-cnt
         ^ArrayManager am]
        (accessors-for v)]
    (merge m
           {:extract-root extract-root
            :extract-shift extract-shift
            :extract-tail extract-tail
            :nm nm
            :extract-cnt extract-cnt
            :am am})))

(defn dbg-vec [v]
  (let [{:keys [v subvector? subvec-start subvec-end
                extract-root extract-shift extract-tail ^NodeManager nm]}
        (unwrap-subvec-accessors-for v)
        root  (extract-root v)
        shift (extract-shift v)
        tail  (extract-tail v)]
    (when subvector?
      (printf "SubVector from start %d to end %d of vector:\n"
              subvec-start subvec-end))
    (letfn [(go [indent shift i node]
              (when node
                (dotimes [_ indent]
                  (print "  "))
                (printf "%02d:%02d %s" shift i
                        (let [cn (.getName (class node))
                              d  (.lastIndexOf cn ".")]
                          (subs cn (inc d))))
                (if-not (or (zero? shift) (.regular nm node))
                  (print ":" (seq (ranges nm node))))
                (if (zero? shift)
                  (print ":" (vec (.array nm node))))
                (println)
                (if-not (zero? shift)
                  (dorun
                   (map-indexed (partial go (inc indent) (- shift log-branch-factor))
                                (let [arr (.array nm node)]
                                  (if (.regular nm node)
                                    arr
                                    (butlast arr))))))))]
      (printf "%s (%d elements):\n" (.getName (class v)) (count v))
      (go 0 shift 0 root)
      (println "tail:" (vec tail)))))

(defn first-diff [xs ys]
  (loop [i 0 xs (seq xs) ys (seq ys)]
    (if (try (and xs ys (= (first xs) (first ys)))
             (catch Exception e
               (.printStackTrace e)
               i))
      (let [xs (try (next xs)
                    (catch Exception e
                      (prn :xs i)
                      (throw e)))
            ys (try (next ys)
                    (catch Exception e
                      (prn :ys i)
                      (throw e)))]
        (recur (inc i) xs ys))
      (if (or xs ys)
        i
        -1))))

(defn same-coll? [a b]
  (and (= (count a)
          (count b)
          (.size ^java.util.Collection a)
          (.size ^java.util.Collection b))
       (= a b)
       (= b a)
       (= (hash a) (hash b))
       (= (.hashCode ^Object a) (.hashCode ^Object b))))

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
    (same-coll? v1 v2)))

(defn check-catvec [& counts]
  (let [ranges (map range counts)
        v1 (apply concat ranges)
        v2 (apply fv/catvec (map fv/vec ranges))]
    (same-coll? v1 v2)))

(defn generative-check-subvec [iterations max-init-cnt slices]
  (dotimes [_ iterations]
    (let [init-cnt (rand-int (inc max-init-cnt))
          s1       (rand-int init-cnt)
          e1       (+ s1 (rand-int (- init-cnt s1)))]
      (loop [s&es [s1 e1] cnt (- e1 s1) slices slices]
        (if (or (zero? cnt) (zero? slices))
          (if-not (try (apply check-subvec init-cnt s&es)
                       (catch Exception e
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
                   (catch Exception e
                     (throw
                      (ex-info "check-catvec failure w/ Exception"
                               {:cnts cnts}
                               e))))
        (throw
         (ex-info "check-catvec failure w/o Exception" {:cnts cnts})))))
  true)

(defn count-nodes [& vs]
  (let [m (java.util.IdentityHashMap.)]
    (doseq [v vs]
      (let [{:keys [v extract-root extract-shift ^NodeManager nm]}
            (unwrap-subvec-accessors-for v)]
        (letfn [(go [n shift]
                  (when n
                    (.put m n n)
                    (if-not (zero? shift)
                      (let [arr (.array nm n)
                            ns  (take branch-factor arr)]
                        (doseq [n ns]
                          (go n (- shift log-branch-factor)))))))]
          (go (extract-root v) (extract-shift v)))))
    (.size m)))


;; Other invariants/conditions that could be checked:

;; For Clojure's built-in vector, it is probably an invariant that all
;; elements are "as far left as they can be", for the number of valid
;; elements.  That is probably true for many RRB vectors, but
;; definitely not in general.

(defn all-vector-tree-nodes [v]
  (let [{:keys [v extract-root extract-shift extract-tail ^NodeManager nm]}
        (unwrap-subvec-accessors-for v)
        root  (extract-root v)
        shift (extract-shift v)]
    (letfn [(go [depth shift node]
              (if node
                (if (not= shift 0)
                  (cons
                   {:depth depth :shift shift :kind :internal :node node}
                   (apply concat
                          (map (partial go (inc depth) (- shift log-branch-factor))
                               (let [arr (.array nm node)]
                                 (if (.regular nm node)
                                   arr
                                   (butlast arr))))))
                  (cons {:depth depth :shift shift :kind :internal :node node}
                        (map (fn [x]
                               {:depth (inc depth) :kind :leaf :value x})
                             (.array nm node))))))]
      (cons {:depth 0 :kind :base :shift shift :value v}
            (go 1 shift root)))))

;; All nodes that should be internal nodes are one of the internal
;; node types satisfying internal-node-type?  All nodes that are less
;; than "leaf depth" must be internal nodes, and none of the ones
;; at "leaf depth" should be.  Probably the most general restriction
;; checking for leaf values should be simply that they are any type
;; that is _not_ an internal node type.  They could be objects that
;; return true for vector-type? for example, if a vector is an element
;; of another vector.

(defn leaves-with-internal-node-type [node-infos]
  (filter (fn [node-info]
            (and (= :leaf (:kind node-info))
                 (internal-node-type? (:node node-info))))
          node-infos))

(defn non-leaves-not-internal-node-type [node-infos]
  (filter (fn [node-info]
            (and (= :internal (:kind node-info))
                 (not (internal-node-type? (:node node-info)))))
          node-infos))

;; TBD: The definition of nth in deftype Vector seems to imply that
;; every descendant of a 'regular' node must also be regular.  That
;; would be a straightforward sanity check to make, to return an error
;; if a non-regular node is found with a regular ancestor in the tree.

(defn basic-node-errors [v]
  (let [{:keys [v extract-shift]} (unwrap-subvec-accessors-for v)
        shift (extract-shift v)
        nodes (all-vector-tree-nodes v)
        by-kind (group-by :kind nodes)
        leaf-depths (set (map :depth (:leaf by-kind)))
        expected-leaf-depth (+ (/ shift log-branch-factor) 2)
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
          (> (count v) branch-factor-plus-one) #{(dec expected-leaf-depth)}
          :else #{(dec expected-leaf-depth)
                  (- expected-leaf-depth 2)})]
    (cond
      (not= (mod shift log-branch-factor) 0)
      {:error true
       :description (str "shift value in root must be a multiple of"
                         " log-branch-factor=" log-branch-factor ".  Found "
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
       :description "A leaf (at max depth) has one of the internal node types, returning true for internal-node-type?"
       :data (first (leaves-with-internal-node-type nodes))}

      (seq (non-leaves-not-internal-node-type nodes))
      {:error true
       :description "A non-leaf node has a type that returns false for internal-node-type?"
       :data (first (non-leaves-not-internal-node-type nodes))}

      :else
      {:error false})))

(defn objects-in-slot-branch-factor-of-obj-arrays
  "Function to look for errors of the form where a node's node.array
  object, which is often an array of branch-factor or branch-factor-plus-one java.lang.Object's, has
  an element at index branch-factor that is not nil, and refers to an object that
  is of any type _except_ an array of ints.  There appears to be some
  situation in which this can occur, but it seems to almost certainly
  be a bug if that happens, and we should be able to detect it
  whenever it occurs."
  [v]
  (let [{:keys [v ^NodeManager nm]} (unwrap-subvec-accessors-for v)
        node-maps (all-vector-tree-nodes v)
        internal (filter #(= :internal (:kind %)) node-maps)]
    (keep (fn [node-info]
            (let [^objects arr (.array nm (:node node-info))
                  n (count arr)]
              (if (== n branch-factor)
                (aget arr branch-factor))))
          internal)))

(defn ranges-not-int-array [x]
  (seq (remove int-array? (objects-in-slot-branch-factor-of-obj-arrays x))))

(defn atomicref? [x]
  (instance? AtomicReference x))

(defn thread? [x]
  (instance? java.lang.Thread x))

(defn non-identical-edit-nodes [v]
  (let [{:keys [v]} (unwrap-subvec-accessors-for v)
        node-maps (all-vector-tree-nodes v)
        ^java.util.IdentityHashMap ihm (java.util.IdentityHashMap.)]
    (doseq [i node-maps]
      (when (= :internal (:kind i))
        (.put ihm (.edit (:node i)) true)))
    ihm))

(defn edit-nodes-errors [v]
  (let [{:keys [v extract-root]} (unwrap-subvec-accessors-for v)
        klass (class v)
        ^java.util.IdentityHashMap ihm (non-identical-edit-nodes v)
        objs-maybe-some-nils (.keySet ihm)
        ;; I do not believe that Clojure's built-in vector types can
        ;; ever have edit fields equal to nil, but there are some
        ;; cases where I have seen core.rrb-vector edit fields equal
        ;; to nil.  As far as I can tell this seems harmless, as long
        ;; as it is in a persistent vector, not a transient one.
        objs (remove nil? objs-maybe-some-nils)
        neither-nil-nor-atomicref (remove atomicref? objs)]
    (if (seq neither-nil-nor-atomicref)
      {:error true
       :description (str "Found edit object with class "
                         (class (first neither-nil-nor-atomicref))
                         " - expecting nil or AtomicReference")
       :data ihm
       :not-atomic-refs neither-nil-nor-atomicref}
      (let [refd-objs (map #(.get ^AtomicReference %) objs)
            non-nils (remove nil? refd-objs)
            not-threads (remove thread? non-nils)
            root-edit (.edit (extract-root v))]
        (cond
          (seq not-threads)
          {:error true
           :description (str "Found edit AtomicReference ref'ing neither nil"
                             " nor a Thread object")
           :data ihm}
          (persistent-vector-type? v)
          (if (= (count non-nils) 0)
            {:error false}
            {:error true
             :description (str "Within a persistent (i.e. not transient)"
                               " vector, found at least one edit"
                               " AtomicReference object that ref's a Thread"
                               " object.  Expected all of them to be nil.")
             :data ihm
             :val1 (count non-nils)
             :val2 non-nils})
          
          (transient-vector-type? v)
          (cond
            (not= (count non-nils) 1)
            {:error true
             :description (str "Within a transient vector, found "
                               (count non-nils) " edit AtomicReference"
                               " object(s) that ref's a Thread object."
                               "  Expected exactly 1.")
             :data ihm
             :val1 (count non-nils)
             :val2 non-nils}
            (not (atomicref? root-edit))
            {:error true
             :description (str "Within a transient vector, found root edit"
                               " field that was ref'ing an object with class "
                               (class root-edit)
                               " - expected AtomicReference.")
             :data root-edit}
            (not (thread? (.get ^AtomicReference root-edit)))
            (let [obj (.get ^AtomicReference root-edit)]
              {:error true
               :description (str "Within a transient vector, found root edit"
                                 " field ref'ing an AtomicReference object,"
                                 " but that in turn ref'd something with class "
                                 (class obj)
                                 " - expected java.lang.Thread.")
               :data obj})
            :else {:error false})

          :else {:error true
                 :description (str "Unknown class " klass " for object checked"
                                   " by edit-nodes-wrong-number-of-threads")
                 :data v})))))

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
                    (<= root-node-cnt branch-factor)  ;; all elements in tail
                    (= 0 num-non-nil))
               (<= 1 num-non-nil branch-factor)))
      {:error true, :kind :internal
       :description
       (str "Found internal regular node with # full + # partial=" num-non-nil
            " children outside of range [1, branch-factor]."
            " root-node?=" root-node? " root-node-cnt=" root-node-cnt)
       :data children}
      :else
      {:error false, :kind :internal,
       :full? (= branch-factor (count full-children))
       :count (reduce + (map #(or (:count %) 0) children))})))


(defn non-regular-node-errors [node nm children]
  (let [rng (ranges nm node)
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
      (not= num-non-nil (aget rng branch-factor))
      {:error true, :kind :internal,
       :description (str "Found internal non-regular node with "
                         num-non-nil " non-nil, " num-nil " nil children, and"
                         " last elem of ranges=" (aget rng branch-factor)
                         " - expected it to match # non-nil children.")}
      (not= expected-ranges (take (count expected-ranges) (seq rng)))
      {:error true, :kind :internal,
       :description (str "Found internal non-regular node with "
                         num-non-nil " non-nil, " num-nil " nil children, and"
                         " # children prefix sums: " expected-ranges
                         " - expected that to match stored ranges: "
                         (seq rng))}
      ;; I believe that there must always be at least one
      ;; non-nil-children.  By checking for this condition, we will
      ;; definitely find out if it is ever violated.
      ;; TBD: What if we have a tree with ranges, and then remove all
      ;; elements?  Does the resulting tree triger this error?
      (not (<= 1 (aget rng branch-factor) branch-factor))
      {:error true, :kind :internal
       :description (str "Found internal non-regular node with (aget rng branch-factor)"
                         "=" (aget rng branch-factor) " outside of range [1, branch-factor].")}
      :else
      {:error false, :kind :internal, :full? false,
       :count (last expected-ranges)})))


(defn max-capacity-over-branch-factor-squared [root-shift]
  (let [shift-amount (max 0 (- root-shift (* 2 branch-factor)))]
    (bit-shift-left 1 shift-amount)))


(defn ranges-errors [v]
  (let [{:keys [v extract-root extract-shift extract-tail extract-cnt
                ^NodeManager nm ^ArrayManager am]}
        (unwrap-subvec-accessors-for v)
        root  (extract-root v)
        root-node-cnt (count v)
        root-shift (extract-shift v)
        tail-off (debug-tailoff v)
        tail (extract-tail v)]
    (letfn [
      (go [shift node]
        (cond
          (nil? node) {:error false :kind :nil}
          (zero? shift) (let [n (count (.array nm node))]
                          (merge {:error (zero? n), :kind :leaves,
                                  :full? (= n branch-factor), :count n}
                                 (if (zero? n)
                                   {:description
                                    (str "Leaf array has 0 elements."
                                         "  Expected > 0.")})))
          :else ;; non-0 shift
          (let [children (map (partial go (- shift log-branch-factor))
                              (let [arr (.array nm node)]
                                (if (.regular nm node)
                                  arr
                                  (butlast arr))))
                errs (filter :error children)]
            (cond
              (seq errs) {:error true, :description "One or more errors found",
                          :data errs}
              (not= branch-factor (count children))
              {:error true, :kind :internal,
               :description (str "Found internal node that has "
                                 (count children) " children - expected branch-factor.")}
              (.regular nm node) (regular-node-errors (= shift root-shift)
                                                      root-node-cnt children)
              :else (non-regular-node-errors node nm children)))))]
      (let [x (go root-shift root)]
        (cond
          (:error x) x
          (not= tail-off (:count x))
          {:error true, :kind :root,
           :description (str "Found tail-off=" tail-off " != " (:count x)
                             "=count of values beneath internal nodes")
           :internal-node-leaf-count (:count x) :tail-off tail-off
           :cnt (extract-cnt v)}
          (and (transient-vector-type? v)
               (not= (.alength am tail) branch-factor))
          {:error true, :kind :root,
           :description (str "Found transient vector with tail length "
                             (.alength am tail) " - expecting branch-factor")}
          ;; It is always a bad thing if shift becomes more than
          ;; int-width-bits, because the bit-shift-left and
          ;; bit-shift-right operations on ints actually behave
          ;; like (bit-shift-left x (mod shift-amount int-width-bits))
          ;; for shift-amount over int-width-bits.  It is also likely
          ;; a bug in the implementation if that happens.
          (>= root-shift int-width-bits)
          {:error true, :kind :root,
           :description (str "shift of root is " root-shift " >= "
                             int-width-bits ", which is not supported.")}
          ;; This is not necessarily a bug, but it seems likely to be
          ;; a bug if a tree is less than 1/branch-factor-squared full compared to its
          ;; max capacity.  1/branch-factor full is normal when a tree becomes 1
          ;; deeper than it was before.
          (< 0 (:count x) (max-capacity-over-branch-factor-squared root-shift))
          {:error true, :kind :root,
           :description (str "For root shift=" root-shift " the maximum "
                             "capacity divided by branch-factor-squared is "
                             (max-capacity-over-branch-factor-squared root-shift)
                             " but the tree contains only "
                             (:count x) " vector elements outside of the tail")}
          :else x)))))
