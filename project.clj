(defproject org.clojure/core.rrb-vector "0.0.12-SNAPSHOT"
  :description "RRB-Trees for Clojure(Script) -- see Bagwell & Rompf"
  :url "https://github.com/clojure/core.rrb-vector"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.2.0"
  :parent [org.clojure/pom.contrib "0.1.2"]
  :dependencies [[org.clojure/clojure "1.5.1"]]
  :source-paths ["src/main/clojure"
                 "src/main/cljs"]
  :test-paths ["src/test/clojure"]
  :jvm-opts ^:replace ["-XX:+UseG1GC"
                       "-XX:-OmitStackTraceInFastThrow"]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.7.0"]]
                   :plugins [[lein-cljsbuild "1.1.7"]]}
             :socket {:jvm-opts ["-Dclojure.server.repl={:port 50505 :accept clojure.core.server/repl}"]}
             ;; Using collections-check requires these minimum
             ;; versions of Clojure and test.check
             :check {:dependencies [[collection-check "0.1.7"]
                                    [org.clojure/clojure "1.7.0"]
                                    [org.clojure/test.check "0.9.0"]]
                     :test-paths ["src/test_local/clojure"]}
             :test-failing {:test-paths ["src/test_failing/clojure"]}
             :cljs {:dependencies [[org.clojure/clojure "1.10.0"]
                                   [org.clojure/clojurescript "1.10.238"]]}
             :1.6 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :1.7 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :1.8 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :1.9 {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :1.10 {:dependencies [[org.clojure/clojure "1.10.1"]]}
             :master {:dependencies [[org.clojure/clojure "1.11.0-master-SNAPSHOT"]]}}
  :cljsbuild {:builds {:test {:source-paths ["src/main/cljs"
                                             "src/test/cljs"]
                              :compiler {:optimizations :advanced
                                         :output-to "out/test.js"}}}})
