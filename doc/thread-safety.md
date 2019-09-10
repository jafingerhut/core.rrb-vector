# Clojure, and synchronization between threads in the JVM

This article discusses the implementation of Clojure/Java data
structures.  Unlike most such articles, it is not on how the data
structures work and implement vectors, maps, sets, etc.

Instead, we focus on how Clojure implements immutability in a
thread-safe way, and on the few mutable Clojure data structures, such
as transient collections, or other internal mutable state, such as the
implementation of some transducers.  In particular, what
synchronization do they require outside of the Clojure implementation
in order to use them safely in a multi-threaded program?

The following quote from the book "Java Concurrency in Practice" is a
good introduction to what the word "synchronization" means in this
context:

> In a single-threaded environment, if you write a value to a variable
> and later read that variable with no intervening writes, you can
> expect to get the same value back.  This seems only natural.  It may
> be hard to accept at first, but when the reads and writes occur in
> different threads, _this is simply not the case_.  In general, there
> is _no_ guarantee that the reading thread will see a value written
> by another thread on a timely basis, or even at all.  In order to
> ensure visibility of memory writes across threads, you must use
> synchronization.
> 
> -- "Java Concurrency in Practice", Section 3.1 "Visibility"

See Note 1 for some notes about ClojureScript, which is not discussed
here except for that note.


# References

Abbrevations:

* _CEJMMK_: Close Encounters of the Java Memory Model Kind
* _JLS_: Java Language Specification
* _JMM_: Java Memory Model
* _JCIP_: the book "Java Concurrency in Practice"

Some references that go into depth on the Java Memory Model.

* Section 17.4 "Memory Model",
  https://docs.oracle.com/javase/specs/jls/se11/html/jls-17.html
  * Part of "The Java Language Specification: Java SE 11", James
    Gosling, Bill Joy, Guy Steele, Gilad Bracha, Alex Buckley, Daniel
    Smith, 2018-08-21,
    https://docs.oracle.com/javase/specs/jls/se11/html/index.html
* "Java Concurrency in Practice", Brian Goetz, with Tim Peierls,
  Joshua Bloch, Joseph Bowbeer, David Holmes, and Doug Lea,
  Addison-Wesley, 2006
* "Close Encounters of the Java Memory Model Kind", Aleksey Shipilev,
  2016, https://shipilev.net/blog/2016/close-encounters-of-jmm-kind/

Warning: The specification of the Java Memory Model is fairly
math-intensive and easily misinterpreted.  If you think you understand
it, consider reading "Close Encounters of the Java Memory Model Kind"
and see if you find any surprises.

In particular, the name of the "happens before" relation can be
misleading: "A happens-before B" does not mean that a machine must
execute A before B.  For example, "A happens-before B" is true for any
two statements A and B executed by a single thread, but if A and B are
assignments to normal fields of a class with no special modifiers
(e.g. no final, volatile, etc.), then a compiler in most cases is free
to execute those statements in either order.

Most of the JCIP book is advice on how to write programs safely in
Java using the synchronization mechanisms available, and classes that
use those mechanisms.  While there is not extensive discussion of the
Java Memory Model in the book, Chapter 16 does have some, including
how the earlier advice in the book is related to it.


# Clojure persistent data structures

Many of Clojure's data structures are immutable.  To make instances of
the Java classes that implement these immutable data structures safe
for use across multiple threads, they often make heavy use of Java
`final` fields, and initialize these fields in the constructor of the
object.

As one example, the class `clojure.lang.PersistentVector` has instance
variables (i.e. non-static fields) `cnt`, `shift`, `root`, `tail`,
`_meta`, all declared `final`.  The class has only two constructors,
and both of them initialize all of these fields.

Some of those fields are primitive Java types, e.g. `cnt` and `shift`
are `int`, and the Java language does not allow those fields to be
modified after the constructor is completed (except in some situations
involving Java deserialization that I am not addressing here).

But `tail` has type `Object[]`, an array of objects.  The `final`
declaration for `tail` means that after the constructor is complete,
we cannot modify the reference `tail` to point to a different object
array (nor can we change `tail` to null), but we can still modify the
elements of the object array that `tail` points to.  Is this
immutable?  Is this thread safe?


## Immutability

First, is this immutable?  In terms of Java language rules, all arrays
are mutable, so in that sense instances of the `PersistentVector`
class are mutable.  Here is a demonstration in a Clojure REPL:

