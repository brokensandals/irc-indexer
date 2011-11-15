(ns irc-indexer.setup
  (require [cheshire.core :as json]
           [clj-http.client :as client]))

(def ^:private ^{:doc "Index and mapping settings for elasticsearch."} index-spec
  {:settings
    {:analysis
      {:analyzer
        {:default {:type "english"}}}}
   :mappings
    {:transcript
      {:properties
        {:server {:index "not_analyzed"
                  :type "string"
                  :include_in_all false}
         :channel {:index "not_analyzed"
                   :type "string"
                   :include_in_all false}
         :entries
          {:index_name "entry"
           :properties
            {:event {:index "not_analyzed"
                     :type "string"
                     :include_in_all false}
             :log_time {:format "date_time_no_millis"
                       :type "date"
                       :include_in_all false}
             :nick {:index "not_analyzed"
                    :type "string"}
             :message {:term_vector "with_positions_offsets"
                       :type "string"}}}}}}})

(defn setup-index
  "Given an elasticsearch index URL (e.g. http://localhost:9200/irc),
   attempts to delete and then create and initialize it for irc-indexer.
   Prints the responses received."
  [index]
  (println "Deleting" index ":" (client/delete index {:throw-exceptions false}))
  (println "Creating" index ":"
    (client/put index {:content-type "application/json"
                       :body (json/generate-string index-spec)
                       :throw-exceptions false})))