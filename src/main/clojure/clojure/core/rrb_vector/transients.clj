(ns clojure.core.rrb-vector.transients
  (:require [clojure.core.rrb-vector.nodes
             :refer [ranges last-range dbgln int-array?
                     branch-factor branch-factor-plus-one
                     branch-factor-minus-one branch-factor-squared
                     log-branch-factor branch-factor-bitmask
                     ]])
  (:import (clojure.core.rrb_vector.nodes NodeManager)
           (clojure.core ArrayManager)
           (java.util.concurrent.atomic AtomicReference)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(definterface ITransientHelper
  (editableRoot [^clojure.core.rrb_vector.nodes.NodeManager nm
                 ^clojure.core.ArrayManager am
                 root])
  (editableTail [^clojure.core.ArrayManager am
                 tail])
  (ensureEditable [^clojure.core.rrb_vector.nodes.NodeManager nm
                   root])
  (ensureEditable [^clojure.core.rrb_vector.nodes.NodeManager nm
                   ^clojure.core.ArrayManager am
                   ^java.util.concurrent.atomic.AtomicReference root-edit
                   current-node
                   ^int shift])
  (pushTail [^clojure.core.rrb_vector.nodes.NodeManager nm
             ^clojure.core.ArrayManager am
             ^int shift
             ^int cnt
             ^java.util.concurrent.atomic.AtomicReference root-edit
             current-node
             tail-node])
  (popTail [^clojure.core.rrb_vector.nodes.NodeManager nm
            ^clojure.core.ArrayManager am
            ^int shift
            ^int cnt
            ^java.util.concurrent.atomic.AtomicReference root-edit
            current-node])
  (doAssoc [^clojure.core.rrb_vector.nodes.NodeManager nm
            ^clojure.core.ArrayManager am
            ^int shift
            ^java.util.concurrent.atomic.AtomicReference root-edit
            current-node
            ^int i
            val])
  (newPath [^clojure.core.rrb_vector.nodes.NodeManager nm
            ^clojure.core.ArrayManager am
            tail
            ^java.util.concurrent.atomic.AtomicReference edit
            ^int shift
            current-node]))

(def ^ITransientHelper transient-helper
  (reify ITransientHelper
    (editableRoot [this nm am root]
      (.node nm
             (AtomicReference. (Thread/currentThread))
             (clojure.core/aclone ^objects (.array nm root))))

    (editableTail [this am tail]
      (let [ret (.array am branch-factor)]
        (System/arraycopy tail 0 ret 0 (.alength am tail))
        ret))

    (ensureEditable [this nm root]
      (let [owner (->> root (.edit nm) (.get))]
        (cond
          (identical? owner (Thread/currentThread))
          nil

          (not (nil? owner))
          (throw
           (IllegalAccessError. "Transient used by non-owner thread"))

          :else
          (throw
           (IllegalAccessError. "Transient used after persistent! call")))))

    (ensureEditable [this nm am root-edit current-node shift]
      (if (identical? root-edit (.edit nm current-node))
        current-node
        (if (zero? shift)
          (let [new-arr (.aclone am (.array nm current-node))]
            (.node nm root-edit new-arr))
          (let [new-arr (aclone ^objects (.array nm current-node))]
            (if (== branch-factor-plus-one (alength ^objects new-arr))
              (aset new-arr branch-factor (aclone (ints (aget ^objects new-arr branch-factor)))))
            (.node nm root-edit new-arr)))))

    ;; Note 1: See the code in namespace
    ;; clojure.core.rrb-failing-tests, deftest
    ;; many-subvec-and-catvec-leads-to-exception, for a way to
    ;; reproduce the condition that leads to the error message below,
    ;; repeatably.  Shortly after the error message is printed, if you
    ;; do anything that does a seq over the resulting data structure,
    ;; or probably many other operations, it throws an exception
    ;; because the place where the code expects to find a Java array
    ;; of ints, it instead finds a NodeVec object.
    (pushTail [this nm am shift cnt root-edit current-node tail-node]
      (let [ret (.ensureEditable this nm am root-edit current-node shift)]
        (if (.regular nm ret)
          (do (loop [n ret shift shift]
                (let [arr    (.array nm n)
                      subidx (bit-and (bit-shift-right (dec cnt) shift) branch-factor-bitmask)]
                  (if (== shift log-branch-factor)
                    (aset ^objects arr subidx tail-node)
                    (let [child (aget ^objects arr subidx)]
                      (if (nil? child)
                        (aset ^objects arr subidx
                              (.newPath this nm am
                                        (.array nm tail-node)
                                        root-edit
                                        (unchecked-subtract-int shift log-branch-factor)
                                        tail-node))
                        (let [editable-child
                              (.ensureEditable this nm am
                                               root-edit
                                               child
                                               (unchecked-subtract-int
                                                shift log-branch-factor))]
                          (aset ^objects arr subidx editable-child)
                          (recur editable-child (- shift log-branch-factor))))))))
              ret)
          (let [arr  (.array nm ret)
                rngs (ranges nm ret)
                li   (unchecked-dec-int (aget rngs branch-factor))
                cret (if (== shift log-branch-factor)
                       nil
                       (let [child (.ensureEditable this nm am
                                                    root-edit
                                                    (aget ^objects arr li)
                                                    (unchecked-subtract-int
                                                     shift log-branch-factor))
                             ccnt  (if (pos? li)
                                     (unchecked-subtract-int
                                      (aget rngs li)
                                      (aget rngs (unchecked-dec-int li)))
                                     (aget rngs 0))]
                         (if-not (== ccnt (bit-shift-left 1 shift))
                           (.pushTail this nm am
                                      (unchecked-subtract-int shift log-branch-factor)
                                      (unchecked-inc-int ccnt)
                                      root-edit
                                      child
                                      tail-node))))]
            (if cret
              (do (aset ^objects arr li cret)
                  (aset rngs li (unchecked-add-int (aget rngs li) branch-factor))
                  ret)
              (do (when (>= li branch-factor-minus-one)
                    ;; See Note 1
                    (let [msg (str "Assigning index " (inc li) " of vector"
                                   " object array to become a node, when that"
                                   " index should only be used for storing"
                                   " range arrays.")
                          data {:shift shift, :cnd cnt,
                                :current-node current-node,
                                :tail-node tail-node, :rngs rngs, :li li,
                                :cret cret}]
                      (throw (ex-info msg data))))
                  (aset ^objects arr (inc li)
                        (.newPath this nm am
                                  (.array nm tail-node)
                                  root-edit
                                  (unchecked-subtract-int shift log-branch-factor)
                                  tail-node))
                  (aset rngs (unchecked-inc-int li)
                        (unchecked-add-int (aget rngs li) branch-factor))
                  (aset rngs branch-factor (unchecked-inc-int (aget rngs branch-factor)))
                  ret))))))

    (popTail [this nm am shift cnt root-edit current-node]
      (let [ret (.ensureEditable this nm am root-edit current-node shift)]
        (if (.regular nm ret)
          (let [subidx (bit-and
                        (bit-shift-right (unchecked-dec-int cnt) shift)
                        branch-factor-bitmask)]
            (cond
              (> shift log-branch-factor)
              (let [child (.popTail this nm am
                                    (unchecked-subtract-int shift log-branch-factor)
                                    cnt
                                    root-edit
                                    (aget ^objects (.array nm ret) subidx))]
                (if (and (nil? child) (zero? subidx))
                  nil
                  (let [arr (.array nm ret)]
                    (aset ^objects arr subidx child)
                    ret)))

              (zero? subidx)
              nil

              :else
              (let [arr (.array nm ret)]
                (aset ^objects arr subidx nil)
                ret)))
          (let [rngs   (ranges nm ret)
                subidx (bit-and
                        (bit-shift-right (unchecked-dec-int cnt) shift)
                        branch-factor-bitmask)
                subidx (loop [subidx subidx]
                         (if (or (zero? (aget rngs (unchecked-inc-int subidx)))
                                 (== subidx branch-factor-minus-one))
                           subidx
                           (recur (unchecked-inc-int subidx))))]
            (cond
              (> shift log-branch-factor)
              (let [child     (aget ^objects (.array nm ret) subidx)
                    child-cnt (if (zero? subidx)
                                (aget rngs 0)
                                (unchecked-subtract-int
                                 (aget rngs subidx)
                                 (aget rngs (unchecked-dec-int subidx))))
                    new-child (.popTail this nm am
                                        (unchecked-subtract-int subidx log-branch-factor)
                                        child-cnt
                                        root-edit
                                        child)]
                (cond
                  (and (nil? new-child) (zero? subidx))
                  nil

                  (.regular nm child)
                  (let [arr (.array nm ret)]
                    (aset rngs subidx
                          (unchecked-subtract-int (aget rngs subidx) branch-factor))
                    (aset ^objects arr subidx new-child)
                    (if (nil? new-child)
                      (aset rngs branch-factor (unchecked-dec-int (aget rngs branch-factor))))
                    ret)

                  :else
                  (let [rng  (last-range nm child)
                        diff (unchecked-subtract-int
                              rng
                              (if new-child (last-range nm new-child) 0))
                        arr  (.array nm ret)]
                    (aset rngs subidx
                          (unchecked-subtract-int (aget rngs subidx) diff))
                    (aset ^objects arr subidx new-child)
                    (if (nil? new-child)
                      (aset rngs branch-factor (unchecked-dec-int (aget rngs branch-factor))))
                    ret)))

              (zero? subidx)
              nil

              :else
              (let [arr   (.array nm ret)
                    child (aget ^objects arr subidx)]
                (aset ^objects arr subidx nil)
                (aset rngs subidx 0)
                (aset rngs branch-factor     (unchecked-dec-int (aget rngs branch-factor)))
                ret))))))
    
    (doAssoc [this nm am shift root-edit current-node i val]
      (let [ret (.ensureEditable this nm am root-edit current-node shift)]
        (if (.regular nm ret)
          (loop [shift shift
                 node  ret]
            (if (zero? shift)
              (let [arr (.array nm node)]
                (.aset am arr (bit-and i branch-factor-bitmask) val))
              (let [arr    (.array nm node)
                    subidx (bit-and (bit-shift-right i shift) branch-factor-bitmask)
                    child  (.ensureEditable this nm am
                                            root-edit
                                            (aget ^objects arr subidx)
                                            shift)]
                (aset ^objects arr subidx child)
                (recur (unchecked-subtract-int shift log-branch-factor) child))))
          (let [arr    (.array nm ret)
                rngs   (ranges nm ret)
                subidx (bit-and (bit-shift-right i shift) branch-factor-bitmask)
                subidx (loop [subidx subidx]
                         (if (< i (aget rngs subidx))
                           subidx
                           (recur (unchecked-inc-int subidx))))
                i      (if (zero? subidx)
                         i
                         (unchecked-subtract-int
                          i (aget rngs (unchecked-dec-int subidx))))]
            (aset ^objects arr subidx
                  (.doAssoc this nm am
                            (unchecked-subtract-int shift log-branch-factor)
                            root-edit
                            (aget ^objects arr subidx)
                            i
                            val))))
        ret))

    (newPath [this nm am tail edit shift current-node]
      (if (== (.alength am tail) branch-factor)
        (loop [s 0 n current-node]
          (if (== s shift)
            n
            (let [arr (object-array branch-factor)
                  ret (.node nm edit arr)]
              (aset ^objects arr 0 n)
              (recur (unchecked-add s log-branch-factor) ret))))
        (loop [s 0 n current-node]
          (if (== s shift)
            n
            (let [arr  (object-array branch-factor-plus-one)
                  rngs (int-array branch-factor-plus-one)
                  ret  (.node nm edit arr)]
              (aset ^objects arr 0 n)
              (aset ^objects arr branch-factor rngs)
              (aset rngs branch-factor 1)
              (aset rngs 0 (.alength am tail))
              (recur (unchecked-add s log-branch-factor) ret))))))))
