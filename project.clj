(defproject colorcloud "0.1.0-SNAPSHOT"
  :description "colorcloud using datomic"
  :url "http://colorcloud.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :datomic {
    :schemas ["resources/schema" ["seattle-schema.dtm"
                                  "seattle-data0.dtm"]]}
  :profiles {
    :dev {
      :datomic {
        :config "resources/free-transactor-template.properties"
        :db-uri "datomic:free://localhost:4334/colorcloud"}}}

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.datomic/datomic-free "0.8.4159"]
                 [datomic-schema "1.0.2"]  ; macro for db schema
                 [clj-redis "0.0.12"]   ;                           
                 [org.clojure/data.json "0.2.2"]    ; json package]
                 [clj-time "0.5.1"]       ; clj-time wraps Joda time
                 ]

  :main colorcloud.core)
