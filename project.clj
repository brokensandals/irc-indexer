(defproject irc-indexer "1.0.0-SNAPSHOT"
  :description "Logs IRC channels into an elasticsearch index."
  :dependencies [[cheshire "2.0.2"]
                 [clj-http "0.2.3"]
                 [clj-time "0.3.1"]
                 [irclj "0.4.1"]
                 [org.clojure/clojure "1.3.0"]
                 [org.clojure/tools.logging "0.2.3"]
                 [commons-cli/commons-cli "1.2"]]
  :dev-dependencies [[clj-http-fake "0.2.3"]]
  :main irc-indexer.core)