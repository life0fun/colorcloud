;; datomic data accessor
(ns colorcloud.datomic.dda
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  (:require [datomic.api :as d])
  (:require [colorcloud.datomic.dbschema :as dbschema]
            [colorcloud.datomic.dbdata :as dbdata]
            [colorcloud.datomic.timeline :as timeline])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:require [clj-redis.client :as redis])    ; bring in redis namespace
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format :refer [parse unparse formatter]]
            [clj-time.coerce :refer [to-long from-long]]))

;
; http://blog.datomic.com/2013/05/a-whirlwind-tour-of-datomic-query_16.html
; query API results as a list of facts as list of tuples. [ [tuple1...] [tuple2...]]
; the intermediate value for joining are :db/id, can be in both [entity attr val] pos
; to join, the attribute col in tuple is the join col, and val.
; find ?e ret entity id, need to use (d/entity db ) to convert to lazy entity.
;  (d/entity db (ffirst (d/q '[:find ?e :where [?e :parent/fname]] db))) ;find parents that have fname
;
; query stmt is a list, or a map, with :find :in :where seps query args.
;   [?a …]  collection   [ [?a ?b ] ]  relation
;   [(predicate ...)] [(function ...) bindings]
;
; a named rule is a list of clause [(community-type ?c ?t) [?c :community/type ?t]])]
; a set of rules is a list of rules. [[[rule1 ?c] [_ :x ?c]] [[rule2 ?d] [_ :x ?d]]]
; rules with the same name defined multiple times in rule set make rule OR.
;   [[northern ?c] (region ?c :region/ne)] 
;   [[northern ?c] (region ?c :region/n)]
; Within the same rule, multiple tuples are AND.
;
;
; all the :ref :many attribute stores clojure.lang.MapEntry. use :db/id to get the id.
; knowing entity id, query with (d/entity db eid). otherwise, [:find $t :where []]
; (d/entity db eid) rets the entity. entity is LAZY. attr only availabe when touch.
; To add data to a new entity, build a transaction using :db/add implicitly 
; with the map structure (or explicitly with the list structure), a temporary id, 
; and the attributes and values being added.
;
; #db/id[partition-name value*] : value is an optional negative number.
; all instances of the same temp id are mapped to the same actual entity id in a given transaction, 
; {:db/id entity-id attribute value attribute value ... }
; [:db/add entity-id attribute value]
; (d/transact conn [newch [:db/add pid :parent/child (:db/id newch)]]
;
;
; In general, unique temporary ids are mapped to new entity ids.
; within the same transaction, the same tempid maps to the same real entity id.
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
; entity-id can be used at both side of the datom, e.g., give a parent entity id,
;   (d/q '[:find ?e :in $ ?attr :where [17592186045703 ?attr ?e]] db :parent/child)
;   (d/q '[:find ?e :in $ ?attr :where [?e ?attr 17592186045703]] db :child/parent)
;

; outbound query and inbound query
; using parent id, get list of children
;   (:parent/child (d/entity db 17592186045476))
;
; inbound query(who refed me) is used for query another entity that refs this entity. 
; parent entity can be used to query all child entity that refs to this parent entity.
; use inbound query with convention is prefix attr name with _.
;   (:child/_parent (d/entity db 17592186045476))
; = (:parent/child (d/entity db 17592186045476))
;   (-> (d/entity db 17592186045476) :child/_parent)
; this reverse look-up might be time consuming, use explicit linking might be better.
;
; (map (fn [id] (d/touch (d/entity db (:db/id id)))) 
;   (-> (d/entity db 17592186045476) :child/_parent))
;
; (d/q '[:find ?e :in $ ?x :where [?e :child/parent ?x]] db (:db/id p))

;
;

;; store database uri
(defonce uri "datomic:free://localhost:4334/colorcloud")
;; connect to database and the db
(def conn (d/connect uri))
(def db (d/db conn))


(declare find-parent-by-cid)
(declare find-parent-by-cname)

;; parse schema dtm file
;(def schema-tx (read-string (slurp "./resource/schema/seattle-schema.dtm")))
;; parse seed data dtm file
;(def data-tx (read-string (slurp "./resource/schema/seattle-data0.dtm")))

; rules to find all parent or child with the name, using rules for OR logic
(def nameruleset '[[[byname ?e ?n] 
                   [?e :parent/fname ?n]]  ; multiple tuples within a rule are AND.
                  [[byname ?e ?n]
                   [?e :parent/lname ?n]]
                  [[byname ?e ?n]
                   [?e :child/fname ?n]]
                  [[byname ?e ?n]
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


; show entity by id
(defn show-entity-by-id
  "show all attrs and values of the entity by id"
  [eid]
  (let [e (d/touch (d/entity db eid))  ; touch to reify all attributes.
        attrs (keys e)]
    (prn "--------- " eid " ----------------")
    (doseq [a attrs]
      (prn a  (a e)))))


(defn add-family
  "insert two parents with two children"
  []
  (let [tmplparent (dbdata/create-parent)
        tmprparent (dbdata/create-parent)
        tmplch (dbdata/create-child)
        tmprch (dbdata/create-child)
        lch (assoc tmplch :child/parent [(:db/id tmplparent) (:db/id tmprparent)])
        rch (assoc tmprch :child/parent [(:db/id tmplparent) (:db/id tmprparent)])
        lparent (assoc tmplparent :parent/child [(:db/id lch) (:db/id rch)])
        rparent (assoc tmprparent :parent/child [(:db/id lch) (:db/id rch)])
        ]
    (prn "inserting " lch rch lparent rparent)
    (d/transact conn [lch rch lparent rparent])))


; :find rets entity id, find all parent's pid and name.
(defn list-parent
  "find all parents with all children"
  []
  (let [pc (d/q '[:find ?p :where [?p :parent/child]] db)] ;?p parent who has children
    (map (comp show-entity-by-id first) pc)))


; use :db/add to upsert child attr to parent. find parent eid by list-parent.
; entity is a map of attributes. insert ref attr, must use refed entity id.
; [:db/add entity-id attribute value]
(defn insert-child
  "insert a children to parent by parent id, pid must be num, not string"
  [pid]  ; passed in pid is a num
  (let [pe (d/entity db pid)   ; get the lazy entity by id
        ch (:parent/child pe)
        newch (assoc (dbdata/create-child) :child/parent pid)]
    (d/transact conn [ newch
                      [:db/add pid :parent/child (:db/id newch)]])
    (prn pid pe ch newch)))


; [:db/add entity-id attribute value]
(defn link-parent-child
  "link child to parent by parent id and child id"
  [pid cid]
  (let [parent (d/entity db pid)
        child (d/entity db cid)]
    (d/transact conn [[:db/add pid :parent/child cid]
                      [:db/add cid :child/parent pid]])))


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
        ;parent (-> ce (:parent/_child))   ; inbound(who refed me) might be slow.
        parent (:child/parent ce)  ; :ref :many rets a map, each tuple is a  clojure.lang.MapEntry.
        ]
    (prn parent)
    (map (comp show-entity-by-id :db/id) parent)))  ; eid is the 1st in a ret tuple.


; search all fname and lname to check whether there is a match
(defn find-parent-by-cname
  "find the parent of a child by its name"
  [clname cfname]
  (let [fname (first cfname)
        ; args needs to bind to ?var to pass into query
        rset (d/q '[:find ?p :in $ % ?n
                    :where [?p :parent/child ?e]  ; join parent entity that child entity equals
                           [?e :child/parent]  ; for child entity that has parent
                           (byname ?e ?n)]     ; its fname or lname mateches ?
                db
                nameruleset
                (str clname))
        ]
    (doseq [pid rset] 
      ((comp show-entity-by-id first) pid))
    (prn clname rset)))


; find a person by name, use set/union as sql union query.
(defn find-by-name 
  "find a person by either first name or last name"
  [pname]
  (let [parent (d/q '[:find ?e :in $ % ?n
                      :where [?e :parent/child]
                             (byname ?e ?n)]
                    db
                    nameruleset
                    pname)
        child (d/q '[:find ?e :in $ % ?n
                     :where [?e :child/parent]  ; query child
                             (byname ?e ?n)]
                    db
                    nameruleset
                    pname)
        all (clojure.set/union parent child)  
      ]
    (prn parent child all)
    (map (comp show-entity-by-id first) all)))


; list an entity attribute's timeline
(defn timeline
  "list an entity's attribute's timeline "
  [eid attr]
  (let [txhist (timeline/timeline eid attr)]
    (doseq [t txhist]
      (show-entity-by-id (first t))
      (show-entity-by-id (second t)))))

; list a person's all transaction timeline
(defn person-timeline
  "list a person's transaction timeline"
  [eid]
  (let [txhist (timeline/person-timeline eid)]
    (doseq [t txhist]
      (prn t)
      (show-entity-by-id (first t)))))


