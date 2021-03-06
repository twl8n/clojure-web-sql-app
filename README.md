#### Clojure web sql application

This is a working example Clojure web application with a SQL database. The SQL is sqlite, which already exists
on all Macs, probably most Linux distros. I suspect Windows users will have to install SQLite.

This project uses Leiningen, which saves all kinds of headaches.

[Leiningen for automating Clojure projects without setting your hair on fire](https://leiningen.org)

You only need to create the database once. Every time you want to run the app, do the "lein run ..." as below.

This demo server runs on port 8080 instead of the normal http port 80. 

```
> cat schema.sql| sqlite3 expmgr.db
> lein run < /dev/null > run.log 2>&1 &
> cat run.log
2017-10-04 21:10:35.562:INFO:oejs.Server:jetty-7.6.8.v20121106
2017-10-04 21:10:35.678:INFO:oejs.AbstractConnector:Started SelectChannelConnector@0.0.0.0:8080
> jobs
[1]  + running    lein run < /dev/null > run.log 2>&1
# Just leave the job running in the backround to use the web app.
# When you are done, use the kill command to kill the web app.
> kill %1
```

To see the web application in action, in your web browser go to:

http://localhost:8080/app?action=list-all

Clojure web apps are web servers and use ring. Ring is jetty which is a standalone http server. Apache httpd
is not involved, so Clojure apps have a fairly different architecture than LAMP Perl or PHP web apps.


#### Errors

```
java.sql.SQLException: [SQLITE_ERROR] SQL error or missing database (no such table: entry)
```

You haven't created the database, or the db file is unreadable.
