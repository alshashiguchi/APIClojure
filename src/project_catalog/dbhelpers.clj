(ns project-catalog.dbhelpers
  (:require [monger.core :as mg]
            [monger.collection :as mc]
            [monger.json]))

(defn db-get-project [proj-name]
  (let [connect-string (System/getenv "MONGO_CONNECTION")
  {:keys [conn db]} (mg/connect-via-uri connect-string)]
  (mc/find-maps db "project-catalog" {:proj-name proj-name})
  )
)          