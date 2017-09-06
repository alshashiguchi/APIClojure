(ns project-catalog.service
  (:require [io.pedestal.http :as http]
            [io.pedestal.http.route :as route]
            [io.pedestal.http.body-params :as body-params]
            [ring.util.response :as ring-resp]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s - served from %s"
                              (clojure-version)
                              (route/url-for ::about-page))))

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
      (let [projname (get-in request [:path-params :project-name])]
            (http/json-response ((keyword projname) mock-projedt-collection))
      )
)

(defn add-project
  [request]
  (prn (:json-params request))
    (ring-resp/created "http://fake-201-url" "fake 201 in the body")
  )

(defn home-page
  [request]
  (ring-resp/response "Hello Arthur!"))

(defn get-projects
  [request]
  (http/json-response mock-projedt-collection))

;; Defines "/" and "/about" routes with their associated :get handlers.
;; The interceptors defined after the verb map (e.g., {:get home-page}
;; apply to / and its children (/about).
(def common-interceptors [(body-params/body-params) http/html-body])

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
     ^:interceptors [(body-params/body-params) http/html-body]
     ["/projects" {:get get-projects
                   :post add-project}]
     ["/projects/:project-name" {:get get-project}]
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

