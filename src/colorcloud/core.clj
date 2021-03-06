(ns colorcloud.core
  (:require [clojure.string :as str]
            [clojure.pprint :refer :all])
  (:import [java.io FileReader]
           [java.util Map Map$Entry List ArrayList Collection Iterator HashMap])
  (:require [clj-redis.client :as redis]) ; bring in redis namespace
  (:require [clj-time.core :as clj-time :exclude [extend]]
            [clj-time.format]
            [clj-time.local])
  (:require [colorcloud.datomic.dda :as dda])  ; datomic data accessor
  (:gen-class :main true))


(def help-info (list " ---------------------------------"
                     "lein datomic start &"
                     "lein datomic initialize  # for running transactor"
                     "lein run create-schema"
                     "lein run list-schema"
                     "lein run add-family (add 2 families)"
                     "lein run create-homework (multiple times)"
                     "lein run create-assignment"
                     "lein run find-assignment"
                     "lein run submit-answer assignment-id child-id  <= from find-assignment"
                     "lein run find-answer"
                ))

(defn -main [& args]
  (case (first args)
    "help" (doall (map prn help-info))
    "create-schema" (dda/create-schema)
    "list-schema" (dda/list-attr (second args))
    "show-entity" (dda/show-entity-by-id (read-string (last args)))
    "list-parent" (dda/list-parent)
    "add-family" (dda/add-family (second args))
    "insert-child" (dda/insert-child (read-string (last args)))
    "find-children" (dda/find-children)
    "find-parent" (dda/find-parent (second args) (last args))
    "find-by-name" (dda/find-by-name (second args))
    "timeline" (dda/timeline (read-string (second args)) (last args))
    "person-timeline" (dda/person-timeline (read-string (second args)))
    "create-homework" (dda/create-homework)
    "find-homework" (dda/find-homework)
    "create-assignment" (dda/create-assignment)
    "find-assignment" (dda/find-assignment)
    "fake-comment" (dda/fake-comment)
    "find-comment" (dda/find-comment)
    "find-answer" (dda/find-answer)
    "submit-answer" (dda/submit-answer (read-string (second args)) (read-string (last args)))
    "create-course-lecture" (dda/create-course-and-lecture)
    "find-course" (dda/find-course)
    "find-lecture" (dda/find-lecture)
    "add-course-lecture" (dda/add-course-lecture (read-string (second args)) (read-string (last args)))
    "rm-course-lecture" (dda/rm-course-lecture (read-string (second args)) (read-string (last args)))
    (doall (map prn help-info))))
