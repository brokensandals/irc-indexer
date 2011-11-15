(ns irc-indexer.bot
  (:import [java.util.concurrent TimeUnit])
  (:require [clj-time.core :as time]
            [clj-time.format :as time-format]
            [irc-indexer.indexing :as indexing])
  (:use irclj.core))

(def log-time-formatter (time-format/formatters :date-time-no-ms))

; irclj sometimes seems to equivocate about including or excluding the hash;
; I need consistency, so...
; FIXME: obviously it's not always OK to assume the channel name starts with a hash
(defn- normalize-channel
  [channel]
  (if (.startsWith channel "#")
      channel
      (str "#" channel)))

; irclj doesn't appear to provide an indicator of whether a message was to our bot
; or to the channel. We'll hackily infer this.
(defn- direct?
  [irc channel bot-nick]
  (and (not (.startsWith channel "#"))
       (not (some #(= % (normalize-channel channel))
                  (map normalize-channel (keys (:channels @irc)))))))

(defn log-irc
  "Logs IRC channels into a text file and an elasticsearch index.
   Needs an indexer (irc-indexer.indexing/new-indexer), and a Writer for the text file.
   Must be told the hostname and port to connect to; the nickname for the bot; the
   frequency (in seconds) to update the index; a (possibly empty) list of channels to join initially;
   and accepts text to show on joining a channel (announcement) and on receiving a direct message (help)."
  [& {:keys [indexer update-interval server port bot-nick channels log-writer announcement help]}]

  (letfn [(record-event [raw-channel event nick message] ; write an event to the IRC log, as well as store it using the indexer
            (let [channel (normalize-channel raw-channel)
                  log-time (time-format/unparse log-time-formatter (time/now))
                  entry {:event event, :nick nick, :log_time log-time, :message message}]
              (.write log-writer (prn-str (merge {:server server :channel channel} entry)))
              (.flush log-writer)
              (indexing/store indexer server channel entry)))]
    
    (connect
      (create-irc
        {:server server
         :port port
         :name bot-nick
         :username bot-nick
         :realname (str "irc-indexer " bot-nick)

         :fnmap
           {:on-join (fn [{:keys [irc nick channel]}]
                       (when (= bot-nick nick)
                         (send-message irc (normalize-channel channel) announcement))
                       (record-event channel "join" nick ""))
            
            :on-part (fn [{:keys [nick channel reason]}]
                       (record-event channel "part" nick reason))

            ; TODO: logging quits would be nice
            
            :on-kick (fn [{:keys [nick channel target reason]}]
                       ;lame, but I don't want another field in the mapping just for 'kick' messages
                       (record-event channel "kick" nick (str target
                                                              (when reason (str " - " reason)))))
            
            :on-message (fn [{:keys [irc nick channel message]}]
                          (if (direct? irc channel bot-nick)
                            (send-message irc channel help)
                            (record-event channel "message" nick message)))

            :on-topic (fn [{:keys [nick channel topic]}]
                        (record-event channel "topic" nick topic))}})
      
      :channels channels))
  
  (while true ;FIXME: graceful shutdown
    (.sleep TimeUnit/SECONDS update-interval)
    (indexing/push indexer)))
