# Color Cloud

Inspired by Dr. Sugata Mitra's 2013 TED award talk, here is my effort to build a school in cloud. This is for my kids and other millions kids whose desire to learn restricted by their financial difficulties.

Dr. Mitra asked, what did the poor do wrong ? let's answer it!


## Datomic server configuration

When starting datomic, bin/transactor will print the URI for connecting,
datomic:free://localhost:4334/<DB-NAME>, means using free protocol. These are configured at free-transactor-template.properties.
To create a connection string, simply replace DB-NAME with your db name.

    db-uri = "datomic:free://localhost:4334/colorcloud"

    lein datomic start        # start datomic server first
    lein datomic initialize   # create db and load data
    lein run create-schema    # create schema
    lein run list-schema      # list all attributes in db
    lein run add-family       # run 2 times to add 2 family
    lein run create-homework  # run many times to create tons of homework
    lein run create-assignment
    lein run find-assignment  # get the assignment id and child id
    lein run submit-answer assignment-id child-id 
    lein run fake-comment     # fake a comment for testing

You can use repl to verify database is initialized properly. Note datomic db schema, config and db-uri are defined inside project.clj.


    lein repl
    user=> (require '[datomic.api :as d])
    user=> (def uri "datomic:free://localhost:4334/colorcloud")
    user=> (d/delete-database uri)
    user=> (d/create-database uri)
    user=> (def conn (d/connect uri))
    user=> (def db (d/db conn))

    user=> (def results (q '[:find ?c :where [?c :community/name]] db))
    user=> results
    user=> (d/delete-database uri)
    user=> (d/create-database uri)

    user=> (d/q '[:find ?atn :where [?ref ?attr] [?attr :db/ident ?atn]] db)

## Entity model

With clojure, there is only one abstraction, list. Hence we have a list of courses and a list of homeworks to a list of children, etc. We extensively use db.type/ref with :db.cardinality/many to model one-to-many relationships. For many-to-many relationship, we use a dedicated entity with many cardinality.

## Copyright

All copyright reserved !