(ns expense-mgr.core
  (:require [clojure.java.jdbc :as jdbc] ;; :refer :all]
            [clojure.tools.namespace.repl :as tns]
            [clojure.string :as str]
            [clojure.pprint :refer :all]
            [clostache.parser :refer [render]]
            [ring.adapter.jetty :as ringa]
            [ring.util.response :as ringu]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]])
  (:gen-class))


(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     "expmgr.db"
   })

;; outer is a map with a key :category_list, the value of which is a list of maps with key :id.
;; Make a set of the :id values and if that id exists in the inner map, assoc :selected with the inner map

;; This creates an inner map suitable for html <select>, or multi <select> now that we have 0..n categories
;; per entry.

;; Now that this is working, the let binding seem redundant. It might read better to simply inline
;; the set function.

(defn map-selected [outer inner]
  "Create a data structure for categories to populate an html select element in a Clostache template. Map
  inner and conditionally assoc :selected key, mapping the result via assoc into outer as :all-category. Add
  key :all-category to outer, and while setting :selected appropriately on each iteration
  of :all-category. Map assoc, map conditional assoc. And :checked with value checked."
  (map (fn [omap]
         (assoc omap :all-category
                (map (fn [imap] 
                       (let [catset (set (mapv :id (:category_list omap)))]
                         (if (contains? catset (:id imap)) 
                           (assoc imap :selected "selected" :checked "checked")
                           imap))) inner))) outer))

