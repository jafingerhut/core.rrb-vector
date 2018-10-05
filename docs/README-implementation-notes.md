# Information sources about RRB-Trees

The [original paper](https://infoscience.epfl.ch/record/169879/files/RMTrees.pdf):
"RRB-Trees: Efficient Immutable Vectors", Phil Bagwell, Tiark Rompf,
March 2012

[Prototype implementation](https://github.com/TiarkRompf/rrbtrees)
of RRB-Trees written in Scala in 2012 by Tiark Rompf, co-author with
Phil Bagwell of the paper on RRB-Trees.

Some [questions and
discussion](https://stackoverflow.com/questions/14007153/what-invariant-do-rrb-trees-maintain)
about invariants maintained by RRB-trees from StackOverflow.

Jean Niklas L'orange's [Master's thesis](https://hypirion.com/thesis.pdf):
"Improving RRB-Tree Performance through Transience", Jean Niklas
L'orange, Master's Thesis, 2014

Clojure's [core.rrb-vector](https://github.com/clojure/core.rrb-vector)
source code.

Note: Several people have [public Github
forks](https://github.com/clojure/core.rrb-vector/network)
of this repository which can be browsed on Github.  It looks like
several have worked on proposed bug fixes.

[JIRA bug tracker](https://dev.clojure.org/jira/browse/CRRBV) for the
Clojure core.rrb-vector library.


# Some notes on details of core.rrb-vector implementation

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