```
$ clj
Clojure 1.10.1
user=> (def v1 [1 2 3])
#'user/v1
user=> (class v1)
clojure.lang.PersistentVector
user=> (.tail v1)
#object["[Ljava.lang.Object;" 0x2adddc06 "[Ljava.lang.Object;@2adddc06"]
user=> (seq (.tail v1))
(1 2 3)
user=> v1
[1 2 3]
user=> (aset (.tail v1) 1 -2)
-2
user=> v1
[1 -2 3]      ;; Wait ...  we just mutated an "immutable" vector?!?
```

However, I have never seen a Clojure program that accesses the fields
of a `PersistentVector` like this.

Clojure promises this: If you restrict yourself to accessing vectors
via the Clojure functions provided for manipulating them, _those
functions_ will not mutate the vectors.

And that is a pretty easy restriction to live with.  In Clojure, you
must go out of your way to play tricks like the one above.  See Note 2
for a few more nit-picky details, if you are curious.


## Thread safety

For this section, assume the common case in Clojure, where we do not
play the tricks described in the previous section (see the section
"Clojure mutable data structures" below if you are curious what
happens if we do play such tricks in a multi-threaded program).

If one thread creates a Clojure immutable collection, can other
threads that read it see a different view of its contents than the
thread that created it?

So far, I have failed to understand the JLS mathematical definition of
the behavior of final fields and their synchronization between
threads, but the following description I will, for now, assume
accurately captures the intent:

> Set the final fields for an object in that object's constructor; and
> do not write a reference to the object being constructed in a place
> where another thread can see it before the object's constructor is
> finished.  If this is followed, then when the object is seen by
> another thread, that thread will always see the correctly
> constructed version of that object's final fields.  It will also see
> versions of any object or array referenced by those final fields
> that are at least as up-to-date as the final fields are.
> 
> -- Java Language Specification, Section 17.5 "final Field Semantics"

Note the last sentence in particular.  A common pattern in Clojure
code for these immutable collections is a sequence like this:

1. Allocate an array A.  Initialize its contents completely.
2. Call a constructor and pass a reference to A as a parameter.  The
   constructor code assigns A to a final field of the constructed
   object.
3. The constructor completes and returns the new collection C.
4. Never write to any element of array A ever again.

Because of the last sentence in the JLS quote above, all of the writes
to the array A in step 1 are visible to all other threads that read
collection C, even if no further synchronization steps are taken.

Not only that, but step 1 could be replaced with writing to an
arbitrary collection of fields in an arbitrary set of Java objects,
all reachable through following one or more references from an object
X.  It does not matter whether those fields are declared final or not.
As long as C's constructor writes a reference to X into a final field
of object C, then all of those writes are guaranteed to be visible to
any thread that reads C.

See Note 3 if you are curious about the non-final fields in Clojure
collections used to store a hash value calculated from the collection
contents.


# Clojure mutable data structures

You may reasonably ask: "What Clojure mutable data structures?  Aren't
they all immutable?"

While the most commonly used Clojure data structures are immutable in
the sense described above, there are a few that are mutable in that
sense.  For example, transient collections such as `(transient [])`
have a `conj!` operation provided by Clojure that typically does
mutate the object.  Also, one design goal of Clojure was to have
straightforward interoperation with the JVM, so it is straightforward
to access all of the many Java libraries that have been written, of
which many use mutable data structures.

There are several ways to make changes to a mutable data structure C
in thread T1, then later guarantee that a different thread T2 sees all
of those writes.

1. Only modify fields "owned" by the object within code that first
   acquires, and then releases the object's lock, e.g. a Java
   non-static method declared with the `synchronized` modifier.
   Ownership here is not something that is defined within the Java
   language itself, but based practices and conventions created by
   Java program writers.
2. T1 modifies fields owned by the object C, then stops making
   modifications, and performs a "release" synchronization action
   (examples below).  T2 makes no modifications to C, then performs a
   corresponding "acquire" synchronization action.  At that point T2
   should be able to see all updates made to C by T1.

Examples of corresponding "release" and "acquire" actions that T1 and
T2 could perform:

1. T1 writes a reference to C into a field `F` declared `volatile`,
   and T2 reads the reference from the same field `F`.  In this case
   we somehow need to guarantee that T2's read of `F` is after T1's
   write -- more on that below at "Notes on causality".
