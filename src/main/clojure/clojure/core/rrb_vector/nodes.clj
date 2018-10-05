(ns clojure.core.rrb-vector.nodes
  (:import (clojure.core VecNode ArrayManager)
           (clojure.lang PersistentVector PersistentVector$Node)
           (java.util.concurrent.atomic AtomicReference)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Original set of values in rrb-vector library:
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;;(def ^:const branch-factor 32)   ;; must be a power of 2, and I think also at least 4
;;(def ^:const branch-factor-plus-one 33)
;;(def ^:const branch-factor-minus-one 31)
;;(def ^:const branch-factor-minus-two 30)
;;(def ^:const branch-factor-squared 1024)
;;(def ^:const log-branch-factor 5)
;;(def ^:const branch-factor-bitmask 0x1f)   ;; must be 1 less than branch-factor

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Experimental values with smaller branch factor, in hopes that test
;; cases that exhibit bugs will be smaller, too.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; I found no errors with branch-factor 4 even with this expression in
;; adventofcode:
;;(require '[adventofcode1.nineteen-b :as ntb])
;;(def x (for [n (range 1 1000)] (ntb/puzzle-b n)))

;;(def ^:const branch-factor 4)   ;; must be a power of 2, and I think also at least 4
;;(def ^:const branch-factor-plus-one 5)
;;(def ^:const branch-factor-minus-one 3)
;;(def ^:const branch-factor-minus-two 2)
;;(def ^:const branch-factor-squared 16)
;;(def ^:const log-branch-factor 2)
;;(def ^:const branch-factor-bitmask 0x3)   ;; must be 1 less than branch-factor

;; branch-factor 8

(def ^:const branch-factor 8)   ;; must be a power of 2, and I think also at least 4
(def ^:const branch-factor-plus-one 9)
(def ^:const branch-factor-minus-one 7)
(def ^:const branch-factor-minus-two 6)
(def ^:const branch-factor-squared 64)
(def ^:const log-branch-factor 3)
(def ^:const branch-factor-bitmask 0x7)   ;; must be 1 less than branch-factor

;;; array managers

(defmacro mk-am [t]
  (#'clojure.core/mk-am &env &form t))

(definline object [x] x)

(def ams
  (assoc @#'clojure.core/ams :object (mk-am object)))

(def object-am
  (ams :object))

;;; empty nodes

(def empty-pv-node PersistentVector/EMPTY_NODE)

(def empty-gvec-node clojure.core/EMPTY-NODE)

;;; node managers

(definterface NodeManager
  (node [^java.util.concurrent.atomic.AtomicReference edit arr])
  (empty [])
  (array [node])
  (^java.util.concurrent.atomic.AtomicReference edit [node])
  (^boolean regular [node])
  (clone [^clojure.core.ArrayManager am ^int shift node]))

(def object-nm
  (reify NodeManager
    (node [_ edit arr]
      (PersistentVector$Node. edit arr))
    (empty [_]
      empty-pv-node)
    (array [_ node]
      (.-array ^PersistentVector$Node node))
    (edit [_ node]
      (.-edit ^PersistentVector$Node node))
    (regular [_ node]
      (not (== (alength ^objects (.-array ^PersistentVector$Node node)) (int branch-factor-plus-one))))
    (clone [_ am shift node]
      (PersistentVector$Node.
       (.-edit ^PersistentVector$Node node)
       (aclone ^objects (.-array ^PersistentVector$Node node))))))

(def primitive-nm
  (reify NodeManager
    (node [_ edit arr]
      (VecNode. edit arr))
    (empty [_]
      empty-gvec-node)
    (array [_ node]
      (.-arr ^VecNode node))
    (edit [_ node]
      (.-edit ^VecNode node))
    (regular [_ node]
      (not (== (alength ^objects (.-arr ^VecNode node)) (int branch-factor-plus-one))))
    (clone [_ am shift node]
      (if (zero? shift)
        (VecNode. (.-edit ^VecNode node)
                  (.aclone am (.-arr ^VecNode node)))
        (VecNode. (.-edit ^VecNode node)
                  (aclone ^objects (.-arr ^VecNode node)))))))

;;; ranges

(defmacro ranges [nm node]
  `(ints (aget ~(with-meta `(.array ~nm ~node) {:tag 'objects}) branch-factor)))

(defn last-range [^NodeManager nm node]
  (let [rngs (ranges nm node)
        i    (unchecked-dec-int (aget rngs branch-factor))]
    (aget rngs i)))

(defn regular-ranges [shift cnt]
  (let [step (bit-shift-left (int 1) (int shift))
        rngs (int-array branch-factor-plus-one)]
    (loop [i (int 0) r step]
      (if (< r cnt)
        (do (aset rngs i r)
            (recur (unchecked-inc-int i) (unchecked-add-int r step)))
        (do (aset rngs i (int cnt))
            (aset rngs branch-factor (unchecked-inc-int i))
            rngs)))))

;;; root overflow

(defn overflow? [^NodeManager nm root shift cnt]
  (if (.regular nm root)
    (> (bit-shift-right (unchecked-inc-int (int cnt)) (int log-branch-factor))
       (bit-shift-left (int 1) (int shift)))
    (let [rngs (ranges nm root)
          slc  (aget rngs branch-factor)]
      (and (== slc (int branch-factor))
           (or (== (int shift) (int log-branch-factor))
               (recur nm
                      (aget ^objects (.array nm root) (unchecked-dec-int slc))
                      (unchecked-subtract-int (int shift) (int log-branch-factor))
                      (unchecked-add-int
                       (unchecked-subtract-int (aget rngs branch-factor-minus-one) (aget rngs branch-factor-minus-two))
                       (int branch-factor))))))))

;;; find nil / 0

(defn index-of-0 ^long [arr]
  (let [arr (ints arr)]
    (loop [l 0 h branch-factor-minus-one]
      (if (>= l (unchecked-dec h))
        (if (zero? (aget arr l))
          l
          (if (zero? (aget arr h))
            h
            branch-factor))
        (let [mid (unchecked-add l (bit-shift-right (unchecked-subtract h l) 1))]
          (if (zero? (aget arr mid))
            (recur l mid)
            (recur (unchecked-inc-int mid) h)))))))

(defn index-of-nil ^long [arr]
  (loop [l 0 h branch-factor-minus-one]
    (if (>= l (unchecked-dec h))
      (if (nil? (aget ^objects arr l))
        l
        (if (nil? (aget ^objects arr h))
          h
          branch-factor))
      (let [mid (unchecked-add l (bit-shift-right (unchecked-subtract h l) 1))]
        (if (nil? (aget ^objects arr mid))
          (recur l mid)
          (recur (unchecked-inc-int mid) h))))))

;;; children

(defn first-child [^NodeManager nm node]
  (aget ^objects (.array nm node) 0))

(defn last-child [^NodeManager nm node]
  (let [arr (.array nm node)]
    (if (.regular nm node)
      (aget ^objects arr (dec (index-of-nil arr)))
      (aget ^objects arr (unchecked-dec-int (aget (ranges nm node) branch-factor))))))

(defn remove-leftmost-child [^NodeManager nm shift parent]
  (let [arr (.array nm parent)]
    (if (nil? (aget ^objects arr 1))
      nil
      (let [regular? (.regular nm parent)
            new-arr  (object-array (if regular? branch-factor branch-factor-plus-one))]
        (System/arraycopy arr 1 new-arr 0 branch-factor-minus-one)
        (if-not regular?
          (let [rngs     (ranges nm parent)
                rng0     (aget rngs 0)
                new-rngs (int-array branch-factor-plus-one)
                lim      (aget rngs branch-factor)]
            (System/arraycopy rngs 1 new-rngs 0 (dec lim))
            (loop [i 0]
              (when (< i lim)
                (aset new-rngs i (- (aget new-rngs i) rng0))
                (recur (inc i))))
            (aset new-rngs branch-factor (dec (aget rngs branch-factor)))
            (aset new-rngs (dec (aget rngs branch-factor)) (int 0))
            (aset ^objects new-arr branch-factor new-rngs)))
        (.node nm (.edit nm parent) new-arr)))))

(defn replace-leftmost-child [^NodeManager nm shift parent pcnt child d]
  (if (.regular nm parent)
    (let [step (bit-shift-left 1 shift)
          rng0 (- step d)
          ncnt (- pcnt d)
          li   (bit-and (bit-shift-right shift (dec pcnt)) branch-factor-bitmask)
          arr      (.array nm parent)
          new-arr  (object-array branch-factor-plus-one)
          new-rngs (int-array branch-factor-plus-one)]
      (aset ^objects new-arr 0 child)
      (System/arraycopy arr 1 new-arr 1 li)
      (aset ^objects new-arr branch-factor new-rngs)
      (aset new-rngs 0 (int rng0))
      (aset new-rngs li (int ncnt))
      (aset new-rngs branch-factor (int (inc li)))
      (loop [i 1]
        (when (<= i li)
          (aset new-rngs i (+ (aget new-rngs (dec i)) step))
          (recur (inc i))))
      (.node nm nil new-arr))
    (let [new-arr  (aclone ^objects (.array nm parent))
          rngs     (ranges nm parent)
          new-rngs (int-array branch-factor-plus-one)
          li       (dec (aget rngs branch-factor))]
      (aset new-rngs branch-factor (aget rngs branch-factor))
      (aset ^objects new-arr branch-factor new-rngs)
      (aset ^objects new-arr 0 child)
      (loop [i 0]
        (when (<= i li)
          (aset new-rngs i (- (aget rngs i) (int d)))
          (recur (inc i))))
      (.node nm nil new-arr))))

(defn replace-rightmost-child [^NodeManager nm shift parent child d]
  (if (.regular nm parent)
    (let [arr (.array nm parent)
          i   (unchecked-dec (index-of-nil arr))]
      (if (.regular nm child)
        (let [new-arr (aclone ^objects arr)]
          (aset ^objects new-arr i child)
          (.node nm nil new-arr))
        (let [arr     (.array nm parent)
              new-arr (object-array branch-factor-plus-one)
              step    (bit-shift-left 1 shift)
              rngs    (int-array branch-factor-plus-one)]
          (aset rngs branch-factor (inc i))
          (aset ^objects new-arr branch-factor rngs)
          (System/arraycopy arr 0 new-arr 0 i)
          (aset ^objects new-arr i child)
          (loop [j 0 r step]
            (when (<= j i)
              (aset rngs j r)
              (recur (inc j) (+ r step))))
          (aset rngs i (int (last-range nm child)))
          (.node nm nil new-arr))))
    (let [rngs     (ranges nm parent)
          new-rngs (aclone rngs)
          i        (dec (aget rngs branch-factor))
          new-arr  (aclone ^objects (.array nm parent))]
      (aset ^objects new-arr i child)
      (aset ^objects new-arr branch-factor new-rngs)
      (aset new-rngs i (int (+ (aget rngs i) d)))
      (.node nm nil new-arr))))

;;; fold-tail

(defn new-path [^NodeManager nm ^ArrayManager am shift node]
  (let [reg? (== branch-factor (.alength am (.array nm node)))
        len  (if reg? branch-factor branch-factor-plus-one)
        arr  (object-array len)
        rngs (if-not reg?
               (doto (int-array branch-factor-plus-one)
                 (aset 0 (.alength am (.array nm node)))
                 (aset branch-factor 1)))
        ret  (.node nm nil arr)]
    (loop [arr arr shift shift]
      (if (== shift log-branch-factor)
        (do (if-not reg?
              (aset arr branch-factor rngs))
            (aset arr 0 node))
        (let [a (object-array len)
              e (.node nm nil a)]
          (aset arr 0 e)
          (if-not reg?
            (aset arr branch-factor rngs))
          (recur a (- shift log-branch-factor)))))
    ret))

(defn fold-tail [^NodeManager nm ^ArrayManager am node shift cnt tail]
  (let [tlen     (.alength am tail)
        reg?     (and (.regular nm node) (== tlen branch-factor))
        arr      (.array nm node)
        li       (index-of-nil arr)
        new-arr  (object-array (if reg? branch-factor branch-factor-plus-one))
        rngs     (if-not (.regular nm node) (ranges nm node))
        cret     (if (== shift log-branch-factor)
                   (.node nm nil tail)
                   (fold-tail nm am
                              (aget ^objects arr (dec li))
                              (- shift log-branch-factor)
                              (if (.regular nm node)
                                (mod cnt (bit-shift-left 1 shift))
                                (let [li (unchecked-dec-int (aget rngs branch-factor))]
                                  (if (pos? li)
                                    (unchecked-subtract-int
                                     (aget rngs li)
                                     (aget rngs (unchecked-dec-int li)))
                                    (aget rngs 0))))
                              tail))
        new-rngs (ints (if-not reg?
                         (if rngs
                           (aclone rngs)
                           (regular-ranges shift cnt))))]
    (when-not (and (or (nil? cret) (== shift log-branch-factor)) (== li branch-factor))
      (System/arraycopy arr 0 new-arr 0 li)
      (when-not reg?
        (if (or (nil? cret) (== shift log-branch-factor))
          (do (aset new-rngs li
                    (+ (if (pos? li)
                         (aget new-rngs (dec li))
                         (int 0))
                       tlen))
              (aset new-rngs branch-factor (inc li)))
          (do (when (pos? li)
                (aset new-rngs (dec li)
                      (+ (aget new-rngs (dec li)) tlen)))
              (aset new-rngs branch-factor li))))
      (if-not reg?
        (aset new-arr branch-factor new-rngs))
      (if (nil? cret)
        (aset new-arr li
              (new-path nm am
                        (unchecked-subtract-int shift log-branch-factor)
                        (.node nm nil tail)))
        (aset new-arr (if (== shift log-branch-factor) li (dec li)) cret))
      (.node nm nil new-arr))))
