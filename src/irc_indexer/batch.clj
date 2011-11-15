(ns irc-indexer.batch
  (:import [java.io PushbackReader])
  (:require [irc-indexer.indexing :as indexing]))

(defn batch-process
  "Reads from a stream (containing an IRC log created by irc-indexer) and indexes
   all its events into the given index, putting the specified number of
   entries in each transcript segment. (This is how you reindex.)"
  [raw-input indexer]
  (with-open [input (PushbackReader. raw-input)]
    (loop [log-entry (read input false nil)
           read-count 1]
      (if log-entry
          (do 
            (indexing/store indexer
                            (:server log-entry)
                            (:channel log-entry)
                            (select-keys log-entry [:event :nick :log_time :message]))
            (when (= 0 (mod read-count 5000))
              (indexing/push indexer))
            (recur (read input false nil) (inc read-count)))
          (do
            (println "Entries found:" read-count)))))
  (indexing/push indexer))