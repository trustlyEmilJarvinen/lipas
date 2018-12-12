(ns lipas.backend.core
  (:require
   [buddy.hashers :as hashers]
   [clojure.core.async :as async]
   [dk.ative.docjure.spreadsheet :as excel]
   [lipas.backend.db.db :as db]
   [lipas.backend.email :as email]
   [lipas.backend.jwt :as jwt]
   [lipas.backend.search :as search]
   [lipas.i18n.core :as i18n]
   [lipas.permissions :as permissions]
   [lipas.reports :as reports]
   [lipas.utils :as utils]
   [taoensso.timbre :as log]))

;;; User ;;;

(defn username-exists? [db user]
  (some? (db/get-user-by-username db user)))

(defn email-exists? [db user]
  (some? (db/get-user-by-email db user)))

(defn add-user! [db user]
  (when (username-exists? db user)
    (throw (ex-info "Username is already in use!"
                    {:type :username-conflict})))

  (when (email-exists? db user)
    (throw (ex-info "Email is already in use!"
                    {:type :email-conflict})))

  (let [defaults {:permissions permissions/default-permissions
                  :username    (:email user)
                  :user-data   {}
                  :password    (str (utils/gen-uuid))}
        user     (-> (merge defaults user)
                     (update :password hashers/encrypt))]

    (db/add-user! db user)
    {:status "OK"}))

(defn register! [db emailer user]
  (add-user! db user)
  (email/send-register-notification! emailer
                                     "lipasinfo@jyu.fi"
                                     (dissoc user :password))
  {:status "OK"})

(defn publish-users-drafts! [db {:keys [permissions id] :as user}]
  (let [drafts (->> (db/get-users-drafts db user)
                    (filter (partial permissions/publish? permissions)))]
    (log/info "Publishing" (count drafts) "drafts from user" id)
    (doseq [draft drafts]
      (db/upsert-sports-site! db user (assoc draft :status "active")))))

;; TODO send email
(defn update-user-permissions! [db emailer user]
  (db/update-user-permissions! db user)
  (publish-users-drafts! db user))

(defn get-user [db identifier]
  (or (db/get-user-by-email db {:email identifier})
      (db/get-user-by-username db {:username identifier})
      (db/get-user-by-id db {:id identifier})))

(defn get-user! [db identifier]
  (if-let [user (get-user db identifier)]
    user
    (throw (ex-info "User not found."
                    {:type :user-not-found}))))

(defn get-users [db]
  (db/get-users db))

(defn create-magic-link [url user]
  (let [token (jwt/create-token user :terse? true :valid-seconds (* 7 24 60 60))]
    (str url "?token=" token)))

(defn send-password-reset-link! [db emailer {:keys [email reset-url]}]
  (if-let [user (db/get-user-by-email db {:email email})]
    (let [reset-link (create-magic-link reset-url user)]
      (email/send-reset-password-email! emailer email reset-link))
    (throw (ex-info "User not found"
                    {:type :email-not-found}))))

(defn send-magic-link! [db emailer {:keys [user login-url]}]
  (let [email      (-> user :email)
        user       (or (db/get-user-by-email db {:email email})
                       (do (add-user! db user)
                           (db/get-user-by-email db {:email email})))
        reset-link (create-magic-link login-url user)]
    (email/send-magic-login-email! emailer email reset-link)))

(defn reset-password! [db user password]
  (db/reset-user-password! db (assoc user :password
                                    (hashers/encrypt password))))

;;; Sports-sites ;;;

(defn- check-permissions! [user sports-site draft?]
  (when-not (or draft?
                (permissions/publish? (:permissions user) sports-site))
    (throw (ex-info "User doesn't have enough permissions!"
                    {:type :no-permission}))))

;; TODO change to lighter check query
(defn- check-sports-site-exists! [db lipas-id]
  (when (empty? (db/get-sports-site-history db lipas-id))
    (throw (ex-info "Sports site not found"
                    {:type     :sports-site-not-found
                     :lipas-id lipas-id}))))

(defn upsert-sports-site!*
  "Should be used only when data is from trusted sources (migrations
  etc.). Doesn't check if lipas-ids exist or not."
  ([db user sports-site]
   (upsert-sports-site!* db user sports-site false))
  ([db user sports-site draft?]
   (db/upsert-sports-site! db user sports-site draft?)))

(defn upsert-sports-site!
  ([db user sports-site]
   (upsert-sports-site! db user sports-site false))
  ([db user sports-site draft?]
   (check-permissions! user sports-site draft?)
   (when-let [lipas-id (:lipas-id sports-site)]
     (check-sports-site-exists! db lipas-id))
   (upsert-sports-site!* db user sports-site draft?)))

(defn get-sports-sites-by-type-code
  ([db type-code]
   (get-sports-sites-by-type-code db type-code {}))
  ([db type-code {:keys [locale] :as opts}]
   (let [data (db/get-sports-sites-by-type-code db type-code opts)]
     (if (#{:fi :en :se} locale)
       (map (partial i18n/localize locale) data)
       data))))

(defn get-sports-site-history [db lipas-id]
  (db/get-sports-site-history db lipas-id))

(defn index! [search sports-site]
  (let [idx-name "sports_sites_current"]
    (search/index! search idx-name :lipas-id sports-site)))

(defn search [search params]
  (let [idx-name "sports_sites_current"]
    (search/search search idx-name params)))

;;; Reports ;;;

(defn energy-report [db type-code year]
  (let [data (get-sports-sites-by-type-code db type-code {:revs year})]
    (reports/energy-report data)))

;; TODO support :se and :en locales
(defn sports-sites-report [search params fields out]
  (let [idx-name  "sports_sites_current"
        in-chan   (search/scroll search idx-name params)
        locale    :fi
        headers   (mapv #(get-in reports/fields [% locale]) fields)
        data-chan (async/go
                    (loop [res [headers]]
                      (if-let [page (async/<! in-chan)]
                        (recur (-> page :body :hits :hits
                                   (->>
                                    (map (comp (partial reports/->row fields)
                                               (partial i18n/localize locale)
                                               :_source))
                                    (into res))))
                        res)))]
    (->> (async/<!! data-chan)
         (excel/create-workbook "lipas")
         (excel/save-workbook-into-stream! out))))

(comment
  (let [wb (excel/create-workbook "dada" res)]
    (excel/save-workbook-into-stream! out wb))

  (require '[lipas.backend.config :as config])
  (def db-spec (:db config/default-config))
  (def admin (get-user db-spec "admin@lipas.fi"))
  (publish-users-drafts! db-spec admin)

  (def search (search/create-cli {:hosts    ["localhost:9200"]
                                  :user     "elastic"
                                  :password "changeme"}))
  (def fields ["lipas-id" "name" "admin" "owner" "properties.surface-material"
               "location.city.city-code"])
  (sports-sites-report search {:query  {:match_all {}}
                               :_source {:excludes ["location.geometries"]}
                               :size    100}
                      fields)

  (sports-sites-report search {:query  {:bool {:must [{:query_string {:query "mursu*"}}]}}
                               :_source {:excludes ["location.geometries"]}
                               :size    100}
                      fields)

  (with-open [out (io/output-stream "kissa.xlsx")]
    (let [query {:query   {:bool
                           {:must
                            [{:query_string
                              {:query "mursu*"}}]}}
                 :_source {:excludes ["location.geometries"]}
                 :size    100}
          ch    (sports-sites-report search query fields out)])))
