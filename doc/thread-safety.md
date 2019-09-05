
https://clojure.atlassian.net/browse/CLJ-2090
"Improve clojure.core/distinct perf by using transient set"
Ticket opened by Nikita Propokov
Some comments from 2017 about thread safety in transducers and transients.


https://clojure.atlassian.net/browse/CLJ-2146
"partition-by and partition-all transducers should ensure visibility of state changes"
Ticket opened by Alex Miller.
One of the comments links to the Clojure Google group discussion below.


Clojure Google group thread:
"Using transducers in a new transducing context"

https://groups.google.com/forum/m/#!topic/clojure/VQj0E9TJWYY


Clojure Google group thread:
"Immutable or Effectively Immutable?"
Started by Mike Fikes.  I got involved, and Alex Miller and a few
others, even Stu Halloway chimed in once.
https://groups.google.com/forum/#!searchin/clojure/transient$20thread$20safety%7Csort:date/clojure/wuqww92le8Y/qb-lTuqPeHMJ

One of Alex Miller's messages links to his article below.


https://puredanger.github.io/tech.puredanger.com/2008/11/26/jmm-and-final-field-freeze/
"JMM and final field freeze"
2008 article by Alex Miller


Java Language Specification SE 8
Section on Threads and Locks, defining the Java Memory Model (not the
clearest thing to read).
https://docs.oracle.com/javase/specs/jls/se8/html/jls-17.html


"Close Encounters of the Java Memory Model Kind"
Aleksey Shipilev, 2016
https://shipilev.net/blog/2016/close-encounters-of-jmm-kind/

"Memory Barriers: a Hardware View for Software Hackers"
Paul E. McKenney, 2009
http://www.puppetmastertrading.com/images/hwViewForSwHackers.pdf


StackOverflow "What is Clojure volatile?"
https://stackoverflow.com/questions/31288608/what-is-clojure-volatile

# 2019-Jul discussion on #clojure-dev Slack channel

If anyone has both (a) good knowledge of how the edit fields are used in Clojure's transient data structure implementations and (b) time to kill answering questions about them, I have a few.
andy.fingerhut 10:35 AM
In particular, probably the most important question I have is: (a) Should it be an invariant that all edit fields within the same tree all point at the same Java object (if that should be an invariant, then there is a bug in Clojure's implementation where it doesn't preserve that condition).  (b) If that shouldn't be an invariant, then how do they enforce what they are intended to enforce?
alexmiller 11:03 AM
I don't have code up atm but I believe that is the field that used to be an invariant, but no longer is
assuming that's the thread tracking field
originally transient data structures required that all changes to a transient happened in the same thread
andy.fingerhut 11:04 AM
It is the thread-tracking field.  I know that it used to be enforced "more strongly" in the past, and now much less so.
alexmiller 11:04 AM
in clojure 1.6 we relaxed this to "no more than one thread at a time" so that transients can be modified by go blocks which may get assigned to different threads over time from the go block pool
andy.fingerhut 11:05 AM
I think I am finding the answers to my original question, a la rubber duck (and probably almost having the answer before asking).
alexmiller 11:05 AM
iirc we removed some of the checks but not all of the tracking
andy.fingerhut 11:06 AM
The edit nodes are still necessary, I think, to know which tree nodes are "owned" by this transient (and it made clones of them so it is safe for it to mutate them), vs. ones that might still be shared with immutable data structures.
alexmiller 11:10 AM
that sounds right. it's been a few years. :)
andy.fingerhut 3:27 PM
Hmmm.  And I think I may have finally answered a question that has long bugged me: Is it safe to pass transient collections from one thread to another, and if so, why?  Most collections have a bunch of Java arrays in their implementation, which by themselves are not always published safely to other threads in all cases, only some.  For persistent collections, I believe all Java arrays are fully written during constructor calls, and then a reference to those arrays are stored in final fields of the persistent collection implementation, and final fields have special rules in the JMM for being safe to publish to other threads.
:clap:
1

I believe the answer for transients has always been that they are safe because all of the transient Java objects have volatile fields, so as long as every operation on a transient reads or writes at least one of those fields after modifying an array, and the next thread reads one of them (which ensureEditable() does in all of the published methods), then the next thread should read everything up to date, too.
Wow, it would be so easy to break that with otherwise innocent-looking changes to some of the transient methods.
andy.fingerhut 3:46 PM
OK, that hasn't always been the answer, because Alex made those transient object fields volatile at the same time that the same-thread-only-can-update restriction was removed for transients.  Still digging on this for a bit more, and may write up some notes in case anyone wants to read and/or double-check them.
:+1:
1

alexmiller 4:10 PM
When they were enforced to be single thread only it wouldn’t matter
andy.fingerhut 4:30 PM
It still needed a way to safely publish updates made while transient, in the implementation of persistent!, which I believe were in place since transients were introduced. (edited) 

