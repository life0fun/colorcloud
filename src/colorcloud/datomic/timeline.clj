;; datomic data accessor
(ns colorcloud.datomic.timeline
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  (:require [datomic.api :as d])
  (:require [colorcloud.datomic.dbschema :as dbschema]
            [colorcloud.datomic.dbdata :as dbdata])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:require [clj-redis.client :as redis])    ; bring in redis namespace
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format :refer [parse unparse formatter]]
            [clj-time.coerce :refer [to-long from-long]]))


; http://blog.datomic.com/2013/05/a-whirlwind-tour-of-datomic-query_16.html
;
; time travel as transaction is also an entity.
; given an atom, there are 3 time-related pieces
;   1. the transaction entity that created the datom
;   2. the relative time, t, (d/tx->t txid)
;   3. the clock time, :db/txInstant of the transaction
; the optional 4th args is ?tx, which give you back the transaction entity for the datom.
;
; to find the timestamp of an attribute value, find the transaction that create it.
; (def txid (->> (d/q '[:find ?tx :in $ ?e :where [?e :parent/child _ ?tx]] db id) ffirst))
;
; Given a tx id, the d/tx->t fn ret relative time to be used as happened before.
; Getting a Tx instant, (-> (d/entity (d/db conn) txid) :db/txInstant)
;   => #inst "2013-02-20T16:27:11.788-00:00"
; (d/as-of db (txid) to going back in Time.
; (def older-db (d/as-of db (dec txid)))
; (:parent/child (d/entity older-db id))
;
; timeline query across all time
;   (def hist (d/history db))
;   (->> (d/q '[:find ?tx ?v ?op :in $ ?e ?attr :where [?e ?attr ?v ?tx ?op]] 
;               hist 17592186045703 :parent/fname) (sort-by first))
;


;; store database uri
(defonce uri "datomic:free://localhost:4334/colorcloud")
;; connect to database and the db
(def conn (d/connect uri))
(def db (d/db conn))


; find the timeline of an attribute of 
(defn timeline
  "list a timeline of an attribute of the entity"
  [eid attr]
  (let [hist (d/history db)
        txhist (->> (d/q '[:find ?tx ?v ?op 
                           :in $ ?e ?attr
                           :where [?e ?attr ?v ?tx ?op]]
                      hist 
                      eid 
                      :parent/fname)
                    (sort-by first))
        ]
    (prn txhist)))
