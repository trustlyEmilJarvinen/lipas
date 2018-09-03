(ns lipas.schema.core
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [hiposfer.geojson.specs :as geojson]
            [lipas.data.admins :as admins]
            [lipas.data.cities :as cities]
            [lipas.data.ice-stadiums :as ice-stadiums]
            [lipas.data.materials :as materials]
            [lipas.data.owners :as owners]
            [lipas.data.sports-sites :as sports-sites]
            [lipas.data.swimming-pools :as swimming-pools]
            [lipas.data.types :as sports-site-types]
            [lipas.utils :as utils]))

;;; Utils ;;;

(defn str-in [min max]
  (s/and string? #(<= min (count %) max)))

(defn number-in
  "Returns a spec that validates numbers in the range from
  min (inclusive) to max (exclusive)."
  [& {:keys [min max]}]
  (s/and number? #(<= min % (dec max))))

;;; Regexes ;;;

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(def postal-code-regex #"[0-9]{5}")

;; https://stackoverflow.com/questions/3143070/javascript-regex-iso-datetime
(def timestamp-regex
  #"\d{4}-[01]\d-[0-3]\dT[0-2]\d:[0-5]\d:[0-5]\d\.\d+([+-][0-2]\d:[0-5]\d|Z)")

;;; Generators ;;;

(defn gen-str [min max]
  (gen/fmap #(apply str %)
            (gen/vector (gen/char-alpha) (+ min (rand-int max)))))

(defn email-gen []
  "Function that returns a Generator for email addresses"
  (gen/fmap
   (fn [[name host tld]]
     (str name "@" host "." tld))
   (gen/tuple
    (gen-str 1 15)
    (gen-str 1 15)
    (gen-str 2 63))))

(defn postal-code-gen []
  "Function that returns a Generator for Finnish postal codes"
  (gen/fmap
   (partial reduce str)
   (s/gen
    (s/tuple
     (s/int-in 0 10)
     (s/int-in 0 10)
     (s/int-in 0 10)
     (s/int-in 0 10)
     (s/int-in 0 10)))))

(defn timestamp-gen []
  (gen/fmap
   (fn [[yyyy MM dd hh mm ss ms]]
     (let [MM (utils/zero-left-pad MM 2)
           dd (utils/zero-left-pad dd 2)
           hh (utils/zero-left-pad hh 2)
           mm (utils/zero-left-pad mm 2)
           ss (utils/zero-left-pad ss 2)]
       (str yyyy "-" MM "-" dd "T" hh ":" mm ":" ss "." ms "Z")))
   (gen/tuple
    (s/gen (s/int-in 1900 (inc utils/this-year)))
    (s/gen (s/int-in 1 (inc 12)))
    (s/gen (s/int-in 1 (inc 28)))
    (s/gen (s/int-in 1 (inc 23)))
    (s/gen (s/int-in 0 (inc 59)))
    (s/gen (s/int-in 0 (inc 59)))
    (s/gen (s/int-in 0 (inc 999))))))

(defn lipas-point-feature-gen []
  (gen/fmap
   (fn [[lon lat]]
     {:type "FeatureCollection"
      :features
      [{:type "Feature"
        :geometry
        {:type "Point"
         :coordinates [lon lat]}}]})
   (s/gen (s/tuple :lipas.location.coordinates/lon
                   :lipas.location.coordinates/lat))))

;; Specs ;;

(s/def :lipas/timestamp-type (s/and string? #(re-matches timestamp-regex %)))
(s/def :lipas/timestamp (s/with-gen :lipas/timestamp-type timestamp-gen))

(s/def :lipas/email-type (s/and string? #(re-matches email-regex %)))
(s/def :lipas/email (s/with-gen :lipas/email-type email-gen))

(s/def :lipas/hours-in-day (number-in :min 0 :max (inc 24)))

;;; User ;;;

(s/def :lipas.user/id uuid?)
(s/def :lipas.user/firstname (str-in 1 128))
(s/def :lipas.user/lastname (str-in 1 128))
(s/def :lipas.user/username (str-in 1 128))
(s/def :lipas.user/password (str-in 6 128))
(s/def :lipas.user/email :lipas/email)

(s/def :lipas.user/login (s/or :username :lipas.user/username
                               :email :lipas.user/email))

(s/def :lipas.user/user-data (s/keys :req-un [:lipas.user/firstname
                                              :lipas.user/lastname]))

(s/def :lipas.user.permissions/sports-sites
  (s/coll-of :lipas.sports-site/lipas-id
             :min-count 0
             :max-count 1000
             :distinct true
             :into []))

(s/def :lipas.user.permissions/cities
  (s/coll-of :lipas.location.city/city-code
             :min-count 0
             :distinct true
             :into []))

(s/def :lipas.user.permissions/types
  (s/coll-of :lipas.sports-site.type/type-code
             :min-count 0
             :distinct true
             :into []))

(s/def :lipas.user.permissions/admin? boolean?)
(s/def :lipas.user.permissions/draft? boolean?)

(s/def :lipas.user/permissions
  (s/keys :opt-un [:lipas.user.permissions/admin?
                   :lipas.user.permissions/draft?
                   :lipas.user.permissions/sports-sites
                   :lipas.user.permissions/cities
                   :lipas.user.permissions/types]))

(s/def :lipas.user/permissions-request (str-in 1 200))

(s/def :lipas/new-user (s/keys :req-un [:lipas.user/email
                                        :lipas.user/username
                                        :lipas.user/password
                                        :lipas.user/user-data]
                               :opt-un [:lipas.user/permissions]))

(s/def :lipas/user (s/merge :lipas/new-user
                            (s/keys :req-un [:lipas.user/id])))

;;; Location ;;;

(s/def :lipas.location/address (str-in 1 200))
(s/def :lipas.location/postal-code-type
  (s/and string? #(re-matches postal-code-regex %)))

(s/def :lipas.location/postal-code (s/with-gen
                       :lipas.location/postal-code-type
                       postal-code-gen))

(s/def :lipas.location/postal-office (str-in 0 50))
(s/def :lipas.location.city/city-code (into #{} (map :city-code) cities/active))
(s/def :lipas.location.city/neighborhood (str-in 1 100))

(s/def :lipas.location/city
  (s/keys :req-un [:lipas.location.city/city-code]
          :opt-un [:lipas.location.city/neighborhood]))

;; TODO maybe narrow down coords to Finland extent?
(s/def :lipas.location.coordinates/lat (s/double-in :min -90.0
                                                    :max 90.0
                                                    :NaN? false
                                                    :infinite? false))

(s/def :lipas.location.coordinates/lon (s/double-in :min -180.0
                                                    :max 180.0
                                                    :NaN? false
                                                    :infinite? false))

;; NOTE: generator supports only point features atm
(s/def :lipas.location/geometries (s/with-gen
                                    ::geojson/feature-collection
                                    lipas-point-feature-gen))

(comment (s/valid?
          ::geojson/feature-collection
          (gen/generate (s/gen :lipas.location/geometries))))

;;; Sports site ;;;

(s/def :lipas.sports-site/lipas-id (s/int-in 0 2147483647)) ; PSQL integer max
(s/def :lipas.sports-site/hall-id (str-in 2 20))
(s/def :lipas.sports-site/status (into #{} (keys sports-sites/statuses)))
(s/def :lipas.sports-site/name (str-in 2 100))
(s/def :lipas.sports-site/marketing-name (str-in 2 100))

(s/def :lipas.sports-site/owner (into #{} (keys owners/all)))
(s/def :lipas.sports-site/admin (into #{} (keys admins/all)))

(s/def :lipas.sports-site/phone-number (str-in 1 50))
(s/def :lipas.sports-site/www (str-in 1 200))
(s/def :lipas.sports-site/email :lipas/email)

(s/def :lipas.sports-site.type/type-code
  (into #{} (map :type-code) sports-site-types/all))

(s/def :lipas.sports-site/construction-year
  (into #{} (range 1850 utils/this-year)))

(s/def :lipas.sports-site/renovation-years
  (s/coll-of (s/int-in 1900 (inc utils/this-year))
             :distinct true :into []))

(s/def :lipas.sports-site/properties
  (s/map-of keyword? (s/or :string? (str-in 1 100)
                           :number? number?
                           :boolean? boolean?)))

(s/def :lipas/location
  (s/keys :req-un [:lipas.location/address
                   :lipas.location/postal-code
                   :lipas.location/geometries
                   :lipas.location/city]
          :opt-un [:lipas.location/postal-office]))

(s/def :lipas.sport-site.type/type-code
  (into #{} (map :type-code) sports-site-types/all))

(s/def :lipas.sports-site.type/size-category
  (into #{} (keys ice-stadiums/size-categories)))

(s/def :lipas.sports-site/type
  (s/keys :req-un [:lipas.sports-site.type/type-code]
          :opt-un [:lipas.sports-site.type/size-category]))

;; When was the *document* created
(s/def :lipas.sports-site/created-at :lipas/timestamp)

;; What date/time does the document describe
(s/def :lipas.sports-site/event-date :lipas/timestamp)

(s/def :lipas/sports-site
  (s/keys :req-un [:lipas.sports-site/event-date
                   :lipas.sports-site/lipas-id
                   :lipas.sports-site/status
                   :lipas.sports-site/type
                   :lipas.sports-site/name
                   :lipas.sports-site/owner
                   :lipas.sports-site/admin
                   :lipas.sports-site/type
                   :lipas/location]
          :opt-un [:lipas.sports-site/created-at
                   :lipas.sports-site/marketing-name
                   :lipas.sports-site/phone-number
                   :lipas.sports-site/www
                   :lipas.sports-site/email
                   :lipas.sports-site/construction-year
                   :lipas.sports-site/renovation-years
                   ;; :lipas.sports-site/properties
                   ]))

(s/def :lipas/sports-sites (s/coll-of :lipas/sports-site :distinct true :into []))

;;; Building ;;;

(s/def :lipas.building/main-construction-material
  (into #{} (keys materials/building-materials)))
(s/def :lipas.building/supporting-structure
  (into #{} (keys materials/supporting-structures)))
(s/def :lipas.building/ceiling-structure
  (into #{} (keys materials/ceiling-structures)))

(s/def :lipas.building/construction-year :lipas.sports-site/construction-year)
(s/def :lipas.building/main-designers (str-in 2 100))
(s/def :lipas.building/total-surface-area-m2 (number-in :min 100 :max (inc 50000)))
(s/def :lipas.building/total-volume-m3 (number-in :min 100 :max (inc 400000)))
(s/def :lipas.building/total-pool-room-area-m2 (number-in :min 100 :max (inc 10000)))
(s/def :lipas.building/total-ice-area-m2 (number-in :min 100 :max (inc 10000)))
(s/def :lipas.building/total-water-area-m2 (number-in :min 10 :max (inc 10000)))
(s/def :lipas.building/heat-sections? boolean?)
(s/def :lipas.building/piled? boolean?)
(s/def :lipas.building/staff-count (s/int-in 0 (inc 1000)))
(s/def :lipas.building/seating-capacity (s/int-in 0 (inc 50000)))
(s/def :lipas.building/heat-source (into #{} (keys swimming-pools/heat-sources)))
(s/def :lipas.building/ventilation-units-count (s/int-in 0 (inc 100)))

(s/def :lipas.building/main-construction-materials
  (s/coll-of :lipas.building/main-construction-material
             :min-count 0
             :max-count (count materials/building-materials)
             :distinct true
             :into []))

(s/def :lipas.building/supporting-structures
  (s/coll-of :lipas.building/supporting-structure
             :min-count 0
             :max-count (count materials/supporting-structures)
             :distinct true
             :into []))

(s/def :lipas.building/ceiling-structures
  (s/coll-of :lipas.building/ceiling-structure
             :min-count 0
             :max-count (count materials/ceiling-structures)
             :distinct true
             :into []))

;;; Ice stadiums ;;;

;; Building ;;

(s/def :lipas.ice-stadium/building
  (s/keys :opt-un [:lipas.building/total-surface-area-m2
                   :lipas.building/total-volume-m3
                   :lipas.building/total-ice-area-m2
                   :lipas.building/construction-year
                   :lipas.building/seating-capacity]))

;; Envelope structure ;;

(s/def :lipas.ice-stadium.envelope/base-floor-structure
  (into #{} (keys materials/base-floor-structures)))

(s/def :lipas.ice-stadium.envelope/insulated-exterior? boolean?)
(s/def :lipas.ice-stadium.envelope/insulated-ceiling? boolean?)
(s/def :lipas.ice-stadium.envelope/low-emissivity-coating? boolean?)

(s/def :lipas.ice-stadium/envelope
  (s/keys :opt-un [:lipas.ice-stadium.envelope/insulated-exterior?
                   :lipas.ice-stadium.envelope/insulated-ceiling?
                   :lipas.ice-stadium.envelope/low-emissivity-coating?]))

;; Rinks ;;

(s/def :lipas.ice-stadium.rink/width-m (number-in :min 0 :max 100))
(s/def :lipas.ice-stadium.rink/length-m (number-in :min 0 :max 100))

(s/def :lipas.ice-stadium/rink (s/keys :req-un [:lipas.ice-stadium.rink/width-m
                                                :lipas.ice-stadium.rink/length-m]))
(s/def :lipas.ice-stadium/rinks
  (s/coll-of :lipas.ice-stadium/rink
             :min-count 0
             :max-count 20
             :distinct false
             :into []))

;; Refrigeration ;;

(s/def :lipas.ice-stadium.refrigeration/original? boolean?)
(s/def :lipas.ice-stadium.refrigeration/individual-metering? boolean?)
(s/def :lipas.ice-stadium.refrigeration/condensate-energy-recycling? boolean?)

(s/def :lipas.ice-stadium.refrigeration/condensate-energy-main-target
  (into #{} (keys ice-stadiums/condensate-energy-targets)))

(s/def :lipas.ice-stadium.refrigeration/condensate-energy-main-targets
  (s/coll-of :lipas.ice-stadium.refrigeration/condensate-energy-main-target
             :min-count 0
             :max-count 10
             :distinct true
             :into []))

(s/def :lipas.ice-stadium.refrigeration/power-kw
  (s/int-in 0 (inc 10000)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant
  (into #{} (keys ice-stadiums/refrigerants)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-amount-kg
  (s/int-in 0 (inc 10000)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-solution
  (into #{} (keys ice-stadiums/refrigerant-solutions)))

(s/def :lipas.ice-stadium.refrigeration/refrigerant-solution-amount-l
  (s/int-in 0 (inc 30000)))

(s/def :lipas.ice-stadium/refrigeration
  (s/keys :opt-un [:lipas.ice-stadium.refrigeration/original?
                   :lipas.ice-stadium.refrigeration/individual-metering?
                   :lipas.ice-stadium.refrigeration/condensate-energy-recycling?
                   :lipas.ice-stadium.refrigeration/condensate-energy-main-targets
                   :lipas.ice-stadium.refrigeration/power-kw
                   :lipas.ice-stadium.refrigeration/refrigerant
                   :lipas.ice-stadium.refrigeration/refrigerant-amount-kg
                   :lipas.ice-stadium.refrigeration/refrigerant-solution
                   :lipas.ice-stadium.refrigeration/refrigerant-solution-amount-l]))

;; Conditions ;;

(s/def :lipas.ice-stadium.conditions/air-humidity-min (s/int-in 0 (inc 100)))
(s/def :lipas.ice-stadium.conditions/air-humidity-max (s/int-in 0 (inc 100)))
(s/def :lipas.ice-stadium.conditions/stand-temperature-c (s/int-in -10 (inc 50)))
(s/def :lipas.ice-stadium.conditions/daily-open-hours :lipas/hours-in-day)
(s/def :lipas.ice-stadium.conditions/open-months (s/int-in 0 (inc 12)))

(s/def :lipas.ice-stadium.conditions/ice-surface-temperature-c
  (s/int-in -10 (inc -1)))

(s/def :lipas.ice-stadium.conditions/skating-area-temperature-c
  (s/int-in -15 (inc 20)))

(s/def :lipas.ice-stadium.conditions/daily-maintenances-week-days
  (s/int-in 0 (inc 50)))

(s/def :lipas.ice-stadium.conditions/daily-maintenances-weekends
  (s/int-in 0 (inc 50)))

(s/def :lipas.ice-stadium.conditions/weekly-maintenances (s/int-in 0 (inc 100)))

(s/def :lipas.ice-stadium.conditions/average-water-consumption-l
  (s/int-in 0 (inc 1000)))

(s/def :lipas.ice-stadium.conditions/maintenance-water-temperature-c
  (s/int-in 0 100))

(s/def :lipas.ice-stadium.conditions/ice-resurfacer-fuel
  (into #{} (keys ice-stadiums/ice-resurfacer-fuels)))

(s/def :lipas.ice-stadium.conditions/ice-average-thickness-mm
  (s/int-in 0 (inc 150)))

(s/def :lipas.ice-stadium/conditions
  (s/keys :opt-un [:lipas.ice-stadium.conditions/air-humidity-min
                   :lipas.ice-stadium.conditions/air-humidity-max
                   :lipas.ice-stadium.conditions/ice-surface-temperature-c
                   :lipas.ice-stadium.conditions/skating-area-temperature-c
                   :lipas.ice-stadium.conditions/stand-temperature-c
                   :lipas.ice-stadium.conditions/daily-open-hours
                   :lipas.ice-stadium.conditions/open-months
                   ;; :lipas.ice-stadium.conditions/daily-maintenances-week-days
                   ;; :lipas.ice-stadium.conditions/daily-maintenances-weekends
                   :lipas.ice-stadium.conditions/weekly-maintenances
                   :lipas.ice-stadium.conditions/average-water-consumption-l
                   :lipas.ice-stadium.conditions/maintenance-water-temperature-c
                   :lipas.ice-stadium.conditions/ice-resurfacer-fuel
                   :lipas.ice-stadium.conditions/ice-average-thickness-mm]))

;; Ventilation ;;

(s/def :lipas.ice-stadium.ventilation/heat-recovery-efficiency
  (s/int-in 0 (inc 100)))

(s/def :lipas.ice-stadium.ventilation/heat-recovery-type
  (into #{} (keys ice-stadiums/heat-recovery-types)))

(s/def :lipas.ice-stadium.ventilation/dryer-type
  (into #{} (keys ice-stadiums/dryer-types)))

(s/def :lipas.ice-stadium.ventilation/dryer-duty-type
  (into #{} (keys ice-stadiums/dryer-duty-types)))

(s/def :lipas.ice-stadium.ventilation/heat-pump-type
  (into #{} (keys ice-stadiums/heat-pump-types)))

(s/def :lipas.ice-stadium/ventilation
  (s/keys :opt-un [:lipas.ice-stadium.ventilation/heat-recovery-efficiency
                   :lipas.ice-stadium.ventilation/heat-recovery-type
                   :lipas.ice-stadium.ventilation/dryer-type
                   :lipas.ice-stadium.ventilation/dryer-duty-type
                   :lipas.ice-stadium.ventilation/heat-pump-type]))

;; Energy consumption ;;

(s/def :lipas.energy-consumption/electricity-mwh (s/int-in 0 10000))
(s/def :lipas.energy-consumption/heat-mwh (s/int-in 0 10000))
;; TODO find out realistic limits for cold energy
(s/def :lipas.energy-consumption/cold-mwh (s/int-in 0 100000))
(s/def :lipas.energy-consumption/water-m3 (s/int-in 0 500000))
(s/def :lipas.energy-consumption/contains-other-buildings? boolean?)

(s/def :lipas/energy-consumption
  (s/keys :opt-un [:lipas.energy-consumption/electricity-mwh
                   :lipas.energy-consumption/cold-mwh
                   :lipas.energy-consumption/heat-mwh
                   :lipas.energy-consumption/water-m3
                   :lipas.energy-consumption/contains-other-buildings?]))

(def months #{:jan :feb :mar :apr :may :jun :jul :aug :sep :oct :nov :dec})

(s/def :lipas.ice-stadium/energy-consumption-monthly
  (s/map-of months :lipas/energy-consumption))

(s/def :lipas.ice-stadium.type/type-code #{2510 2520})
(s/def :lipas.ice-stadium/type
  (s/merge
   :lipas.sports-site/type
   (s/keys :req-un [:lipas.ice-stadium.type/type-code])))

(s/def :lipas.sports-site/ice-stadium
  (s/merge
   :lipas/sports-site
   (s/keys :req-un [:lipas.ice-stadium/type]
           :opt-un [:lipas.sports-site/hall-id
                    :lipas.ice-stadium/building
                    :lipas.ice-stadium/rinks
                    :lipas.ice-stadium/envelope
                    :lipas.ice-stadium/refrigeration
                    :lipas.ice-stadium/ventilation
                    :lipas.ice-stadium/conditions
                    :lipas.ice-stadium/energy-consumption-monthly
                    :lipas/energy-consumption])))

;;; Swimming pools ;;;

;; Building ;;

(s/def :lipas.swimming-pool/building
  (s/keys :opt-un [:lipas.building/main-designers
                   :lipas.building/total-surface-area-m2
                   :lipas.building/total-volume-m3
                   :lipas.building/construction-year
                   :lipas.building/seating-capacity
                   :lipas.building/total-water-area-m2
                   :lipas.building/total-pool-room-area-m2
                   :lipas.building/heat-sections?
                   :lipas.building/main-construction-materials
                   :lipas.building/piled?
                   :lipas.building/supporting-structures
                   :lipas.building/ceiling-structures
                   :lipas.building/staff-count
                   :lipas.building/heat-source
                   :lipas.building/ventilation-units-count]))

;; Water treatment ;;

(s/def :lipas.swimming-pool.water-treatment/ozonation boolean?)
(s/def :lipas.swimming-pool.water-treatment/uv-treatment boolean?)
(s/def :lipas.swimming-pool.water-treatment/activated-carbon boolean?)
(s/def :lipas.swimming-pool.water-treatment/filtering-method
  (into #{} (keys swimming-pools/filtering-methods)))

(s/def :lipas.swimming-pool.water-treatment/filtering-methods
  (s/coll-of :lipas.swimming-pool.water-treatment/filtering-method
             :min-count 0
             :max-count 10
             :distinct true
             :into []))

;; TODO maybe get rid of this?
(s/def :lipas.swimming-pool.water-treatment/comment (str-in 1 1024))

(s/def :lipas.swimming-pool/water-treatment
  (s/keys :opt-un [:lipas.swimming-pool.water-treatment/filtering-methods
                   :lipas.swimming-pool.water-treatment/activated-carbon
                   :lipas.swimming-pool.water-treatment/uv-treatment
                   :lipas.swimming-pool.water-treatment/ozonation
                   :lipas.swimming-pool.water-treatment/comment]))

;; Pools ;;

(s/def :lipas.swimming-pool.pool/temperature-c (number-in :min 0 :max 50))
(s/def :lipas.swimming-pool.pool/volume-m3 (number-in :min 0 :max 5000))
(s/def :lipas.swimming-pool.pool/area-m2 (number-in :min 0 :max 2000))
(s/def :lipas.swimming-pool.pool/length-m (number-in :min 0 :max 200))
(s/def :lipas.swimming-pool.pool/width-m (number-in :min 0 :max 100))
(s/def :lipas.swimming-pool.pool/depth-min-m (number-in :min 0 :max 10))
(s/def :lipas.swimming-pool.pool/depth-max-m (number-in :min 0 :max 10))
(s/def :lipas.swimming-pool.pool/type (into #{} (keys swimming-pools/pool-types)))
(s/def :lipas.swimming-pool.pool/outdoor-pool? boolean?)

(s/def :lipas.swimming-pool/pool
  (s/keys :opt-un [:lipas.swimming-pool.pool/type
                   :lipas.swimming-pool.pool/outdoor-pool?
                   :lipas.swimming-pool.pool/temperature-c
                   :lipas.swimming-pool.pool/volume-m3
                   :lipas.swimming-pool.pool/area-m2
                   :lipas.swimming-pool.pool/length-m
                   :lipas.swimming-pool.pool/width-m
                   :lipas.swimming-pool.pool/depth-min-m
                   :lipas.swimming-pool.pool/depth-max-m]))

(s/def :lipas.swimming-pool/pools
  (s/coll-of :lipas.swimming-pool/pool
             :min-count 0
             :max-count 50
             :distinct false
             :into []))

;; Slides ;;

(s/def :lipas.swimming-pool.slide/length-m (number-in :min 0 :max 200))
(s/def :lipas.swimming-pool.slide/structure
  (into #{} (keys materials/slide-structures)))

(s/def :lipas.swimming-pool/slide
  (s/keys :req-un [:lipas.swimming-pool.slide/length-m]
          :opt-un [:lipas.swimming-pool.slide/structure]))

(s/def :lipas.swimming-pool/slides
  (s/coll-of :lipas.swimming-pool/slide
             :min-count 0
             :max-count 10
             :distinct false
             :into []))

;; Saunas ;;

(s/def :lipas.swimming-pool.sauna/men? boolean?)
(s/def :lipas.swimming-pool.sauna/women? boolean?)
(s/def :lipas.swimming-pool.sauna/accessible? boolean?)
(s/def :lipas.swimming-pool.sauna/type
  (into #{} (keys swimming-pools/sauna-types)))

(s/def :lipas.swimming-pool/sauna
  (s/keys :req-un [:lipas.swimming-pool.sauna/type]
          :opt-un [:lipas.swimming-pool.sauna/men?
                   :lipas.swimming-pool.sauna/women?
                   :lipas.swimming-pool.sauna/accessible?]))

(s/def :lipas.swimming-pool/saunas
  (s/coll-of :lipas.swimming-pool/sauna
             :min-count 0
             :max-count 50
             :distinct false
             :into []))

;; Other facilities ;;

(s/def :lipas.swimming-pool.facilities/platforms-1m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-3m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-5m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-7.5m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/platforms-10m-count (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/hydro-massage-spots-count
  (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/hydro-neck-massage-spots-count
  (s/int-in 0 100))
(s/def :lipas.swimming-pool.facilities/kiosk? boolean?)

;; Showers and lockers ;;
(s/def :lipas.swimming-pool.facilities/showers-men-count (s/int-in 0 200))
(s/def :lipas.swimming-pool.facilities/showers-women-count (s/int-in 0 200))
(s/def :lipas.swimming-pool.facilities/lockers-men-count (s/int-in 0 1000))
(s/def :lipas.swimming-pool.facilities/lockers-women-count (s/int-in 0 1000))

(s/def :lipas.swimming-pool/facilities
  (s/keys :opt-un [:lipas.swimming-pool.facilities/platforms-1m-count
                   :lipas.swimming-pool.facilities/platforms-3m-count
                   :lipas.swimming-pool.facilities/platforms-5m-count
                   :lipas.swimming-pool.facilities/platforms-7.5m-count
                   :lipas.swimming-pool.facilities/platforms-10m-count
                   :lipas.swimming-pool.facilities/hydro-massage-spots-count
                   :lipas.swimming-pool.facilities/hydro-neck-massage-spots-count
                   :lipas.swimming-pool.facilities/kiosk?
                   :lipas.swimming-pool.facilities/showers-men-count
                   :lipas.swimming-pool.facilities/showers-women-count
                   :lipas.swimming-pool.facilities/lockers-men-count
                   :lipas.swimming-pool.facilities/lockers-women-count]))

;; Conditions ;;

(s/def :lipas.swimming-pool.conditions/open-days-in-year (s/int-in 0 (inc 365)))
(s/def :lipas.swimming-pool.conditions/daily-open-hours :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-mon :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-tue :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-wed :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-thu :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-fri :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-sat :lipas/hours-in-day)
(s/def :lipas.swimming-pool.conditions/open-hours-sun :lipas/hours-in-day)

(s/def :lipas.swimming-pool/conditions
  (s/keys :opt-un [:lipas.swimming-pool.conditions/open-days-in-year
                   :lipas.swimming-pool.conditions/open-hours-in-day
                   :lipas.swimming-pool.conditions/open-hours-mon
                   :lipas.swimming-pool.conditions/open-hours-tue
                   :lipas.swimming-pool.conditions/open-hours-wed
                   :lipas.swimming-pool.conditions/open-hours-thu
                   :lipas.swimming-pool.conditions/open-hours-fri
                   :lipas.swimming-pool.conditions/open-hours-sat
                   :lipas.swimming-pool.conditions/open-hours-sun
                   :lipas.swimming-pool.conditions/total-visitors-count]))

(s/def :lipas.swimming-pool.type/type-code #{3110 3120 3130})
(s/def :lipas.swimming-pool/type
  (s/merge
   :lipas.sports-site/type
   (s/keys :req-un [:lipas.swimming-pool.type/type-code])))

;; Energy saving ;;
(s/def :lipas.swimming-pool.energy-saving/shower-water-heat-recovery?
  boolean?)

(s/def :lipas.swimming-pool.energy-saving/filter-rinse-water-recovery?
  boolean?)

(s/def :lipas.swimming-pool/energy-saving
  (s/keys :opt-un
          [:lipas.swimming-pool.energy-saving/shower-water-heat-recovery?
           :lipas.swimming-pool.energy-saving/filter-rinse-water-recovery?]))

;; Visitors ;;
(s/def :lipas.swimming-pool.visitors/total-count (s/int-in 0 1000000))
(s/def :lipas.swimming-pool/visitors
  (s/keys :req-un [:lipas.swimming-pool.visitors/total-count]))

(s/def :lipas.sports-site/swimming-pool
  (s/merge
   :lipas/sports-site
   (s/keys :req-un [:lipas.swimming-pool/type]
           :opt-un [:lipas.sports-site/hall-id ; Legacy portal id
                    :lipas.swimming-pool/water-treatment
                    :lipas.swimming-pool/facilities
                    :lipas.swimming-pool/building
                    :lipas.swimming-pool/pools
                    :lipas.swimming-pool/saunas
                    :lipas.swimming-pool/slides
                    :lipas.swimming-pool/conditions
                    :lipas.swimming-pool/visitors
                    :lipas.swimming-pool/energy-saving
                    :lipas/energy-consumption])))

(s/def :lipas.sports-site/swimming-pools
  (s/coll-of :lipas.sports-site/swimming-pool
             :distinct true
             :into []))

;;; HTTP-API ;;;

(s/def :lipas.api/revs #{"latest" "yearly"})
(s/def :lipas.api/lang #{"fi" "en" "se"})
(s/def :lipas.api/query-params
  (s/keys :opt-un [:lipas.api/revs
                   :lipas.api/lang]))
