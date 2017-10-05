#### Clojure web sql application

This is a working example Clojure web application with a SQL database. The SQL is sqlite, which already exists
on all Macs, probably most Linux distros. I suspect Windows users will have to install SQLite.

This project uses Leiningen, which saves all kinds of headaches.

[Leiningen for automating Clojure projects without setting your hair on fire](https://leiningen.org)

```
> cat schema.sql| sqlite3 expmgr.db
> lein run < /dev/null > run.log 2>&1 &
> cat run.log
2017-10-04 21:10:35.562:INFO:oejs.Server:jetty-7.6.8.v20121106
2017-10-04 21:10:35.678:INFO:oejs.AbstractConnector:Started SelectChannelConnector@0.0.0.0:8080
> jobs
[1]  + running    lein run < /dev/null > run.log 2>&1
> kill %1
```

In your web browser go to:

http://localhost:8080/app?action=list-all

Clojure web apps are web servers and use ring. Ring is a jetty server. Apache httpd is not involved, so
Clojure apps have a fairly different architecture than LAMP Perl or PHP web apps.


#### Errors

```
java.sql.SQLException: [SQLITE_ERROR] SQL error or missing database (no such table: entry)
```

You haven't created the database, or the db file is unreadable.
