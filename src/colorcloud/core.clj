(ns colorcloud.core
  (:require [clojure.string :as str]
            [clojure.pprint :refer :all])
  (:require [datomic.api :as d :refer [db q]])
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
                ))

(defn -main [& args]
  (case (first args)
    "help" (doall (map prn help-info))
    "query" (dda/query)
    "create-schema" (dda/create-schema)
    "list-schema" (dda/list-attr (second args))
    "insert-data" (dda/insert-person "john" "doe")
    "list-parent" (dda/list-parent)
    "insert-parent" (dda/insert-parent)
    (doall (map prn help-info))))