2. T1 calls `A.set(C)` for an object `A` with type
   [`AtomicReference`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicReference.html),
   and T2 reads the reference by calling `A.get()` for the same object
   `A`.  The same "Notes on causality" applies for this technique.
3. T1 calls `Q.put(C)` for an object `Q` with type
   [`ArrayBlockingQueue`](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/ArrayBlockingQueue.html),
   and T2 reads the reference by calling `Q.take()` for the same
   object `Q`.
4. T1 writes a reference to C into any field `F`, even one without
   `volatile` or `final` modifiers, then later unlocks a lock L.  T2
   then requests lock L, and at some point in time gets it, and reads
   `F` afterwards.

There are many Java classes that provide such synchronization
guarantees other than the two examples listed above -- an exhaustive
list would be too long, and probably incomplete by the time it was
created, given that anyone can write such a class.

Any kind of object, immutable or mutable, may be passed from one
thread to another using any of the techniques above.  There is no harm
in using those techniques for immutable objects, but it is
unnecessary.  Any thread may read a reference to an immutable object
in any way whatsoever, e.g. from a field that has been declared
neither `final` nor `volatile`, and without performing any of the
synchronization actions above, and the thread is still gauranteed to
see the correct immutable object contents.


### Notes on causality

`ArrayBlockingQueue` and all other implementations of the
`BlockingQueue` interface guarantee that corresponding `take` and
`put` actions are such that the `take` happens after the `put`, so
nothing more is needed for T2 to know that it has all the latest
updates to C made by T1.

> Memory consistency effects: As with other concurrent collections,
> actions in a thread prior to placing an object into a BlockingQueue
> happen-before actions subsequent to the access or removal of that
> element from the BlockingQueue in another thread.

[Java doc
reference](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/BlockingQueue.html)

Aside: I am assuming here no funny business, such as T1 `put`ing C
into a queue, making further modifications to C, then `put`ing C
again.  That would violate the rule above that T1 no longer makes
modifications to C after the first `put`.

But what about the technique of T1 writing to a `volatile` field `F`,
then T2 reading from a `volatile` field `F`?  If we do nothing else to
synchronize these actions between the threads, it is possible that
T2's read of `F` could get whatever value `F` contained before T1
wrote to `F`.

Is there any guarantee at all if thread T1 knew that it wrote to field
`F` "before 4:30 PM Tuesday", and thread T2 knew that it read from
field `F` "after 4:31 PM Tuesday" (same Tuesday and time zone), that
T2 would see T1's write of `F`?  If so, how is that guaranteed?  If
not, is there any later time T2 could wait until that would guarantee
it?  Maybe this could be guaranteed if there was a time keeper thread
that used a volatile to store the current time, and periodically read
that value, calculated the next time, and wrote the updated time?



# Clojure transients and thread safety

Summary: It seems definitely possible to create a transient Clojure
collection, at least a transient vector and perhaps others, where
after doing a mutation on it, e.g. assoc!, the returned transient
collection is identical to the one passed in.

If you pass a reference to that transient object to another thread,
even in a correctly synchronized way, and then operating in only one
thread at a time the original thread mutates it some more, then the
next thread examines it, the second thread is _not_ guaranteed to see
all changes to it according to what I know about the Java Memory Model
rules.  This is because the modifications made by the original thread
only change Java array element values, but no volatile fields, and no
new objects with final fields are created, and no synchronized method
calls are involved.

I tried to create an experiment of this, hoping that over many many
executions it might demonstrate the unsafety of doing this, but it
never produced unexpected results.  That does not make it safe, of
course, just not as much of an eye opener without the demonstration of
unsafety.

Thus it seems that even with the volatile fields that exist in the
Clojure transient implementation, it requires the usual careful
synchronization to pass a transient from one thread to another,
_after_ the first thread has stopped making changes.

core.async puts all local values, e.g. let/loop values bound to
symbols, into AtomicReferenceArray elements when parking a thread, and
reads them from there when restoring them.  That is one form of
correctly synchronizing the value between threads.


Possible experiment to see if Clojure's transient variant of its
PersistentVector is actually safe to pass from one thread to another:

* Create a transient vector t1 that has at least 33 elements, so it
  has at least one tree node, and not only elements in its tail.
