(defproject colorcloud "0.1.0-SNAPSHOT"
  :description "colorcloud using datomic"
  :url "http://colorcloud.com"
  :license {:name "Proprietary ! Not in public domain"
            :url "www.colorcloud.com"}

  :datomic {
    :schemas ["resources/schema" ["seattle-schema.dtm"
                                  "seattle-data0.dtm"]]}

  ; prfiles map
  ;:db-uri "datomic:free://localhost:4334/colorcloud"
  :profiles {
    :dev {
      :datomic {
        :config "resources/sql-transactor-template.properties"
        :db-uri "datomic:sql://colorcloud?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic"
        }}}

  ; dependency vector
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/java.jdbc "0.0.6"]
                 [mysql/mysql-connector-java "5.1.6"]

                 ;[com.datomic/datomic-free "0.8.4215"]
                 [com.datomic/datomic-pro "0.9.4324"]
                 [datomic-schema "1.0.2"]  ; macro for db schema
                 [clj-redis "0.0.12"]   ;
                 [org.clojure/data.json "0.2.2"]    ; json package]
                 [clj-time "0.5.1"]       ; clj-time wraps Joda time
                 ]

  :main colorcloud.core)
