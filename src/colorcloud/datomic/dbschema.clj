(ns colorcloud.datomic.dbschema
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.data.json :as json])
  (:require [datomic.api :only [q db] :refer [q db] :as d]
            [datomic-schema.schema :refer :all :as dschema]))

;
; datomic db stores are repr by datoms. Each datom is an addition or retraction
; of a relation between an entity, an attribute, a value, and a transaction.
; db schema defines all the attributes that can be associated with entities.
; schema does not tell which attributes link to which entities. App wires them up !
; 
; schema attributes is the same data model for app, so no ORM needed.
; Every attributes is defined by three required attributes.
;   :db/ident - unique name in :<namespace>/<name>
;   :db/valueType - [:db.type/string, boolean, long, ref, instant(time) ]
;   :db/cardinality - attribute values, :db.cardinality/one, many, 
;   :db/unique - like pk, implies :db/index. possible values are
;     :db.unique/value no upsert. :db.unique/identity upsert merge temp id to db value.
;   :db/index 
;   :db/fulltext
;   :db/noHistory - default we store store all past value.
;
;
; all defined attributes in :db.install/attribute, query by ident, list all.
; (q '[:find ?e :in $ ?attr :where [?e :db/ident ?attr]] (db conn) :parent/status)
; (q '[:find ?e :in $ ?attr :where [?e :db/ident ?attr]] (db conn) :db.part/app)
; (q '[:find ?attr :where [_ :db.install/attribute ?attr]] (d/db conn)) 
; (:db/ident (d/entity (d/db conn) 78))  ; to print entity ident
; or with npm install datomicism -g
; 
; using datomic-schema to define attr, the tempid set by {:db/id (d/tempid :db.part/db)
; whe inserting, temporary ids with the same partition and negative number value are 
; mapped to the same entity id. 
;   {:db/id #db/id[:db.part/user -1000460], :district/name "Greater Duwamish"}
;
; how can we do compound unique key ?
; :db/unique implies :db/index. 
; :db.unique/value - attempts to insert a duplicate value for a different entity id will fail
; :db.unique/identity - attr val unique to each entity and "upsert" is enabled; merge.

; app partition
(defpart app)

; parent namespace with all attributes
(defschema person
  (part app)
  (fields
    [firstname :string :indexed :fulltext]
    [lastname :string :indexed :fulltext]
    [age :long]
    [address :string :fulltext]
    [gender :string "use M and F repr gender string"]
    [email :string :many :indexed :fulltext]
    [phone :string :many :indexed :fulltext]
    [contact :ref :many "contact list of the peroson"]
    [location :ref :many "location list of a person, most recent"]
    [likes :long "persons popularity"]
    [status :enum [:pending :active :inactive :cancelled]]))


(defschema parent
  (part app)
  (fields
    [person :ref :one "basic human info"]   ; one identity for one parent
    [child :ref :many "a list of parent's children"]
    [assignemnt :ref :many "all assignments parents assigned to kids"]
    [friends :ref :many "a list of friends of parents"]))  ; the friends of parents


; children namespace with all attributes
(defschema child
  (part app)
  (fields
    [person :ref :one "basic human info"]  ; one identity for one child
    [parent :ref :many "a list of kids parents"] ;
    [friends :ref :many "a list of kids friends"]
    [classmates :ref :many "classmate of the kid"]
    [grade :enum [:first :second :third :fourth :fifth :sixth :seventh :freshman :junior :senior]]
    [activity :ref :many "kids digital activities, calls, sms, app usages, etc"]
    [assignment :ref :many "list of assignments to child"]
    [comments :ref :many "can we comment child's performance ?"]))


; so questions or github project or online streaming courses
(defschema homework
  (part app)
  (fields
    [category :enum [:math :science :reading :coding :art :gym :reporting :game] "assignment category"]
    [title :string :fulltext]
    [body :string :fulltext]
    [author :ref :many "the author of the homework"]
    [uri :uri "uri of the homework, if any"]
    [course :ref :many "which course this homework related to"]
    [upvotes :long "does people like this assignment ?"]
    [solvecnt :long "how many children solved this problem"]
    [topanswer :ref :many "a list of top answers"]
    [comments :ref :many "comments for the homework"]))  ; a list of answers with 


; online streaming a course
(defschema course
  (part app)
  (fields
    [category :enum [:math :science :reading :coding :art :gym :reporting :game] "course category"]
    [title :string :fulltext]
    [body :string :fulltext]
    [uri :uri "content uri of the course, can be video, audio, weburl"]
    [author :ref :many "the author, teacher of the course"]
    [schedule :enum [:M :T :W :TH :F :SA :S] :many "weekday schedule"]
    [hour :long :many "hour schedule"]
    [starttime :instant :many "absolute start time"]
    [homework :ref :many "homeworks for the course"]
    [comments :ref :many "course comments"]))


(defschema assignment
  (part app)
  (fields
    [homework :ref :one "one assignment to one child at a time. batch assignment later"]
    [course :ref :one "one assignment to one child to take the course"]
    [type :enum [:homework :course] "solve a homework or take a course"]
    [byparent :ref :one "assignment created by one parent"]
    [bychild :ref :one "assignment can also be created by child, for extension"]
    [tochild :ref :many "make one assignment to one child, or many children ?"]
    [status :enum [:pending :active :overdue :cancelled] "status of assignment"]
    [hint :string :many "hints to the assignment"]
    [related :ref :many "similar or related assignment"]
    [wathcer :ref :many "watchers of the assignment"]
    [answer :ref :many "a list of answers to the assignment"]
    [comments :ref :many "the comments tree for the answer"]
    [start :instant "starting time of the assignment"]
    [due :instant "due time"]))


(defschema answer
  (part app)
  (fields
    [assignment :ref :one "one answer to one child assignment"]
    [child :ref :one "each child must submit one answer"]
    [score :long "score of the answer"]
    [comments :ref :many "the comments tree for the answer"]))


; comment tree models conversation, engaging all participants, most important part!
(defschema comments
  (part app)
  (fields
    [author :ref :one "the author of the comments"]
    [body :string :fulltext "the body of a comment"]
    [subject :ref :one "the subject comments made to, ref to any entity"]
    [upvotes :long "how many upvotes"]))


(defn entity-attr
  "display all attributes of an entity by its id, passed in eid is in a list [eid]"
  [db eid]
  (let [e (d/entity db (first eid))
        attrs (keys e)
        tostr (reduce (fn [o c] (str o " " c "=" (c e))) (str (first eid) " = ") attrs)]
    ;(pprint/pprint tostr)
    tostr))


(defn create-schema
  "create schema using datomic-schema in db connection"
  [dbconn]
  (d/transact dbconn (build-parts d/tempid)) ;(d/tempid partition) gen tempid in specific partition
  (d/transact dbconn (build-schema d/tempid)))


(defn list-attr
  "list all attributes for ident, if no ident, list all"
  ([db]  ; db is (d/db conn)
    (let [eid (q '[:find ?attr :where [_ :db.install/attribute ?attr]] db)]
      (prn "list all attr " eid)
      (map (partial entity-attr db) eid)))
  ([db ident]
    (let [eid (q '[:find ?e :in $ ?attr :where [?e :db/ident ?attr]] db ident)]
      (map (partial entity-attr db) eid))))


