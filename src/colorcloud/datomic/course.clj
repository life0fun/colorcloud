;; datomic data accessor
(ns colorcloud.datomic.course
  (:import [java.io FileReader]
           [java.net URI]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format :refer [parse unparse formatter]]
            [clj-time.coerce :refer [to-long from-long]])
  (:require [datomic.api :as d])
  (:require [colorcloud.datomic.dbschema :as dbschema]
            [colorcloud.datomic.dbconn :as dbconn :refer :all]))

;
; http://blog.datomic.com/2013/05/a-whirlwind-tour-of-datomic-query_16.html
; query API results as a list of fact tuples. Fact tuple is a list of entity Ids.
;   [ [tuple1...] [tuple2...] ]
;
; the intermediate value for joining are :db/id, can be in both [entity attr val] pos
; to join, the attribute col in tuple is the join col, and val.
;
; find ?e ret entity id, (d/touch(d/entity db) to convert to lazy entity.
;   (d/touch (d/entity db (ffirst (d/q '[:find ?e :where [?e :parent/fname]] db)))
;
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
; all eids must be number, use read-string to convert from command line. 
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
; d/transact tx-data is a list of lists, each of which specifies a write operation
; for single write datom(map tuple), wrap with [datom-tuple]
; for a list of write datom tuple from map, use (vec ([] [])) to wrap them.
; (d/transact conn (vec (map make-attr (d/q '[:find ?e :where []))))
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
; (keys e) or (:parent/child (d/entity db 17592186045703))
; entity attr rets a map entry for all children. (:parent/child (d/entity db pid))
; 
; entity-id can be used at both side of the datom, e.g., give a parent entity id,
;   (d/q '[:find ?e :in $ ?attr :where [17592186045703 ?attr ?e] [...] ] db :parent/child)
;   (d/q '[:find ?e :in $ ?attr :where [?e ?attr 17592186045703] [...] ] db :child/parent)
; query :where takes a vardic list, not a list of list.
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


(declare create-lecture)
(declare create-course-coding)


; for course and lectures
(defn course-attr
  "compose a map of attrs for a course entity"
  [subject title overview materials contenturi]
  (let [m {:db/id (d/tempid :db.part/user)
          :course/subject subject
          :course/title title
          :course/overview overview
          :course/materials materials
          :course/contenturi contenturi}]
    (prn m)
    m))

(defn lecture-attr
  "compose a map of attrs for a course lecture"
  [course seqno date topic content videouri]
  (let [m {:db/id (d/tempid :db.part/user)
          :lecture/course course
          :lecture/seqno seqno
          :lecture/date date
          :lecture/topic topic
          :lecture/content content
          :lecture/videouri videouri}]
    (prn m)
    m))


; create homework to be assigned
(defn create-course
  "create a course "
  ([]
    (create-course :coding))

  ([subject]
    (case subject
      :coding (create-course-coding)
      "default")))


; create course and lecture together
(defn create-course-and-lecture
  "create a course, and a batch of lecture in one transaction"
  []
  (let [cm (create-course)
        cid (:db/id cm)
        lecm (create-lecture cid)
        lid (:db/id lecm)
        clm (assoc cm :course/lectures [lid])]  ; for :many field, add with [lid] or lid, db will handle it.
    (prn clm)
    (prn lecm)
    (submit-transact [clm lecm])))


; the enum must be fully qualified, :homework.subject/math
(defn create-course-coding
  "create a simple math course and lectures"
  []
  (let [subject :course.subject/coding
        title "learning datomic"
        credit 3
        overview (str "datomic is a database as value based on clojure, awesome !")
        materials (str "http://docs.datomic.com/tutorial.html")
        contenturi (URI. "http://docs.datomic.com/")
        coursem (course-attr subject title overview materials contenturi)]
    ;(d/transact conn [coursem])
    coursem))


; create an online course
(defn create-lecture
  "create a course lecture for certain course id"
  [cid]
  (let [lectseq (str "1b")
        lecdate (.toDate (clj-time/date-time 2013 11 25 10 20))
        topic (str "The day of datomic")
        content (str "The Day of Datomic project is a collection of samples and tutorials for learning Datomic")
        videouri (URI. "https://github.com/Datomic/day-of-datomic")
        lecturem (lecture-attr cid lectseq lecdate topic content videouri)]
    ;(d/transact conn [lecturem])
    lecturem)) ; tx-data is a list of write datoms


; find a course
(defn find-course
  "find course by subject, ret a list of course entity"
  []
  (let [subject :course.subject/coding
        ; get a vec of [[course-id lecture-id] [] ...]
        eids (d/q '[:find ?c ?l           ; ret both course id and lecture id
                    :in $ ?sub 
                    :where [?c :course/lectures ?l]    ; all courses that have lectures
                    ] ; all lectures of the course
                db 
                subject)
        cids (map first eids)  ; always ret the first homework to assign.
        lids (map second eids)]
    ; (prn "cids" cids)
    ; (prn "lids" lids)
    (show-entity-by-id (first cids))
    (show-entity-by-id (first lids))
    ; [ [cid lid] [cid lid]], ret course entity map
    (map (comp get-entity first) eids)))  


(defn find-lecture
  "find all lectures, ret a vector of lecture entities"
  []
  (let [lids (d/q '[:find ?l :where [?l :lecture/course]] db)
        entities (map (comp get-entity first) lids)]  ; eid is the first of result tuple
    (map (comp show-entity-by-id first) lids)
    entities))


; linking a lecture to a course, ref attr's val is numeric id value.
(defn add-course-lecture
  "adding a lecture to a course by setting ref attr with id numeric value"
  [cid lid]
  (let [ccode [:db/add cid :course/lectures lid]
        lcode [:db/add lid :lecture/course cid]]
    (submit-transact [ccode lcode])
    (show-entity-by-id cid)
    (show-entity-by-id lid)))


; retract the lecture from a course
(defn rm-course-lecture
  "remove a lecture from a course by setting ref attr with id numeric value"
  [cid lid]
  (let [ccode [:db/retract cid :course/lectures lid]
        lcode [:db/retract lid :lecture/curse cid]]
    (submit-transact [ccode])
    (show-entity-by-id cid)
    (show-entity-by-id lid)))
