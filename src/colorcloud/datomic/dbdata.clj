;; populate the db data
(ns colorcloud.datomic.dbdata
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  (:require [datomic.api :as d])
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


; the value for ref type attribute is refed entity id, not the refed entity refernece.
; When a transaction adds an attribute of reference type to an entity, it must specify 
; an entity id as the attribute's value. entity id can be temp id, or real id.
; for many ref, you can specify a list of entity id.
;

;; store database uri
(defonce uri "datomic:free://localhost:4334/colorcloud")
;; connect to database
(def conn (d/connect uri))

; the global id, gened from unix epoch in milliseconds
(def PersonId (atom (to-long (clj-time/now))))

; get the id for a person
(defn getPersonId [] (let [n (swap! PersonId inc)] (str n)))


(defn parent-attr
  "compose a map of for attributes of parent"
  [fname lname age addr gender email phone]
  (let [m {:db/id (d/tempid :db.part/user)
          :parent/fname fname
          :parent/lname lname
          :parent/age age
          :parent/address addr
          :parent/gender gender
          :parent/email email
          :parent/phone phone}]
    (prn m)
    m))


(defn child-attr
  "compose a map of for attributes of children"
  [fname lname age addr gender email phone]
  (let [m {:db/id (d/tempid :db.part/user)
          :child/fname fname
          :child/lname lname
          :child/age age
          :child/address addr
          :child/gender gender
          :child/email email
          :child/phone phone}]
    (prn m)
    m))


; create a parent entity, does not link child yet
(defn create-parent
  "create a parent entity, id is random"
  []
  (let [pid (getPersonId)
        pfname (str "P-fname-" pid)
        plname (str "P-lname-" pid)
        page (+ 30 (rand-int 20))
        paddr (str "addr-" pid)
        pgender (rand-nth [:M :F])
        pemail (str "P-fname-lname-" pid "@email.com")
        pphone (str "500-000-" pid)
        parent (parent-attr pfname plname page paddr pgender pemail pphone)]
    parent))


; create a child entity, does not link parent yet
(defn create-child
  "create a child entity, id is random"
  []
  (let [cid (getPersonId)
        cfname (str "C-fname-" cid)
        clname (str "C-lname-" cid)
        cage (+ 5 (rand-int 15))
        caddr (str "addr-" cid)
        cgender (rand-nth [:M :F])
        cemail (str "C-fname-lname-" cid "@email.com")
        cphone (str "100-000-" cid)
        child (child-attr cfname clname cage caddr cgender cemail cphone)]
    child))
