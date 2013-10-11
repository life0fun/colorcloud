;; datomic data accessor
(ns colorcloud.datomic.dda
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  (:require [datomic.api :only [q db] :refer [q db] :as d])
  (:require [colorcloud.datomic.dbschema :as dbschema]
            [colorcloud.datomic.dbdata :as dbdata])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:require [clj-redis.client :as redis])    ; bring in redis namespace
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format :refer [parse unparse formatter]]
            [clj-time.coerce :refer [to-long from-long]]))


;
; To add data to a new entity, build a transaction using :db/add implicitly 
; with the map structure (or explicitly with the list structure), a temporary id, 
; and the attributes and values being added.
; #db/id[partition-name value*] : value is an optional negative number.
; all instances of the same temp id are mapped to the same actual entity id in a given transaction, 
; {:db/id entity-id attribute value attribute value ... }
; [:db/add entity-id attribute value]
; [:db/add entity-id attribute value]
; {:db/id #db/id[:db.part/user -1000452], :neighborhood/name "Beacon Hill", :neighborhood/district #db/id[:db.part/user -1000451]}
;
; In general, unique temporary ids are mapped to new entity ids.
; when one of the attribute is :db/unique :db.unique/identity, system will map to existing entity if matches or make a new.
; to add fact to existing entity, retrieve entity id the add using the entity id.
; adding entity ref, must specify an entity id(could be tempid) as the attribute's value.
; takes advantage of the fact that the same temporary id can be generated multiple times by 
; specifying the same partition and negative number; and that all instances of a given temporary id 
; within a transaction will resolve to a single entity id.

;
; (def e (d/entity (db conn) attr-id) gets all entity with ids
; (keys e) or (:parent/child (d/entity (db conn) 17592186045703))
;

;; store database uri
(defonce uri "datomic:free://localhost:4334/colorcloud")
;; connect to database
(def conn (d/connect uri))


;; parse schema dtm file
;(def schema-tx (read-string (slurp "./resource/schema/seattle-schema.dtm")))
;; parse seed data dtm file
;(def data-tx (read-string (slurp "./resource/schema/seattle-data0.dtm")))


; create attr schema thru conn
(defn create-schema
  "create schema with connection to db"
  []
  (dbschema/create-schema conn))

; list all install-ed attrs in db
(defn list-attr
  "list attibutes in db"
  [attr]
  (if-not attr
    (dbschema/list-attr (db conn))
    (dbschema/list-attr (db conn) attr)))

(defn get-communties
  "get all communities"
  []
  (let [comms (q '[:find ?c :where [?c :community/name]] (db conn))]
    (prn "all communities " (count comms))
    comms))

(defn query
  "query for entities"
  []
  (get-communties))


(def data-tx "[
  {:db/id #db/id[:db.part/user -1000001] :person/firstname \"john\"}
  ]")

(defn insert-person
  "test to insert parent and child"
  [pname cname]
  (let [data-tx (read-string data-tx)]
    @(d/transact conn data-tx)))

(defn insert-parent
  "insert a parent with sequential ids"
  []
  (dbdata/insert-parent))

(defn parent-child-names
  "print parent and children names"
  [ [pid cid] ]
  (let [pe (d/entity (db conn) pid)
        ce (d/entity (db conn) cid)
        pfname (:parent/fname pe)
        cfname (:child/fname ce)]
    [pfname cfname]))

(defn list-parent
  "query all parents with all children"
  []
  (let [pc (q '[:find ?p ?c :where [?p :parent/child ?c]] (db conn))]
        ;(map prn pc)))
        (map parent-child-names pc)))
