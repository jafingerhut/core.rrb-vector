(ns clojure.core.rrb-vector.test-utils
  (:require [clojure.core.rrb-vector.rrbt :as rrbt]))

;; The intent is to keep this file as close to
;; src/test/cljs/clojure/core/rrb_vector/test_utils.cljs as possible,
;; so that when we start requiring Clojure 1.7.0 and later for this
;; library, this file and that one can be replaced with a common test
;; file with the suffix .cljc

(def extra-checks? false)

(defn reset-optimizer-counts! []
  (println "reset all optimizer counts to 0")
  (reset! rrbt/peephole-optimization-count 0)
  (reset! rrbt/fallback-to-slow-splice-count1 0)
  (reset! rrbt/fallback-to-slow-splice-count2 0))

(defn print-optimizer-counts []
  (println "optimizer counts: peephole=" @rrbt/peephole-optimization-count
           "fallback1=" @rrbt/fallback-to-slow-splice-count1
           "fallback2=" @rrbt/fallback-to-slow-splice-count2))

;; Enable tests to be run on versions of Clojure before 1.10, when
;; ex-message was added.

#?(:clj
(defn ex-message-copy
  "Returns the message attached to ex if ex is a Throwable.
  Otherwise returns nil."
  {:added "1.10"}
  [ex]
  (when (instance? Throwable ex)
    (.getMessage ^Throwable ex)))
:cljs
(defn ex-message-copy
  "Returns the message attached to the given Error / ExceptionInfo object.
  For non-Errors returns nil."
  [ex]
  (when (instance? js/Error ex)
    (.-message ex))))

#?(:clj
(defn ex-cause-copy
  "Returns the cause of ex if ex is a Throwable.
  Otherwise returns nil."
  {:added "1.10"}
  [ex]
  (when (instance? Throwable ex)
    (.getCause ^Throwable ex)))
:cljs
(defn ex-cause-copy
  "Returns exception cause (an Error / ExceptionInfo) if ex is an
  ExceptionInfo.
  Otherwise returns nil."
  [ex]
  (when (instance? ExceptionInfo ex)
    (.-cause ex)))
)
