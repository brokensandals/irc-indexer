(ns irc-indexer.test.indexing
  (:require [cheshire.core :as json])
  (:use [clj-http.fake]
        [clojure.test]
        [irc-indexer.indexing]))

(def samples-1
  [{:event "message" :nick "mister" :log_time "2010-09-11T13:56:02Z" :message "It's a harsh, cruel world."}
   {:event "kick" :nick "dude" :log_time "2010-09-11T13:57:21Z" :message "mister - go be depressing somewhere else"}])

(def samples-2
  [{:event "message" :nick "buddy" :log_time "2010-09-11T14:12:18Z" :message "talkity talkity talk talk"}
   {:event "message" :nick "pal" :log_time "2010-09-11T14:12:19Z" :message "QUIET!"}])

(def samples (concat samples-1 samples-2))

(def server "irc.some")
(def channel-1 "#people")
(def channel-2 "#nonpeople")
(def room-1 [server channel-1])
(def room-2 [server channel-2])

(def documents
  {"1" {:server server :channel channel-1 :events samples-1}
   "2" {:server server :channel channel-1 :events samples-2}
   "3" {:server server :channel channel-2 :events samples}})

(def url "http://localhost/test")
(def put-urls ["http://localhost/test/transcript/1" "http://localhost/test/transcript/2" "http://localhost/test/transcript/3"])

(def ok-response {:status 200 :body "{\"ok\":true}"})
(def error-response {:status 500})
(def not-ok-response {:status 200 :body "{\"ok\":false}"})

(deftest t-store
  (let [indexer (new-indexer url 5)]
    (doseq [sample samples]
      (store indexer server channel-1 sample))
    (is (await-for 10 indexer) "agent timed out")
    (is (= 1 (count (:rooms @indexer))) "wrong number of rooms")
    (is (= samples (get-in @indexer [:rooms room-1 :entries])) "incorrect entries recorded")
    (is (= 1 (count (:documents @indexer))) "wrong number of documents")
    (is (= {:server server :channel channel-1 :entries samples}
           (get-in @indexer [:documents
                             (get-in @indexer [:rooms room-1 :id])]))
        "incorrect documents")))

(deftest t-store-transcript-size
  (let [indexer (new-indexer url 2)]
    (doseq [sample samples-1]
      (store indexer server channel-1 sample))
    (is (await-for 10 indexer) "agent timed out")
    (is (= 1 (count (:documents @indexer))) "created second document prematurely")
    (let [id-1 (get-in @indexer [:rooms room-1 :id])]
      (doseq [sample samples-2]
        (store indexer server channel-1 sample))
      (is (await-for 10 indexer) "agent timed out")
      (is (= 1 (count (:rooms @indexer))) "wrong number of rooms")
      (is (= samples-2 (get-in @indexer [:rooms room-1 :entries])) "incorrect entries in current room record")
      (let [id-2 (get-in @indexer [:rooms room-1 :id])]
        (is (= {id-1 {:server server :channel channel-1 :entries samples-1}
                id-2 {:server server :channel channel-1 :entries samples-2}}
               (:documents @indexer))
            "incorrect documents")))))

(deftest t-store-two-rooms
  (let [indexer (new-indexer url 5)]
    (store indexer server channel-1 (first samples-1))
    (store indexer server channel-2 (first samples-2))
    (store indexer server channel-1 (second samples-1))
    (store indexer server channel-2 (second samples-2))
    (is (await-for 10 indexer) "agent timed out")
    (is (= samples-1 (get-in @indexer [:rooms room-1 :entries])) "incorrect entries in room-1 record")
    (is (= samples-2 (get-in @indexer [:rooms room-2 :entries])) "incorrect entries in room-2 record")
    (let [id-1 (get-in @indexer [:rooms room-1 :id])
          id-2 (get-in @indexer [:rooms room-2 :id])]
      (is (= {id-1 {:server server :channel channel-1 :entries samples-1}
              id-2 {:server server :channel channel-2 :entries samples-2}}
             (:documents @indexer))
          "incorrect documents"))))

(defn- responder 
  [uris response expect]
  (fn [{:keys [request-method content-type body uri]}]
    (dosync (alter uris conj uri))
    (is (= "application/json" content-type) "wrong content type")
    (is (= :put request-method) "wrong http method")
    (is (= expect (json/parse-string (String. body) true)))
    response))

(deftest t-push
  (let [indexer (new-indexer url 5)
        uris (ref '())]
    (with-fake-routes {(put-urls 0) (responder uris ok-response (documents "1"))
                       (put-urls 1) (responder uris ok-response (documents "2"))
                       (put-urls 2) (responder uris ok-response (documents "3"))}
      (send indexer assoc :documents documents)
      (push indexer)
      (is (await-for 10 indexer) "agent timed out")
      (is (= '("/test/transcript/1" "/test/transcript/2" "/test/transcript/3") (sort @uris)) "each path not invoked exactly once")
      (is (= {} (:documents @indexer)) "documents not cleared"))))

(deftest t-push-failures
  (let [indexer (new-indexer url 5)
        uris (ref '())]
    (with-fake-routes {(put-urls 0) (responder uris error-response (documents "1"))
                       (put-urls 1) (responder uris ok-response (documents "2"))
                       (put-urls 2) (responder uris not-ok-response (documents "3"))}
      (send indexer assoc :documents documents)
      (push indexer)
      (is (await-for 1000 indexer) "agent timed out")
      (is (= '("/test/transcript/1" "/test/transcript/2" "/test/transcript/3") (sort @uris)) "each path not invoked exactly once")
      (is (= (dissoc documents "2") (:documents @indexer)) "documents should contain failed submissions (only)"))))
