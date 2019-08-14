(ns clojure.core.rrb-vector.clj-macros
  (:refer-clojure :exclude [assert]))

(def ^:const elide-assertions? true)
(def ^:const elide-debug-printouts? true)

(defmacro assert [& args]
  (if-not elide-assertions?
    (apply #'clojure.core/assert &form &env args)))

(defmacro dbg [& args]
  (if-not elide-debug-printouts?
    `(prn ~@args)))

(defmacro dbg- [& args])

(defmacro max-tree-capacity-elems [shift]
  `(bit-shift-left 1 (unchecked-add-int (int ~shift) (int 5))))
