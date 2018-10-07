# Information sources about RRB-Trees

The [original paper](https://infoscience.epfl.ch/record/169879/files/RMTrees.pdf):
"RRB-Trees: Efficient Immutable Vectors", Phil Bagwell, Tiark Rompf,
March 2012

This talk by Phil Bagwell is specifically on RRB-Trees:

[Talk](https://www.youtube.com/watch?v=K2NYwP90bNs&index=5&list=PLZdCLR02grLo2QltND1rpy8EA7gbopCIH):
"Striving to Make Things Simple and Fast", Phil Bagwell, Clojure Conj
conference, November 10-12, 2011

This talk by Tiark Rompf is also focused on RRB-Trees:

[Talk](https://skillsmatter.com/skillscasts/3290-fast-concatenation-immutable-vectors):
"Fast Concatenation for Immutable Vectors", Tiark Rompf, Scala Days
conference, April 17, 2012

This talk by Daniel Spiewak is on other functional data structures,
including finger trees, which have similarities to RRB-Trees.

[Talk](https://www.youtube.com/watch?v=pNhBQJN44YQ&list=PLZdCLR02grLo2QltND1rpy8EA7gbopCIH&index=18)
"Extreme Cleverness: Functional Data Structures in Scala", Daniel
Spiewak, Clojure Conj conference, November 10-12, 2011

[Prototype implementation](https://github.com/TiarkRompf/rrbtrees)
of RRB-Trees written in Scala in 2012 by Tiark Rompf, co-author with
Phil Bagwell of the paper on RRB-Trees.

This might be [Scala's production
implementation](https://github.com/scala/scala/blob/2.12.x/src/library/scala/collection/immutable/Vector.scala)

[Paguro library](https://github.com/GlenKPeterson/Paguro) which
includes a Java implementation of RRB-Trees, as well as some other
data structures either inspired by, or copied from, Clojure's
persistent data structures.

Some [questions and
discussion](https://stackoverflow.com/questions/14007153/what-invariant-do-rrb-trees-maintain)
about invariants maintained by RRB-trees from StackOverflow.

Jean Niklas L'orange's [Master's thesis](https://hypirion.com/thesis.pdf):
"Improving RRB-Tree Performance through Transience", Jean Niklas
L'orange, Master's Thesis, 2014

Jean Niklas L'orange's series of blog posts on Clojure persistent
vector implementation.

+ [The basic algorithms](https://hypirion.com/musings/understanding-persistent-vector-pt-1)
+ [Indexing](https://hypirion.com/musings/understanding-persistent-vector-pt-2)
+ [The tail optimisation](https://hypirion.com/musings/understanding-persistent-vector-pt-3)
+ [Transients](https://hypirion.com/musings/understanding-clojure-transients)
+ [Performance](https://hypirion.com/musings/persistent-vector-performance-summarised)
  + [Detailed version of performance article](https://hypirion.com/musings/persistent-vector-performance)


Clojure's [core.rrb-vector](https://github.com/clojure/core.rrb-vector)
source code.

Note: Several people have [public Github
forks](https://github.com/clojure/core.rrb-vector/network)
of this repository which can be browsed on Github.  It looks like
several have worked on proposed bug fixes.

[JIRA bug tracker](https://dev.clojure.org/jira/browse/CRRBV) for the
Clojure core.rrb-vector library.

2013
[announcement](https://groups.google.com/forum/#!msg/clojure/Z7wtm2Lepj0/YBgiRzqCiKIJ)
of core.rrb-vector Clojure library, with some background info on the
implementation and some follow up discussion.

Zach Tellman's Bifurcan library of impure functional data structures
+ [Announcement](https://groups.google.com/d/msg/clojure/1m_I7IrDGb0/6Tb4rFvcBwAJ)
+ [Code](https://github.com/lacuna/bifurcan)


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