alexmiller
Yes, I would think so.

Hmmm.  Looks like core.rrb-vector does not using volatiles for transients, the way the core vector type does.  Something to improve on there.
andy.fingerhut 5:34 PM
OK, for any Java concurrency experts out there, I am now less sure of transient collection thread safety for passing transient vectors (at least, as first example I am looking at) from one thread to another.  Suppose everything is nicely synchronized between two threads when things begin, then thread 1 calls transient on a vector and does a pop! on it, which caused its count to decrease from 3 to 2.  Thread 2 gets a reference to this post-pop! transient vector, and calls count on it.  Is thread 2 guaranteed to get the updated count when pop! completes?  A few more details in the thread I will start on this.

I think maybe the answer is "no".  Why not?  Because even though all of the fields of class PersistentVector$TransientVector are volatile: https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/PersistentVector.java#L520-L524

src/jvm/clojure/lang/PersistentVector.java:520-524
static final class TransientVector extends AFn implements ITransientVector, ITransientAssociative2, Counted{
    volatile int cnt;
    volatile int shift;
    volatile Node root;
    volatile Object[] tail;
 Show more
clojure/clojure | Added by a bot

andy.fingerhut  4 days ago
The pop! operation for transient vectors ends by assigning a value to the root field, followed later by decrementing cnt: https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/PersistentVector.java#L787-L792

src/jvm/clojure/lang/PersistentVector.java:787-792
        root = newroot;
        shift = newshift;
        --cnt;
        tail = newtail;
        return this;
 Show more
clojure/clojure | Added by a bot

andy.fingerhut  4 days ago
Thread 2's count starts by calling ensureEditable, which reads the volatile field root (I am assuming that this happens strictly after pop! completes for this example), then reads the volatile field cnt: https://github.com/clojure/clojure/blob/master/src/jvm/clojure/lang/PersistentVector.java#L537-L540

src/jvm/clojure/lang/PersistentVector.java:537-540
    public int count(){
        ensureEditable();
        return cnt;
    }
clojure/clojure | Added by a bot

andy.fingerhut  4 days ago
So we know Thread 1's pop! causes write of root to happen-before write of cnt, by program order of operations in a single thread.

andy.fingerhut  4 days ago
And we know Thread 2's count causes read of root to happen-before read of cnt, for the same reason.

andy.fingerhut  4 days ago
But even if we assume that Thread 1's write of root happens-before Thread 2's read of root, that doesn't seem to require Thread 2's read of cnt to see the value of Thread 1's update of cnt.   That is: separate volatile fields do not have any coordination between them. (edited)

andy.fingerhut  4 days ago
For this example scenario, it seems that putting an assignment to the field root in method pop at the very end, after updating cnt, would be safer.

andy.fingerhut  4 days ago
All TransientVector methods that are public either start with ensureEditable(), or some other function that calls ensureEditable(), which reads the field root (good), but in order for that to guarantee a consistent state among all 4 volatile fields, it seems like all methods that modify the collection state should also end with an assignment to root.

andy.fingerhut  4 days ago
Perhaps an answer is: depending upon precisely how thread 2 got the reference to the transient collection, it might introduce more synchronization constraints with thread 1, that guarantee things are correct.

alexmiller  4 days ago
You might be right, but I’d have to do some code study to tell. I vaguely recall there being a jira in this area too if you want to hunt it up

alexmiller  4 days ago
There is a total ordering of all volatiles and some additional rules related to happen-before too iirc

andy.fingerhut  4 days ago
https://clojure.atlassian.net/browse/CLJ-1580 is the one I found that looks most related, and its patch was merged in with the same Clojure release where the birth-thread check was removed from transients.

andy.fingerhut  4 days ago
Commit here: https://github.com/clojure/clojure/commit/ef1d0607e19d43dabf72fecb3ea8a263e8bb7351

leonoel  4 days ago
what makes transients memory consistent is not volatiles, it's the golden rule of transients : always use return value for later changes. To follow this rule in a multithreaded context implies a synchronization of t1 and t2 such that t1's pop! returned HB t2's count called, thus ensuring all changes made by t1 are visible on t2.

andy.fingerhut  4 days ago
I agree that may be correct, but is it necessarily true that t2 getting a reference to the updated transient value requires a synchronization from t1 to t2?  If so, why all the warnings in JCIP about improper publishing of a reference to an object?

andy.fingerhut  4 days ago
And if it isn't the volatile's, why bother adding them to the implementation?

leonoel  4 days ago
JCIP's safe publication stuff is about visibility guarantees on race conditions. Transients are explicitly designed to be updated one thread at a time, so I guess it's safe to assume no race conditions here.

leonoel  4 days ago
BTW I would be very glad to know the purpose of all these volatiles in the implementation as well

leonoel  4 days ago
probably the same purpose as volatiles in stateful transducers

andy.fingerhut  3 days ago
In response to this statement of yours Alex: "There is a total ordering of all volatiles and some additional rules related to happen-before too iirc" I think it is true that there is a total ordering of all accesses to a single volatile field among all threads, but at least from my reading so far I haven't seen anything that says there must be a total ordering among accesses to two different volatile fields (unless there is some other constraint like program order or monitor locks, etc.)

andy.fingerhut  3 days ago
FYI for those interested I finally noticed that Goetz's book "Java Concurrency In Practice" (JCIP) has a chap. 16 that attempts to make explicit connections between the Java Memory Model specification terminology, and recommendations made elsewhere in the book.  Nice.
:+1:
1


andy.fingerhut  1 day ago
One source for the purpose of the volatiles is here: https://clojure.atlassian.net/browse/CLJ-1580   Those were a continuation of changes started with this ticket: https://clojure.atlassian.net/browse/CLJ-1498


2019-Sep-04 discussion

andy.fingerhut 2:25 PM
In the Clojure/Java implementation of core.async, what kind of Java synchronization techniques, if any, are used when a go block switches from running in one thread T1, to running in a different thread T2?  (feel free to correct any misimpressions that may be revealed in the question itself)

alexmiller 2:27 PM
channels have a mutex
go blocks become suspended computations (ie functions) that are invoked
when woken from a park

andy.fingerhut 2:29 PM
Can a go block be parked after running in thread T1, then later woken and run in thread T2?

alexmiller 2:30 PM
yes
that's the whole idea

andy.fingerhut 2:30 PM
and any "local data" in the go block will not necessarily have gone through any synchronization mechanisms, unless the Clojure developer adds them?

alexmiller 2:31 PM
where would "local data" be?

andy.fingerhut 2:31 PM
loop/let symbols, fn. parameters, ...

alexmiller 2:31 PM
it'll be run in the context of restored local bindings etc

andy.fingerhut 2:32 PM
saved and restored local bindings, with or without Java synchronization mechanisms provided by the core.async implementation machinery?

alexmiller 2:33 PM
I think those are saved in an atom
stretching the limit of my memory

andy.fingerhut 2:33 PM
I can look this up myself in the code, if it isn't on the tip of your brain -- not trying to get you to do any extensive time digging here.

alexmiller 2:34 PM
that's about as far as I remember, have to run anyhow

andy.fingerhut 2:34 PM
thanks for the info

hiredman 2:37 PM
its an atomic array
bindings created with binding are also saved and restored as the go block moves between threads (I think there are some outstanding issues with that though)

andy.fingerhut 2:38 PM
One of these, I suppose? https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicReferenceArray.html

hiredman 2:39 PM
where locals would be saved in to the, uh, stackframe I guess on the jvm they instead get stored into the atomic array
yeah
and the array has a few extra slots for other bits of dynamic information, bindings, exception handlers

andy.fingerhut 2:40 PM
So if a go block has mutable data that it modifies, and doesn't explicitly try to synchronize it, the act of core.async writing references to such mutable data into an AtomicReferenceArray in thread T1, then reading it back out when restoring in thread T2, should synchronize all of those changes made by T1 and make them visible in T2.  (statement, not question -- but corrections welcome) (edited) 

hiredman 2:42 PM
I vaguely get the sense that is the idea, but I don't know enough to evaluate if that goal is successful achieved
I think I have seen tickets where people have argued you could use a regular array in the go implementation, relying on the channel locks for visibility

andy.fingerhut 2:45 PM
Sounds like a bit more "global" property of the implementation, and thus a bit harder to check, and easier to break, but it does seem at least possible in principle.


----------------------------------------------------------------------

2013-Jul-03

Slack: csd: Could a function like take have been written using transients instead of volatile? I'm guessing the latter was more performant for some reason? When should I use the former vs the latter?

Slack: ghadi: transients were not allowed to be accessed across threads
volatile's purpose is cross-thread publishing

Slack: csd: do transducer execute across threads?

Slack: ghadi: some can

Slack: ghadi: when to use transients: transients need to be contained in a "birthing process" for a persistent data structure

Slack: ghadi: transients (intentionally) do not support all the operations of persistent maps/vectors

Slack: csd: don't volatiles have an issue with multiple writer threads? i would think that would present an issue for using a transducer in multiple threads

Slack: ghadi: you should not let transients escape a "birthing process" (function or handful of functions) or use transients for long-lived data

Slack: csd: also, what's an example of a transducer that is parallizable?

Slack: alexmiller: there are none

Slack: alexmiller: there are no parallel transducer contexts

Slack: alexmiller: volatile is there to allow a transducer to migrate across threads (can happen with a channel transducer and go blocks which are multiplexed over a thread group), but there is only ever one thread at a time

Slack: csd: thanks

Slack: csd: I guess also that transient only applies to a collection type, not e.g. the case of `take, a number

Slack: mpenet: Quite different beasts


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

