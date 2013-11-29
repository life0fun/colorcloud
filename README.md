# Color Cloud

Inspired by Dr. Sugata Mitra's 2013 TED award talk, here is my effort to build a school in cloud. This is for my kids and other millions kids whose desires to learn were restricted by their financial difficulties.

Dr. Mitra asked, what did the poor do wrong ? let's answer it!


## Datomic server configuration

When starting datomic, bin/transactor will print the URI for connecting,
datomic:free://localhost:4334/<DB-NAME>, means using free protocol. These are configured at free-transactor-template.properties.
mainly set data and log dir and disable using ssl between peers and transactors.
To create a connection string, simply replace DB-NAME with your db name.

    db-uri = "datomic:sql://colorcloud?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic";

    lein datomic start &      # start datomic server first
    lein datomic initialize   # load data specified in project.clj datomic schemas.
    lein run create-schema    # create schema
    lein run list-schema      # list all attributes in db
    lein run add-family       # run 2 times to add 2 family
    lein run create-homework  # run many times to create tons of homework
    lein run create-assignment
    lein run find-assignment  # get the assignment id and child id
    lein run submit-answer assignment-id child-id 
    lein run fake-comment     # fake a comment for testing
    lein run create-course-lecture  # create a course and a list of lecture


Datomic database is persistence across restarts. To clean up databases, use `(d/delete-database uri) (d/create-database uri)`. You can use repl to verify database is initialized properly. Note datomic db schema, config and db-uri are defined inside project.clj.


    lein repl
    (require '[datomic.api :as d])
    (def uri "datomic:sql://colorcloud?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic")
    (def conn (d/connect uri))
    (def db (d/db conn))
    (d/delete-database uri)
    (d/create-database uri)

    (def results (q '[:find ?c :where [?c :community/name]] db))
    results
    (d/delete-database uri)
    (d/create-database uri)

    (d/q '[:find ?atn :where [?ref ?attr] [?attr :db/ident ?atn]] db)

or 
    bin/shell
    uri = "datomic:sql://colorcloud?jdbc:mysql://localhost:3306/datomic?user=datomic&password=datomic";
    conn = Peer.connect(uri);
    db = conn.db();
    Peer.q("[:find ?p :where [?p :parent/child]]", db); 


## Sync between GrowingTrees App

As colorcloud db is the back-end for GrowingTrees, we share most of datomic data accessor code. We make modification inside growingtree-server namespace and copy the datomic folder to here. We subs growingtree-server namespace keyword to colorcloud before we can use it.

    for f in *; do sed -i "" 's/growingtree-server/colorcloud/g' $f ; done;

## Copyright

All copyright reserved by the author !
