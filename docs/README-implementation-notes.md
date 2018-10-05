Call tree:

Defined in namespace: clojure.core.rrb-vector.nodes

defmacro mk-am
definline object
(def empty-pv-node PersistentVector/EMPTY_NODE)
(def empty-gvec-node clojure.core/EMPTY-NODE)
(definterface NodeManager
  ;; ...

  ;; TBD: What is method regular for?
)
(def object-nm
  (reify NodeManager
    ;; ...
  ))
(def primitive-nm
  (reify NodeManager
    ;; ...
  ))
(defmacro ranges [nm node]
  ;; ...
  )
(defn last-range [^NodeManager nm node]
  ;; ...
  ;; aget
  ;; ranges
  )
(defn regular-ranges [shift cnt]
  ;; ...
  ;; bit-shift-left, int-array, aset, unchecked-inc-int, unchecked-add-int
  )
(defn overflow? [^NodeManager nm root shift cnt]
  ;; root overflow
  ;; bit-shift-right unchecked-inc-int bit-shift-left aget
  ;; .regular
  ;; ranges
  )

(defn index-of-0 ^long [arr]
  ;; 
  )