(def cat-report-sql
"select * from 
        (select 
        (select name from category where id=entry.category) as category,
        sum(amount) as sum 
        from entry 
        group by category) 
as inner order by sum")

(defn cat-report
  "The template expects fields 'category' and 'sum'."
  []
  (let [recs (jdbc/query db [cat-report-sql])
        total (jdbc/query db ["select 'Total' as category, sum(amount) as sum from entry"])]
    {:rec-list (concat recs total)}))

;; insert into etocat (eid,cid) (select id,category from entry);
;; create table ecat as select id,category from entry;

(def all-cats-sql
"select 
   name as category_name,category.id 
 from entry, category, etocat
 where 
   entry.id=etocat.eid and
   category.id=etocat.cid and 
   entry.id=?
   order by category.id")

(defn list-all-cats
  "List all categories. Takes an entry id, returns a map of all category data matching the entry id."
  [eid]
  {:category_list (jdbc/query db [all-cats-sql eid])})

;; name from category where category.id=entry.category

;; Can use SQL to set col "selected" as true when entry.category = category.id
;; Or can use clojure.

(def show-sql
"select entry.*,(select name from category where category.id=entry.category) as category_name 
 from entry 
 where entry.id=?")

(defn show
  "Get an entry record, and get all necessary category info for the user interface."
  [params]
  (let [id (get params "id")
        erecs (jdbc/query db [show-sql id])
        full-recs (map (fn [rec] (merge rec (list-all-cats (:id rec)))) erecs)
        cats (jdbc/query db ["select * from category order by name"])]
    (map-selected full-recs cats)))


(defn choose
  "Get a single entry record matching the title param."
  [params]
  (let [title (params "title")]
    (when (not (nil? title))
      (jdbc/query db ["select * from entry where title like ? limit 1" (format "%%%s%%" title)]))))


;; using-year is a def that is an fn, and has a local, internal atom uy. Basically using-year is a closure. So
;; that is safe and good programming practice, maybe.

(def using-year
  (let [uy (atom "2017")]
    (fn 
      ([] @uy)
      ([different-year]
       (if (some? different-year)
         (swap! uy (fn [xx] different-year))
         @uy)))))

(defn check-uy [cmap]
  "Check for a missing using-year, and default to 2017. Expect empty string for missing map values, not nil,
  because nil breaks re-matches."
  (let [date (:date cmap)
        uy (:using_year cmap)
        full-match (re-matches #"^(\d{4})-\d{1,2}-\d{1,2}$" date)]
    (cond (some? full-match)
          (second full-match)
          (not (empty? uy))
          uy
          :else "2017")))

(defn smarter-date
  "This is fine, but doesn't normalize dates. Probably better to tokenize dates, and reformat in a normal format."
  [sdmap]
  (let [date (:date sdmap)
        uy (:using_year sdmap)
        full-match (re-matches #"^(\d{4})-\d{1,2}-\d{1,2}$" date)]
    (cond (some? full-match)
          date
          (re-matches #"^-+\d{1,2}-\d{1,2}$" date)
          (str uy date)
          (re-matches #"^\d{1,2}-\d{1,2}$" date)
          (str uy "-" date)
          :else
          (str uy "-" date))))

(defn test-smarter-date
  "A convenience function to validate smarter-date"
  []
  [(smarter-date {:date "-06-01" :using_year "2001"})
   (smarter-date {:date "06-01" :using_year "2001"})])

(defn update-db [params]
  "Update entry. Return a list of a single integer which is the number of records effected, which is what
  jdbc/execute!  returns. On error return list of zero."
  (let [id (params "id")
        date (params "date")
        category (params "category")
        amount (params "amount")
        mileage (params "mileage")
        notes (params "notes")]
    (cond (not (nil? (params "id")))
          (do
            (println "category: " (type (params "category")))
            (jdbc/execute! db 
                           ["update entry set date=?,amount=?,mileage=?,notes=? where id=?"
                            date amount mileage notes id])
            (jdbc/execute! db 
                           ["delete from etocat where eid=?" id])
            ;; Tricky. Lazy map doesn't work here. This is side-effect-y, so perhaps
            ;; for or doseq would be more appropriate. That said, we might want the return value of execute!.
            (mapv 
             (fn [cid]
               (println "inserting cid: " cid)
               (jdbc/execute! db 
                              ["insert into etocat (eid,cid) values (?,?)" id cid])) category))
          :else (do
                  (prn "no id in params:" params)
                  '(0)))))


(defn cstr
  "Output pretty print of str. Unclear why I named this function cstr."
  [str] (str/replace (with-out-str (pprint str)) #"\n" "\n"))


(def list-all-sql
  "select entry.*,(select name from category where category.id=entry.category) as category_name 
from entry
order by entry.id")

(defn list-all [params]
  (let [erecs (jdbc/query db [list-all-sql])
        full-recs (map (fn [rec] (merge rec (list-all-cats (:id rec)))) erecs)
        cats (jdbc/query db ["select * from category order by name"])
        all-rec (assoc {:all-recs (map-selected full-recs cats)} :all-category cats)]
    all-rec))

;; {:last_insert_rowid() 12} The key really is :last_insert_rowid() with parens. The clojure reader simply
;; can't grok a key with parens, so we have to use keyword.

(defn insert [params]
  "map of params => integer record id."
  (let [kmap (jdbc/db-do-prepared-return-keys
              db
              ["insert into entry (date,category,amount,mileage,notes) values (?,?,?,?,?)"
               (params "date")
               (params "category")
               (params "amount")
               (params "mileage")
               (params "notes")])]
    [{:id (get kmap (keyword "last_insert_rowid()"))}]))

(defn fill-list-all
  "Fill in a list of all records."
  [rseq]
  (let [template (slurp "list-all.html")]
    (render template rseq)))

(defn render-any
  "Render rseq to the template file template-name. Clostache/moustache template rendering wrapper."
  [rseq template-name]
  (let [template (slurp template-name)]
    (render template rseq)))

(defn edit
  "Map each key value in the record against placeholders in the template to create a web page."
  [record]
  (let [template (slurp "edit.html")
        body (render template record)]
    body))

(defn request-action
  "Call functions based on http request parameters."
  [working-params action]
  (cond (= "show" action)
        (map #(assoc % :sys-msg (format "read %s from db" (get working-params "id"))) (show working-params))
        (= "choose" action)
        (choose working-params)
        (= "update-db" action)
        (do 
          (update-db working-params)
          ;;(map #(assoc % :sys-msg "updated") (show working-params))
          (list-all working-params))
        (= "list-all" action)
        (list-all working-params)
        (= "insert" action)
        (list-all (first (insert working-params)))
        (= "catreport" action)
        (cat-report)))

(defn reply-action
  "Generate a response aka a reply for some http request."
  [rmap action]
  (cond (or (nil? rmap)
            (nil? (some #{action} ["show" "list-all" "insert" "update-db" "catreport"])))
        ;; A redirect would make sense, maybe.
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (format
                "<html><body>Unknown command: %s You probably want: <a href=\"app?action=list-all\">List all</a></body</html>"
                action)}
        (= "show" action)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (edit (assoc (first rmap) :using_year using-year))}
        (or (= "list-all" action) (= "insert" action) (= "update-db" action))
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (fill-list-all (assoc rmap :sys-msg "list all" :using_year using-year))}
        (= "catreport" action)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body (render-any (assoc rmap :sys-msg "Category report" :using_year using-year) "catreport.html")}))

;; todo: change params keys from strings to clj keywords.
(defn handler 
  "Expense link manager."
  [request]
  (let [temp-params (:params request)
        action (temp-params "action")
        ras  request
        using-year (check-uy {:date (or (temp-params "date") "") :using_year (or (temp-params "using_year") "")})
        ;; Add :using_year, replace "date" value with a better date value
        working-params (merge temp-params
                              {:using_year using-year
                               "date" (smarter-date {:date (or (temp-params "date") "") :using_year using-year})})
        rmap (request-action working-params action)]
    (reply-action rmap action)))

;; def app is ring-fu. The http request is passed through various wrappers that modify the request making it useful
;; and easier to work with. The modifications are standard stuff in the Apache httpd world, but ala carte here
;; in Clojure ring jetty world.

(def app
  (wrap-multipart-params (wrap-params handler)))

;; https://stackoverflow.com/questions/2706044/how-do-i-stop-jetty-server-in-clojure
;; example
;; (defonce server (run-jetty #'my-app {:port 8080 :join? false}))

;; Unclear how defonce and lein ring server headless will play together. We don't much care because we only use
;; lein run. I suspect lein ring isn't working for this app.

(defn ds []
  (defonce server (ringa/run-jetty app {:port 8080 :join? false})))

;; We need -main for 'lien run', but it is ignored by 'lein ring'.
(defn -main []
  (ds))

(defn makefresh
  "This is a helper function when working in the repl."
  []
  (.stop server)
  (tns/refresh)
  (ds)
  (.start server))