* Use assoc! to modify t1 in an element of the tree, i.e. any vector
  index from 0 to 31 inclusive.  I believe this assoc! call will
  return a new root object t2 that is not identical to t1, because the
  assoc! must mark the tree node as "owned" by this transient object,
  when it was not owned by t1.
* Use assoc! to modify t2 in a different element, but also in the
  tree.  I believe this call should always return t3 which is
  identical to t2, and the edited tree node will be identical to the
  edited tree node in t2.  That is *all* objects in t3 should be
  identical to those in t2, except for the vector element that was
  assoc!'d in this last step.

Confirmed by experiment: It is true that t2 and t3 are identical, and
that the only mutations made are to one element of the Object array
required to perform the last assoc! operation.

As a result, it seems to me like this mutation is only guaranteed to
be visible to another thread B, different than the thread A that made
all of the calls listed above, if A and B properly synchronize the
reference t3 from one thread to the next.

From the results of that experiment, it seems clear to me that taking
a transient mutable object that has been mutated, and passing it to
another thread, requires explicit synchronization in passing that
reference, or else the receiving thread has no guarantees whether it
gets up to date values in the Java arrays.  I cannot see how it helps
to have the volatile fields in the nodes _at all_, other than to make
it somewhat more difficult to demonstrate running code examples that
exhibit the problem.


Possible experiment:

Thread T1 creates a PV with 100 elements, converts to transient, does
a few hundred assoc! calls, at least once on all indexes from 0 thru
95 inclusive, then passes a reference to the transient with good
synchronization, e.g. some queue class that synchronizes the
references appended to it to the dequeueing thread
(ConcurrentBlockingQueue? TBD).

Then the receiving thread T2 immediately calls persistent! on it and
reads all elements.  They should all be the last versions written,
because of the synchronization of the queue used to pass the reference
from T1 to T2.


Slight modification that should be bad for getting all data reliably
from T1 to T2:

Start the same as above, except after T1 sends the reference of the
transient in the queue, then it does a bunch more assoc! calls (but no
others, so the base object should remain identical to what was sent to
T2 -- we can check that it remains identical in T1).

Then somehow T1 sends some _other_ kind of causal signal from T1 to T2
that T2 can continue with doing persistent! on the reference it
received.  This could perhaps be a network request to a different
process, which responds with some message/signal that T2 handles, and
then does the persistent! call and the rest when it receives that
signal.

Other ways besides a network request:

T1 creates a file in the file system with a particular path, one that
did not exist before the program started running.  T2 polls
periodically looking for the file to exist, continuing when it does
exist.

T1 writes to a "global variable" that has no synchronization, e.g. not
volatile, not synchronized method to write it, no locks.  T2 reads
from the global variable to see when it changes, again no volatile,
synchronized, or locks involved.  It is not guaranteed that T2 will
_ever_ see the change, but it might, and if it does, it might see that
change before it sees the changes to the transient object.

I tried an experiment like this with several variations, and was not
able to see a stale read result in thread T2 over hundreds of
thousands of trials.

One group of experiments used creation of a file as a 'signal' from T1
to T2.

Another used a Clojure deftype ^:unsynchronized-mutable field value
change.


# Do any Clojure transducers use transient collections in their internal state?

The following transducer implementations in clojure.core have no uses
of volatile, and no transients, and as far as I can see, appear
completely stateless.

* halt-when
* map
* mapcat
* cat
* filter
* remove
* take-while
* replace - implementation uses map transducer
* keep


Uses a single volatile value as internal state, but no transients

* take - volatile state is one integer
* drop - volatile state is one integer
* drop-while - volatile state is one value, which is either true or nil
* take-nth - volatile state is one integer
* keep-indexed - volatile state is one integer
* map-indexed - volatile state is one integer

* distinct - volatile state is one persistent Clojure set, with no
  transient code involved, only persistent operations performed on the
  set.
* interpose - volatile state is one boolean
* dedupe - volatile state is an element from the input sequence, so
  could be mutable, which could be bad not only for synchronization
  between threads, but also for equality semantics.

Uses internal state that contains mutable data, maybe without proper
synchronization if transducer moves between threads?

* partition-by - java.util.ArrayList named a has no synchronization
  guarantees in its implementation.  The Java API docs say that add
  method calls are not synchronized, for example.
  https://docs.oracle.com/javase/8/docs/api/java/util/ArrayList.html
