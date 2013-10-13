;; datomic data accessor
(ns colorcloud.datomic.dda
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  ;(:require [datomic.api :only [q db] :refer [q db] :as d])
  (:require [datomic.api :as d])
  (:require [colorcloud.datomic.dbschema :as dbschema]
            [colorcloud.datomic.dbdata :as dbdata])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:require [clj-redis.client :as redis])    ; bring in redis namespace
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format :refer [parse unparse formatter]]
            [clj-time.coerce :refer [to-long from-long]]))


; query API results as a list of facts as list of tuples. [ [tuple1...] [tuple2...]]
; the intermediate value for implicit joining are :db/id, so it can be in both lh/rh
; to join, the attribute col in tuple is the join col, and val can be used for 
;
; query stmt is a list, or a map, with :find :in :where seps query args.
;   [?a …]  collection   [ [?a ?b ] ]  relation
;   [(predicate ...)] [(function ...) bindings]
; a named rule is a list of clause [(community-type ?c ?t) [?c :community/type ?t]])]
; a set of rules is a list of rules. [[[rule1 ?c] [_ :x ?c]] [[rule2 ?d] [_ :x ?d]]]
; rules with the same name defined multiple times in rule set make rule OR.
;   [[northern ?c] (region ?c :region/ne)] 
;   [[northern ?c] (region ?c :region/n)]
; Within the same rule, multiple tuples are AND.
;


; knowing entity id, query with (d/entity db eid). otherwise, [:find $t :where []]
; (d/entity db eid) rets the entity. entity's toString only show id string.
; To add data to a new entity, build a transaction using :db/add implicitly 
; with the map structure (or explicitly with the list structure), a temporary id, 
; and the attributes and values being added.
; #db/id[partition-name value*] : value is an optional negative number.
; all instances of the same temp id are mapped to the same actual entity id in a given transaction, 
; {:db/id entity-id attribute value attribute value ... }
; [:db/add entity-id attribute value]
; (d/transact conn [newch [:db/add pid :parent/child (:db/id newch)]]

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
(def db (d/db conn))



(declare find-parent-by-cid)
(declare find-parent-by-cname)

;; parse schema dtm file
;(def schema-tx (read-string (slurp "./resource/schema/seattle-schema.dtm")))
;; parse seed data dtm file
;(def data-tx (read-string (slurp "./resource/schema/seattle-data0.dtm")))

; rules to find all parent or child with the name, using rules for OR logic
(def nameruleset '[[[byname ?n] 
                   [?e :parent/fname ?n]]  ; multiple tuples within a rule are AND.
                  [[byname ?n]
                   [?e :parent/lname ?n]]
                  [[byname ?n]
                   [?e :child/fname ?n]]
                  [[byname ?n]
                   [?e :child/lname ?n]]])


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
    (dbschema/list-attr db)
    (dbschema/list-attr db attr)))


(defn get-communties
  "get all communities"
  []
  (let [comms (d/q '[:find ?c :where [?c :community/name]] db)]
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
  (let [pe (d/entity db pid)
        ce (d/entity db cid)
        pfname (:parent/fname pe)
        cfname (:child/fname ce)]
    [pid pfname cid cfname]))


(defn list-parent
  "find all parents with all children"
  []
  (let [pc (d/q '[:find ?p ?c 
                  :where [?p :parent/child ?c]] 
                  db)]
        (map parent-child-names pc)))


; insert child to parent by parent id, get parent by list-parent, pid
; entity is a map of attributes. insert ref attr, must use refed entity id.
; [:db/add entity-id attribute value]
(defn insert-child
  "insert a children to parent by parent id, pid must be num, not string"
  [pid]  ; passed in pid is a num
  (let [pe (d/entity db pid)   ; entity is a map
        ch (:parent/child pe)
        newch (assoc (dbdata/create-child) :child/parent pid)]
    (d/transact conn [ newch
                      [:db/add pid :parent/child (:db/id newch)]])
    (prn pid pe ch newch)))


(defn find-parent
  "find parent by child id, id could be child name or child entity id"
  [cidstr & args]
  (let [cidval (read-string cidstr)
        cid? (number? cidval)]
    (if cid?
      (find-parent-by-cid cidval)
      (find-parent-by-cname cidval args))))

; find parent of a child
(defn find-parent-by-cid
  "find the parent of a child by its id, the passed cid is number"
  [cid]
  (let [ce (d/entity db cid)
        parent (:child/parent ce)   ; ret a list of parents
        pname (:parent/fname (first parent)) ; the first parent's name
        ]
    (prn parent pname)
    (prn ce (:child/fname ce))))


; search all fname and lname to check whether there is a match
(defn find-parent-by-cname
  "find the parent of a child by its id"
  [clname cfname]
  (let [fname (first cfname)
        ; args needs to bind to ?var to pass into query
        rset (d/q '[:find ?pfname ?ch
                :in $ % ?n
                :where [?e :parent/fname ?pfname]
                       [?e :parent/child ?ch]
                       (byname ?n)]
                db
                nameruleset
                (str clname))
        ]
    (prn clname rset)))

; find a person by name, either lname or fname
(defn find-person-by-name 
  "find a person by either first name or last name"
  [pname]
  (let [rset (d/q '[:find ?e :in $ % ?n
                    :where [?e :db/ident]
                            (byname ?n)]
                    db
                    nameruleset
                    pname)
       ;pe (ffirst rset)  ; the first ele in first tuple of the result
       ;attrs (keys pe)
       ]
    (prn rset)))

