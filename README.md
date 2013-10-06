# Color Cloud

Inspired by Dr. Sugata Mitra's 2013 TED award talk, here is my contribution to it. This is for my kids and other millions kids whose desire to learn restricted by their financial difficulties.

Dr. Mitra asked, what did the poor do wrong ? let's answer it!


## Datomic server configuration

When starting datomic, bin/transactor will print the URI for connecting,
datomic:free://localhost:4334/<DB-NAME>, means using free protocol. These are configured at free-transactor-template.properties.
To create a connection string, simply replace DB-NAME with your db name.

  db-uri = "datomic:free://localhost:4334/colorcloud"

  lein datomic start        # start datomic server first
  lein datomic initialize   # create db and load data


You can use repl to verify database is initialized properly. Note datomic db schema, config and db-uri are defined inside project.clj.

  lein repl
  user=> (use '[datomic.api :only [q db] :as d])
  user=> (def uri "datomic:free://localhost:4334/colorcloud")
  user=> (def conn (d/connect uri))
  user=> (def results (q '[:find ?c :where [?c :community/name]] (db conn)))
  user=> results

## 