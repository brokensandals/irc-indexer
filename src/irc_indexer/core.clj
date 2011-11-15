(ns irc-indexer.core
  (:gen-class)
  (:import [org.apache.commons.cli HelpFormatter
                                   Options
                                   PosixParser])
  (:require [clojure.string :as string]
            [irc-indexer.indexing :as indexing])
  (:use [clojure.java.io :only [reader writer]]
        [irc-indexer.batch]
        [irc-indexer.bot]
        [irc-indexer.setup]))

(def ^:private cli-options
  (doto (Options.)
    (.addOption "c" "config" true "Config file location, default ./config")
    (.addOption nil "setup" false "Initialize index specified in config file. WARNING: it will be destroyed if it already exists.")
    (.addOption nil "log" false "Log and index the server/channels specified in config file.")
    (.addOption nil "batch" true "Index the specified irc-indexer log file (this is how you reindex).")
    (.addOption nil "help" false "See this message.")))
    
(defn- print-help
  "Print the available command-line options."
  []
  (.printHelp (HelpFormatter.) "irc-indexer --setup | --log | --batch LOGFILE | --help [-c CONFIGFILE]" cli-options))

(defn- parse-options
  "Parse an array of command-line options into a map."
  [args]
  (let [cmd (.parse (PosixParser.) cli-options (into-array args))]
    {:config (.getOptionValue cmd "config" "config")
     :command (cond
                (.hasOption cmd "setup") :setup
                (.hasOption cmd "log") :log
                (.hasOption cmd "batch") :batch
                (.hasOption cmd "help") :help)
     :batch (.getOptionValue cmd "batch")}))

(defn -main
  [& args]
  (let [{:keys [config command batch]} (parse-options args)
        settings (when (#{:setup :log :batch} command)
                   (read-string (slurp config)))
        indexer (when (#{:log :batch} command)
                  (indexing/new-indexer (get-in settings [:index :url])
                                        (get-in settings [:index :transcript-size])))]
    (case command
      :setup (setup-index (get-in settings [:index :url]))
      :log (log-irc :indexer indexer
                    :update-interval (get-in settings [:index :update-interval])
                    :server (get-in settings [:irc :server])
                    :port (get-in settings [:irc :port])
                    :bot-nick (get-in settings [:irc :bot-nick])
                    :channels (get-in settings [:irc :channels])
                    :log-writer (writer (get-in settings [:irc :log]) :append true) ;FIXME: close this writer appropriately
                    :announcement (get-in settings [:irc :announcement])
                    :help (get-in settings [:irc :help]))
      :batch (with-open [input (reader batch)]
               (batch-process input indexer))
             (print-help))
    (when indexer (await indexer)))
  (shutdown-agents))