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
                     "lein run help"
                ))

(defn -main [& args]
  (case (first args)
    "help" (doall (map prn help-info))
    "query" (dda/query)
    "schema" (dda/create-schema)
    (doall (map prn help-info))))
