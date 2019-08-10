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

Clojure/Java source files in directory src/main/clojure/clojure/core:

* rrb_vector.clj
* rrb_vector/protocols.clj
* rrb_vector/interop.clj
* rrb_vector/rrbt.clj
* rrb_vector/nodes.clj
* rrb_vector/fork_join.clj
* rrb_vector/transients.clj
* rrb_vector/debug.clj

ClojureScript files in directory src/main/cljs/clojure/core:

* rrb_vector.cljs
* rrb_vector/debug.cljs
* rrb_vector/interop.cljs
* rrb_vector/transients.cljs
* rrb_vector/protocols.cljs
* rrb_vector/nodes.cljs
* rrb_vector/macros.clj
* rrb_vector/trees.cljs
* rrb_vector/rrbt.cljs

Test files:

* src/test/clojure/clojure/core/rrb_vector_test.clj
* src/test/cljs/clojure/core/rrb_vector_test.cljs
* src/test_local/clojure/clojure/core/rrb_vector_check.clj

clojure.core/pop! calls method .pop
clojure.core/transient calls method .asTransient


Call tree:

Defined in namespace: clojure.core.rrb-vector.nodes

```
defmacro mk-am
definline object
(def empty-pv-node PersistentVector/EMPTY_NODE)
(def empty-gvec-node clojure.core/EMPTY-NODE)
(definterface NodeManager
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
```

Defined in namespace: clojure.core.rrb-vector.rrbt

```
defmacro assert
defmacro dbg
defmacro dbg-
defn throw-unsupported
defmacro compile-if
defmacro caching-hash
defn hash-gvec-seq
definterface IVecImpl - 7 lines
deftype VecSeq - 222 lines
defprotocol AsRRBT - 2 lines
defn slice-right - 62 lines
defn slice-left - 98 lines
splice-rrbts - 1 line
deftype Vector - 770 lines 491-1260
   pop method - 37 lines 689-725
   popTail method - 88 lines lines 903-990
extend-protocol AsRRBT - 23 lines
defn shift-from-to - 23 lines
defn pair - 4 lines
defn slot-count - 8 lines
defn subtree-branch-count - 17 lines
defn leaf-seq - 2 lines
defn rebalance-leaves - 58 lines
defn child-seq - 14 lines
defn rebalance - 84 lines
defn zippath - 35 lines
defn squash-nodes - 30 lines
defn splice-rrbts - 66 lines
defn array-copy - 9 lines
deftype Transient - 254 lines 1648-1901
   pop method - 55 lines line 1808-1862

     most complex case that handles a tail that had 1 element, and
       pop! leaves it with 0, so a new tail is created -- 34 lines

```

Defined in namespace: clojure.core.rrb-vector.transients

```
definterface ITransientHelper - 39 lines 15-53
def transient-helper - reify TransientHelper 240 lines 55-294
  popTail - 84 lines 154-237
```


# Nodes in the trees implementing vectors

Clojure's PersistentVector, implemented in Java, uses tree nodes with
class PersistentVector$Node.  They have only two fields, 'array' and
'edit'.

It is not clear to me why, but when primitive vectors were added to
Clojure, they used a new class clojure.core.VecNode for their tree
nodes.  They have only two fields, 'arr' and 'edit', used for the same
purposes as the similarly (but not identically!) named fields of
PersistentVector$Node.

In order to make it possible for core.rrb-vector to be passed either
of those kinds of vectors created by Clojure's core library, and reuse
their internal data structures, core.rrb-vector uses those same
classes for its internal tree nodes, using the class
PersistentVector$Node for vectors of arbitrary Object elements, or
clojure.core.VecNode for vectors of primitives, as Clojure does.

The NodeManager interface, and its 'object-nm' and 'primitive-nm'
implementations, helps to write common code that manipulates trees,
regardless of whether the nodes of the tree are type
PersistentVector$Node or clojure.core.VecNode.


# pushTail

This method is called as part of the implementation of these
operations:

* clojure.lang.IPersistentCollection cons - Appears to be called from
  clojure.lang.RT conj(IPersistentCollection coll, Object x) if coll
  is not null.  That can in turn be called from clojure.core/conj

* clojure.lang.ITransientCollection conj - called from
  clojure.core/conj!

clojure.lang.RT.cons(Object x, Object coll) does _not_ call the 'cons'
method, but creates a new Cons object, or a new PersistentList object.



# Idea for somewhat better post-function call checks

The checks I have that call several functions in the debug namespace,
that I use with-redefs to create 'wrapped' versions of functions like
catvec, vector, and pop that check the return value to see if there
are any problems with them, could be extended to also check whether
the return value is correct relative to the input parameters of the
function.  Implementing that should be straightforward, and simply
requires a well-known argument to tell the wrapper what the correct
relationship should be.  For example, it could calculate whether
catvec returns the correct value by comparing `(concat (seq v1) (seq
v2))` to `(seq ret)`.

For transients, this approach might require ensuring that `seq`, or at
least some debug-only version of `seq`, works for transient vectors.

It would be nice to figure out a way to let users of core.rrb-vector
to enable this extra checking, too.

It seems that any atoms or other similar things I use to record sanity
check failures should be in the debug namespace, so that they can be
used by requiring the debug namespace only, without having to load any
test namespace.  Those facilities can be useful even if no test cases
are being run.
