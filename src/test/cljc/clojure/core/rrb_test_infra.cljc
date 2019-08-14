(ns clojure.core.rrb-test-infra
  (:require [clojure.test :refer [deftest testing is are]]
            [clojure.core.rrb-vector.debug :as dv]))


(def full-debug-opts {:trace false
                      :validate true
                      :return-value-checks
                      [dv/edit-nodes-error-checks
                       dv/basic-node-error-checks
                       dv/ranges-error-checks]})

(defn set-debug-opts! [opts]
  (reset! dv/debug-opts {;;:catvec opts
                         :splice-rrbts opts
                         :slicev opts
                         :pop opts
                         :pop! opts
                         :transient opts}))

(defmethod clojure.test/report #?(:clj :begin-test-var
                                  :cljs [:cljs.test/default :begin-test-var])
  [m]
  (println)
  (println "----------------------------------------")
  (println "starting" (:var m)))

#_(defmethod clojure.test/report #?(:clj :end-test-var
                                  :cljs [:cljs.test/default :end-test-var])
  [m]
  (println "finishing" (:var m)))

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
