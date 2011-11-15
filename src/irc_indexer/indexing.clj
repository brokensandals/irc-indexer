(ns irc-indexer.indexing
  (:import [java.util UUID])
  (:require [cheshire.core :as json]
            [clj-http.client :as client]
            [clojure.tools.logging :as log])
  (:use slingshot.core))

; Using an agent like this may not be a perfect fit for the problem...
; but it's good enough, and I wanted to play with Clojure agents :)

(defn new-indexer
  "Creates an indexer agent, with which store and push can be called.
   index: elasticsearch index URL, e.g. http://localhost:9200/irc
   transcript-size: max number of entries to be included in a single indexed document"
  [index transcript-size]
  (agent {:rooms {}
          :documents {}
          :index index
          :transcript-size transcript-size}))

(defn store
  "Record an event (line) to be stored in the index when push is called.
   indexer: indexer created by new-indexer
   server: server hostname, e.g. irc.freenode.net
   channel: channel, e.g. #irclj
   entry: map with keys :event, :log_time, :nick, and :message, all strings
          :log_time format is 2011-10-04T16:40:12Z
   Asynchronous."
  [indexer server channel entry]
  (send indexer
    (fn [{:keys [rooms documents transcript-size] :as state}]
      (let [transcript (get rooms [server channel])
            entries (:entries transcript)
            new-transcript (if (and transcript (< (count entries) transcript-size))
                               (assoc transcript :entries (conj entries entry))
                               {:id (str (UUID/randomUUID))
                                :entries [entry]})
            new-document {:server server
                          :channel channel
                          :entries (:entries new-transcript)}]
        (merge state
               {:rooms (assoc rooms [server channel] new-transcript)
                :documents (assoc documents (:id new-transcript) new-document)})))))

(defn push
  "Given an indexer (created by new-indexer), creates/updates transcript documents in the
   index to contain all the entries so far recorded by calls to the store function.
   Warnings will be logged for each transcript that cannot be stored, and those transcripts
   will be retried on the next call to this function.
   Asynchronous."
  [indexer]
  (send-off indexer
    (fn [{:keys [documents index] :as state}]
      (log/debug "Starting indexing for index:" index)
      (letfn [(put [[id document]]
                (try
                  (log/debug "Updating transcript ID:" id "server:" (:server document) "channel:" (:channel document) "index:" index)
                  (let [response (client/put
                                   (str index "/transcript/" id)
                                   {:content-type "application/json"
                                    :body (json/generate-string document)})
                        parsed (json/parse-string (:body response))]
                    (when-not (true? (get parsed "ok"))
                      (throw+ {:type ::put-transcript-failed :response parsed})))
                  (catch Exception ex
                    (log/warn ex "Failed to update transcript ID:" id "server:" (:server document) "channel:" (:channel document) "index:" index)
                    ; Leave this in the documents map, to be retried.
                    [id document])))]
        (assoc state
               :documents
               (into {} (doall (filter identity (map put documents)))))))))
