;; populate the db data
(ns colorcloud.datomic.dbdata
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  (:require [datomic.api :only [q db] :refer [q db] :as d])
  (:require [colorcloud.datomic.dbschema :as dbschema])
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
; {:db/id #db/id[:db.part/user -1000452], :neighborhood/name "Beacon Hill", 
;  :neighborhood/district #db/id[:db.part/user -1000451]}
;
; In general, unique temporary ids are mapped to new entity ids.
; when one of the attribute is :db/unique :db.unique/identity, system will map to existing entity if matches or make a new.
; to add fact to existing entity, retrieve entity id the add using the entity id.
; adding entity ref, must specify an entity id(could be tempid) as the attribute's value.
; takes advantage of the fact that the same temporary id can be generated multiple times by 
; specifying the same partition and negative number; and that all instances of a given temporary id 
; within a transaction will resolve to a single entity id.


;; store database uri
(defonce uri "datomic:free://localhost:4334/colorcloud")
;; connect to database
(def conn (d/connect uri))

; the global id for gen 
(def PersonId (atom 0))

; get the id for a person
(defn getPersonId [] (let [n (swap! PersonId inc)] (str (+ 1000000 n))))

(defn person-attr
  "compose a map of for attributes of person"
  [id fname lname age addr gender email phone]
  (str "{:db/id #db/id[:db.part/user -" id
        " :person/firstname " fname
        " :person/lastname " lname
        " :person/age " age
        " :person/addr " addr
        " :person/gender " gender
        " :person/email " email
        " :person/phone " phone
        " }")

(defn parent-attr
  "compose a map of for attributes of parent"
  [id personid childid]
  (str "{:db/id #db/id[:db.part/user -" id
       " :parent/person #db/id[:id.part/user -" personid
       " :parent/child #db/id[:id.part/user -" childid
       " }"))

(defn child-attr
  "compose a map of for attributes of children"
  [id personid parentid]
  (str "{:db/id #db/id[:db.part/user -" id
       " :child/person #db/id[:id.part/user -" personid
       " :child/parent #db/id[:id.part/user -" parentid
       " }"))


(defn insert-parent
  "insert a parent with sequential name"
  []
  (let [pid (getPersonId)
        pfname (str "P-fname-" id)
        plname (str "P-lname-" id)
        page (+ 30 (rand-int 20))
        paddr (str "addr-" id)
        pgender (rand-nth ["M" "F"])
        pemail (str "P-fname-lname-" id "@email.com")
        pphone (str "500-000-" id)
        ppersonstr (person-attr pid pfname plname page paddr pgender pemail pphone)
        
        cid (getPersonId)
        cfname (str "C-fname-" id)
        clname (str "C-lname-" id)
        cage (+ 5 (rand-int 15))
        caddr (str "addr-" id)
        cgender (rand-nth ["M" "F"])
        cemail (str "C-fname-lname-" id "@email.com")
        cphone (str "100-000-" id)
        cpersonstr (person-attr cid cfname clname cage caddr cgender cemail cphone)

        childid (getPersonId)
        parentid (getPersonId)
        childstr (child-attr childid cid parentid)
        parentstr (parent-attr parentid pid childid)
        
        data-tx-str (str "[ " ppersonstr cpersonstr childstr parentstr " ]")
        data-tx (read-string data-tx-str)
       ]
    @(d/transact conn data-tx)))


(defn list-parent
  "query all parent"
  []
  (let [results (q '[:find ?e ?p :where [?e :parent/person ?p]] (db conn))
        id (ffirst results)
        fname (second (first results))
        e (-> conn db (d/entity id))]
    (prn fname e)))