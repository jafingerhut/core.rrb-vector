(ns clojure.core.rrb-vector.test-infra
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.core.rrb-vector.rrbt :as rrbt]
            [clojure.core.rrb-vector.debug :as dv]))


;; Note: I am leaving full-debug-opts and set-debug-opts! here for a
;; bit longer, since I recommended someone else use them for trying to
;; analyze a problem they experienced when using core.rrb-vector, but
;; I have already recently added these same things into the
;; clojure.core.rrb-vector.debug namespace, and recommend using the
;; ones in that namespace.

(def full-debug-opts {:trace false
                      :validate true
                      :return-value-checks
                      [dv/edit-nodes-errors
                       dv/basic-node-errors
                       dv/ranges-errors]
                      ;; false -> throw an exception when error detected
                      :continue-on-error false
                      ;; true -> do not throw an exception when warning found
                      :continue-on-warning true})

(defn set-debug-opts!
  [opts]
  (reset! dv/debug-opts
          {:catvec opts        ;; affects checking-catvec behavior,
                               ;; via calling checking-splicev and
                               ;; checking-splice-rrbts and enabling
                               ;; their extra checks.
           :subvec opts        ;; affects checking-subvec behavior,
                               ;; via calling checking-slicev and
                               ;; enabling its extra checks
           :pop opts           ;; affects checking-pop
           :pop! opts          ;; affects checking-pop!
           :transient opts}))  ;; affects checking-transient

(defn now-msec []
  #?(:clj (System/currentTimeMillis)
     ;; Only intended to work for Node.js right now
     :cljs (js/Date.now)))

(def num-deftests-started (atom 0))
(def last-deftest-start-time (atom nil))

(defn reset-event-counts! []
  (reset! rrbt/peephole-optimization-count 0)
  (reset! rrbt/fallback-to-slow-splice-count1 0)
  (reset! rrbt/fallback-to-slow-splice-count2 0))

(defn print-event-counts []
  (println "peephole-opt-count=" @rrbt/peephole-optimization-count
           "fallback-count1=" @rrbt/fallback-to-slow-splice-count1
           "fallback-count2=" @rrbt/fallback-to-slow-splice-count2))

(defn peephole-opt-debug-fn [orig-v optimized-v]
  (let [dbg-vec-opts {:show-children-summary true,
                      :always-show-fringes true,
                      :max-depth 2
                      :show-ranges-as-deltas true}]
    (println "====================")
    (println "this vector was peephole optimized:")
    (dv/dbg-vec orig-v dbg-vec-opts)
    (println)
    (println "result of peephole optimization:")
    (dv/dbg-vec optimized-v dbg-vec-opts)))

(defmethod clojure.test/report #?(:clj :begin-test-var
                                  :cljs [:cljs.test/default :begin-test-var])
  [m]
  (reset-event-counts!)
  (let [n (swap! num-deftests-started inc)]
    (when (== n 1)
      (println "----------------------------------------")
      #?(:clj (let [p (System/getProperties)]
                (println "java.vm.name" (get p "java.vm.name"))
                (println "java.vm.version" (get p "java.vm.version"))
                (println "(clojure-version)" (clojure-version)))
         :cljs (println "*clojurescript-version*" *clojurescript-version*))))
  (println)
  (println "----------------------------------------")
  (println "starting" (:var m))
  (reset! last-deftest-start-time (now-msec)))

(defmethod clojure.test/report #?(:clj :end-test-var
                                  :cljs [:cljs.test/default :end-test-var])
  [m]
  ;;(println "finished" (:var m))
  (println "elapsed time (sec)" (/ (- (now-msec) @last-deftest-start-time)
                                   1000.0))
  (print-event-counts))