* partition-all - similar to partition-by, contains
  java.util.ArrayList instance in its internal state, on which it
  performs operations such as the add method.

Special case?

* random-sample - does not explicitly use any state, but does call
  (rand), which has internal state.  Is it thread safe to call rand
  from arbitrary threads?

The Java API docs for method Math/random that clojure.core/random-sample uses (inside the implementation of clojure.core/rand), say:

This method is properly synchronized to allow correct use by more than
one thread. However, if many threads need to generate pseudorandom
numbers at a great rate, it may reduce contention for each thread to
have its own pseudorandom-number generator.

https://docs.oracle.com/javase/8/docs/api/java/lang/Math.html

So it looks correct for use across threads, although perhaps not as
high performance as something that used a thread local random number
generator, although that would be somewhat weird if the transducer
bounced from thread to thread across executions, for elements on a
single sequence.


# Footnotes

Note 1:

This article does not attempt to cover ClojureScript.  I believe that
almost all ClojureScript programs are single threaded, and for such
programs these issues do not arise, just as they do not arise in a
single threaded Clojure/Java program.

Some JavaScript runtime environments implement Web Workers, and shared
memory buffers between them (e.g. see
https://2ality.com/2017/01/shared-array-buffer.html), but I am not
aware of any support for ClojureScript to store Clojure collections in
such shared buffers.  If such a feature is ever implemented, there
would likely be similar issues that arise there, although the
"JavaScript memory model" rules are likely to be different than
Java's.


Note 2:

Some people wonder why Clojure's implementation does not make use of
the `private` field annotation for more fields, to hide their internal
fields and make the immutability of these data structures "safer".

While that could be done, there are Java reflection APIs and methods
like
[`setAccessible`](https://docs.oracle.com/javase/8/docs/api/java/lang/reflect/AccessibleObject.html#setAccessible-boolean-)
that make it straightforward to modify `private` fields, too, at least
with the default security manager configuration used by the JVM.  Thus
`private` fields are not much safer.  They simply require that someone
write a couple more lines of code in order to play such tricks.

If you want to go a bit further, even in a programming language like
Haskell, its data structures can be mutated if you run certain
programs with administrator/super-user privileges on a host that can
write to arbitrary addresses in any process's memory.  There are many
such programs used for debugging, and for [cheating at computer
games](https://www.cheatengine.org/aboutce.php).  Immutability of data
is a convention, but one that is enforced just slightly more strongly
in Haskell than in Clojure.  It is not merely non-idiomatic to write
code like the example above in Clojure -- most Clojure developers
would likely consider it *malicious* code.


Note 3:

Many Clojure collection implementations contain fields to store a
calculation of the 32-bit type int hash of the collection's contents.
None of these fields are declared final, so the above does not apply.
From a post to the Google Clojure group by Alex Miller 2014-May-06:

> Hash codes are a special case - the pattern they use is sometimes
> called the "racy single-check idiom" and is discussed in Effective
> Java in Item 71 or on an internet near you.  The canonical example
> of this is java.lang.String.  The trick is that if you don't have a
> non-0 hash code, compute it yourself and stash it.  If threads
> happen to see it, they can use it!  If they don't see it, they
> compute it themselves.  If two threads race, they write the *same*
> value, so everyone is fine.  One important aspect is that the hash
> code must be an int which can be written atomically; if it was a
> long, you'd potentially be subject to long tearing.

The article ["Data-Race-Ful Lazy Initialization for
Performance"](http://jeremymanson.blogspot.com/2008/12/benign-data-races-in-java.html)
by Jeremy Manson, 2008-Dec-14, goes into a few more details.

Aside: Clojure vectors can contain elements that are mutable objects,
and for most uses it is not obvious that anything goes wrong when one
does this.  However, these vector elements have at least two potential problems:

1. If such mutable objects are modified by one thread, then some
   proper synchronization technique must be used in order for another
   thread to read the update to date contents.  This is not unique to
   mutable objects inside of Clojure vectors, but all mutable objects
   in general.
2. Two threads could calculate inconsistent values for the hash of the
   object, and thus inconsistent values for the hash of a Clojure
   vector containing the object.

The same comments apply for Clojure maps with values (not keys) that
are mutable objects.

Of course, calculating inconsistent hashes for objects is even worse
for hash-based collections like keys in Clojure maps, or elements of
sets, since the 'same' object will most likely be impossible to be
looked up or removed.
