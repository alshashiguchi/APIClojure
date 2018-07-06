(ns project-catalog.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [io.pedestal.http.route.definition :refer [defroutes]]

            [io.pedestal.interceptor.helpers :refer [definterceptor defhandler]]

            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.data.xml :as xml]

            [project-catalog.dbhelpers :as db]

            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.json]

            [ring.util.response :as ring-resp]))            

(defn get-by-tag [proj-map-in tname]
  (->> proj-map-in
     :content
     (filter #(= (:tag %) tname))
     first
     :content
     first
  )
)
          
(defn auth0-token []
  (let [ret
    (client/post "https://jemez.auth0.com/oauth/token"
         {:debug false
          :content-type :json
          :form-params {:client_id (System/getenv "AUTH0_CLIENT_ID")
                        :client_secret (System/getenv "AUTH0_SECRET")
                        :grant_type "client_credentials"}
                        })
          ]
    (json/read-str (ret :body))
    )
  )    
  
(defn auth0-connections [tok]
  (let [ret
  (client/get "https://jemez.auth0.com/api/connections"
        {:debug false
         :content-type :json
         :accept :json
         :headers {"Authorization" (format "Bearer %s" tok)}
         }
        )
  ]
    (ret :body)
    )
  )
  
    

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

;; MONGO_CONNECTION is of this form
;; mongodb://username:password@staff.mongohq.com:port/dbname
(defn home-page
  [request]
    (prn (System/getenv "MONGO_CONNECTION")  )
    (let [uri (System/getenv "MONGO_CONNECTION")
        {:keys [conn db]} (mg/connect-via-uri uri)]
         (http/json-response
          (mc/find-maps db "project-catalog") ))
  )

(def mock-projedt-collection
  {
    :sleeping-cat
    {
      :name "Sleeping Cat Project"
      :framework "Pedestal"
      :language "Clojure"
      :repo "https://gitlab.com/srehorn/sleepingcat"
    }
    :stinky-dog
    {
      :name "Stinky Dog Experiment"
      :framework "Grails"
      :language "Groovy"
      :repo "https://gitlab.com/srehorn/stinkydog"
    }
  }
)

(defn get-project
  [request]
  (http/json-response
   (db/db-get-project
    (get-in request [:path-params :proj-name]))))

(defn add-project
  [request]
  (prn (:json-params request))
    (let [incomming (:json-params request)
        connect-string (System/getenv "MONGO_CONNECTION")
        {:keys [conn db]} (mg/connect-via-uri connect-string)]
      (prn "-------------------------------")
      (prn db)
      (prn "-------------------------------")
      (prn conn)
      (prn "-------------------------------")
      (prn incomming)
      (prn "-------------------------------")
      (ring-resp/created
        "http://my-created-resource-url"
        (mc/insert-and-return db "project-catalog" incomming)
        )
  )
)

(defn get-projects
  [request]
  (let [uri (System/getenv "MONGO_CONNECTION")
      {:keys [conn db]} (mg/connect-via-uri uri)]
       (http/json-response
        (mc/find-maps db "project-catalog") ))
)

(defn git-search [q]
  (let [ret
    (client/get
     (format "https://api.github.com/search/repositories?q=%s+language:clojure" q)
     {:debug false
      :content-type :json
      :accept :json
      }
     )]
   (json/read-str (ret :body)))
  )

(defn git-get
  [request]
  (http/json-response (git-search (get-in request [:query-params :q])))
  )


(defn monger-mapper [xmlstring]
  "take a raw xml string, and map a known structure into a simple map"
  (let [proj-xml (xml/parse-str xmlstring)]
    {
       :proj-name (get-by-tag proj-xml :proj-name)
       :name (get-by-tag proj-xml :name)
       :framework (get-by-tag proj-xml :framework)
       :language (get-by-tag proj-xml :language)
       :repo (get-by-tag proj-xml :repo)
    }
  )
)

(defn xml-out [known-map]
  (xml/element :project {}
    (xml/element :_id {}  (.toString (:_id known-map)))
    (xml/element :proj-name {} (:proj-name known-map))
    (xml/element :name {} (:name known-map))
    (xml/element :framework {} (:framework known-map))
    (xml/element :repo {} (:repo known-map))
    (xml/element :language {} (:language known-map))
  )
)

(defn add-project-xml
  [request]
    (let [uri (System/getenv "MONGO_CONNECTION")
          {:keys [conn db]} (mg/connect-via-uri uri)
          incoming (slurp (:body request))
          ok (mc/insert-and-return db "project-catalog" (monger-mapper incoming))]
      (-> (ring-resp/created "http://resource-for-my-created-item"
                             (xml/emit-str (xml-out ok)))
          (ring-resp/content-type "application/xml"))
    )
  )

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])


(defhandler token-check [request]
  (let [token (get-in request [:headers "x-catalog-token"])]
    (if (not (=  token "o brave new world"))
      (assoc (ring-resp/response {:body "access denied"}) :status 403)
    )
  )
)

;; Tabular routes
; (def routes #{["/" :get (conj common-interceptors `home-page)]
;               ["/about" :get (conj common-interceptors `about-page)]})

;; Map-based routes
;(def routes `{"/" {:interceptors [(body-params/body-params) http/html-body]
;                   :get home-page
;                   "/about" {:get about-page}}})

;; Terse/Vector-based routes
(def routes
 `[[["/" {:get home-page}
     ^:interceptors [(body-params/body-params)
                      http/html-body token-check]
     ["/projects" {:get get-projects
                   :post add-project}]     
     ["/projects/:proj-name" {:get get-project}]

     ["/projects-xml" {:post add-project-xml}]
     ["/see-also" {:get git-get}]

     ["/about" {:get about-page}]]]])


;; Consumed by project-catalog.server/create-server
;; See http/default-interceptors for additional options you can configure
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; ::http/interceptors []
              ::http/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::http/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::http/resource-path "/public"

              ;; Either :jetty, :immutant or :tomcat (see comments in project.clj)
              ::http/type :jetty
              ;;::http/host "localhost"
              ::http/port 8080
              ;; Options to pass to the container (Jetty)
              ::http/container-options {:h2c? true
                                        :h2? false
                                        ;:keystore "test/hp/keystore.jks"
                                        ;:key-password "password"
                                        ;:ssl-port 8443
                                        :ssl? false}})

