(ns clojure.core.rrb-vector.debug
  (:require clojure.core.rrb-vector.rrbt
            [clojure.core.rrb-vector.nodes
             :refer [ranges object-nm primitive-nm int-array?]]
            [clojure.core.rrb-vector :as fv])
  (:import (clojure.lang PersistentVector PersistentVector$TransientVector
                         PersistentVector$Node APersistentVector$SubVector)
           (clojure.core Vec VecNode)
           (clojure.core.rrb_vector.rrbt Vector Transient)
           (clojure.core.rrb_vector.nodes NodeManager)))

;; Work around the fact that several fields of type
;; PersistentVector$TransientVector are private, but note that this is
;; only intended for debug use.
(def transient-core-vec (transient (vector)))
(def transient-core-vec-class (class transient-core-vec))
(def transient-core-root-field (.getDeclaredField transient-core-vec-class "root"))
(.setAccessible transient-core-root-field true)
(def transient-core-shift-field (.getDeclaredField transient-core-vec-class "shift"))
(.setAccessible transient-core-shift-field true)
(def transient-core-tail-field (.getDeclaredField transient-core-vec-class "tail"))
(.setAccessible transient-core-tail-field true)

(defn internal-node-type? [obj]
  (contains? #{PersistentVector$Node VecNode} (class obj)))

(defn vector-type? [obj]
  (contains? #{PersistentVector PersistentVector$TransientVector
               Vec Vector Transient}
             (class obj)))

(defn subvector? [v]
  (if (instance? APersistentVector$SubVector v)
    {:subvector? true
     :vector-inside (.v v)
     :start (.start v)
     :end (.end v)}
    {:subvector? false
     :vector-inside v}))

(defn accessors-for [v]
  (condp identical? (class v)
    PersistentVector [#(.-root ^PersistentVector %)
                      #(.-shift ^PersistentVector %)
                      #(.-tail ^PersistentVector %)
                      object-nm]
    PersistentVector$TransientVector
                     [#(.get transient-core-root-field ^PersistentVector$TransientVector %)
                      #(.get transient-core-shift-field ^PersistentVector$TransientVector %)
                      #(.get transient-core-tail-field ^PersistentVector$TransientVector %)
                      object-nm]
    Vec              [#(.-root ^Vec %)
                      #(.-shift ^Vec %)
                      #(.-tail ^Vec %)
                      primitive-nm]
    Vector           [#(.-root ^Vector %)
                      #(.-shift ^Vector %)
                      #(.-tail ^Vector %)
                      (.-nm ^Vector v)]
    Transient        [#(.debugGetRoot ^Transient %)
                      #(.debugGetShift ^Transient %)
                      #(.debugGetTail ^Transient %)
                      (.-nm ^Transient v)]))

(defn dbg-vec [v]
  (let [v (let [{:keys [subvector? vector-inside start end]} (subvector? v)]
            (if subvector?
              (do
                (printf "SubVector from start %d to end %d of vector:\n"
                        start end)
                vector-inside)
              v))
        [extract-root extract-shift extract-tail ^NodeManager nm]
        (accessors-for v)
        root  (extract-root v)
        shift (extract-shift v)
        tail  (extract-tail v)]
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
                   (map-indexed (partial go (inc indent) (- shift 5))
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
      (let [[extract-root extract-shift extract-tail ^NodeManager nm]
            (accessors-for v)]
        (letfn [(go [n shift]
                  (when n
                    (.put m n n)
                    (if-not (zero? shift)
                      (let [arr (.array nm n)
                            ns  (take 32 arr)]
                        (doseq [n ns]
                          (go n (- shift 5)))))))]
          (go (extract-root v) (extract-shift v)))))
    (.size m)))


;; Other invariants/conditions that could be checked:

;; For Clojure's built-in vector, it is probably an invariant that all
;; elements are "as far left as they can be", for the number of valid
;; elements.  That is probably true for many RRB vectors, but
;; definitely not in general.

(defn all-vector-tree-nodes [v]
  (let [[extract-root extract-shift extract-tail ^NodeManager nm]
        (accessors-for v)
        root  (extract-root v)
        shift (extract-shift v)
        tail  (extract-tail v)]
    (letfn [(go [depth shift i node]
              (if node
                (if (not= shift 0)
                  (cons
                   {:depth depth :shift shift :kind :internal :node node}
                   (apply concat
                          (map-indexed (partial go (inc depth) (- shift 5))
                                       (let [arr (.array nm node)]
                                         (if (.regular nm node)
                                           arr
                                           (butlast arr))))))
                  (cons {:depth depth :shift shift :kind :internal :node node}
                        (map (fn [x]
                               {:depth (inc depth) :kind :leaf :value x})
                             (.array nm node))))))]
      (cons {:depth 0 :kind :base :shift shift :value v}
            (go 1 shift 0 root)))))

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

(defn basic-node-errors [v]
  (let [[_ extract-shift _ ^NodeManager nm] (accessors-for v)
        shift (extract-shift v)
        nodes (all-vector-tree-nodes v)
        by-kind (group-by :kind nodes)
        leaf-depths (set (map :depth (:leaf by-kind)))]
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

(defn objects-in-slot-32-of-obj-arrays
  "Function to look for errors of the form where a node's node.array
  object, which is often an array of 32 or 33 java.lang.Object's, has
  an element at index 32 that is not nil, and refers to an object that
  is of any type _except_ an array of ints.  There appears to be some
  situation in which this can occur, but it seems to almost certainly
  be a bug if that happens, and we should be able to detect it
  whenever it occurs."
  [v]
  (let [v (let [{:keys [subvector? vector-inside]} (subvector? v)]
            (if subvector? vector-inside v))
        [extract-root extract-shift extract-tail ^NodeManager nm]
        (accessors-for v)
        node-maps (all-vector-tree-nodes v)
        internal (filter #(= :internal (:kind %)) node-maps)]
    (keep (fn [node-info]
            (let [arr (.array nm (:node node-info))
                  n (count arr)]
              (if (== n 33)
                (aget arr 32))))
          internal)))

(defn ranges-not-int-array [x]
  (seq (remove int-array? (objects-in-slot-32-of-obj-arrays x))))
