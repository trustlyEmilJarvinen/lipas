(ns lipas.ui.swimming-pools.views
  (:require [lipas.schema.core :as schema]
            [lipas.ui.components :as lui]
            [lipas.ui.mui :as mui]
            [lipas.ui.mui-icons :as mui-icons]
            [lipas.ui.swimming-pools.events :as events]
            [lipas.ui.swimming-pools.pools :as pools]
            [lipas.ui.swimming-pools.renovations :as renovations]
            [lipas.ui.swimming-pools.saunas :as saunas]
            [lipas.ui.swimming-pools.slides :as slides]
            [lipas.ui.swimming-pools.subs :as subs]
            [lipas.ui.swimming-pools.utils :refer [set-field]]
            [lipas.ui.utils :refer [<== ==>]]
            [re-frame.core :as re-frame]
            [reagent.core :as r]))

(defn info-tab [url]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [:iframe {:src url
              :style {:min-height "800px" :width "100%"}}]]])

(defn energy-tab [tr]
  [mui/grid {:container true}
   [mui/grid {:item true :xs 12}
    [mui/typography (tr :ice-energy/description)]]])

(defn ->select-value [tr prefix k]
  {:value k
   :label (tr (keyword (prefix k)))})

(defn basic-data-tab [tr]
  (let [data               (<== [::subs/editing])
        dialogs            (<== [::subs/dialogs])
        types              (<== [::subs/types])
        cities             (<== [::subs/cities])
        owners             (<== [::subs/owners])
        admins             (<== [::subs/admins])
        filtering-methods  (<== [::subs/filtering-methods])
        building-materials (<== [::subs/building-materials])
        set-field          (partial set-field :editing)]

    [mui/grid {:container true}

     ;; General info
     [lui/form-card {:title (tr :general/general-info)}
      [lui/sports-place-form
       {:tr        tr
        :data      data
        :types     types
        :owners    owners
        :admins    admins
        :on-change set-field}]]

     ;; Location
     [lui/form-card {:title (tr :location/headline)}
      [lui/location-form
       {:tr        tr
        :data      data
        :cities    cities
        :on-change set-field}]]

     ;; Building
     [lui/form-card {:title (tr :building/headline)}
      [mui/form-group
       [lui/year-selector
        {:label     (tr :building/construction-year)
         :value     (-> data :building :construction-year)
         :on-change #(set-field :building :construction-year %)}]
       [lui/text-field
        {:label     (tr :building/main-designers)
         :value     (-> data :building :main-designers)
         :spec      ::schema/main-designers
         :on-change #(set-field :building :main-designers %)}]
       [lui/text-field
        {:label     (tr :building/total-surface-area-m2)
         :type      "number"
         :value     (-> data :building :total-surface-area-m2)
         :spec      ::schema/total-surface-area-m2
         :adornment (tr :physical-units/m2)
         :on-change #(set-field :building :total-surface-area-m2 %)}]
       [lui/text-field
        {:label     (tr :building/total-volume-m3)
         :type      "number"
         :value     (-> data :building :total-volume-m3)
         :spec      ::schema/total-volume-m3
         :adornment (tr :physical-units/m3)
         :on-change #(set-field :building :total-volume-m3 %)}]
       [lui/text-field
        {:label     (tr :building/seating-capacity)
         :type      "number"
         :value     (-> data :building :seating-capacity)
         :spec      ::schema/seating-capacity
         :adornment (tr :units/person)
         :on-change #(set-field :building :seating-capacity %)}]
       [lui/text-field
        {:label     (tr :building/staff-count)
         :type      "number"
         :value     (-> data :building :staff-count)
         :adornment (tr :units/person)
         :on-change #(set-field :building :staff-count %)}]
       [lui/text-field
        {:label     (tr :building/pool-room-total-area-m2)
         :type      "number"
         :adornment (tr :physical-units/m2)
         :value     (-> data :building :pool-room-total-area-m2)
         :on-change #(set-field :building :pool-room-total-area-m2 %)}]
       [lui/text-field
        {:label     (tr :building/total-water-area-m2)
         :type      "number"
         :adornment (tr :physical-units/m2)
         :value     (-> data :building :total-water-area-m2)
         :on-change #(set-field :building :total-water-area-m2 %)}]
       [lui/checkbox
        {:label     (tr :building/heat-sections?)
         :value     (-> data :building :heat-sections?)
         :on-change #(set-field :building :heat-sections? %)}]
       [lui/checkbox
        {:label     (tr :building/piled?)
         :value     (-> data :building :piled?)
         :on-change #(set-field :building :piled? %)}]
       [lui/multi-select
        {:label     (tr :building/main-construction-materials)
         :value     (-> data :building :main-construction-materials)
         :items     (map #(hash-map :value %
                                    :label (tr (keyword :building-materials %)))
                         (keys building-materials))
         :on-change #(set-field :building :main-construction-materials %)}]
       [lui/text-field
        {:label     (tr :building/supporting-structures-description)
         :value     (-> data :building :supporting-structures-description)
         :on-change #(set-field :building :supporting-structures-description %)}]
       [lui/text-field
        {:label     (tr :building/ceiling-description)
         :value     (-> data :building :ceiling-description)
         :on-change #(set-field :building :ceiling-description %)}]]]

     ;; Renovations
     (when (-> dialogs :renovation :open?)
       [renovations/dialog {:tr tr}])

     [lui/form-card {:title (tr :renovations/headline)}
      [renovations/table {:tr tr :items (-> data :renovations vals)}]]

     ;; Water treatment
     [lui/form-card {:title (tr :water-treatment/headline)}
      [mui/form-group
       [lui/checkbox
        {:label     (tr :water-treatment/ozonation?)
         :value     (-> data :water-treatment :ozonation?)
         :on-change #(set-field :water-treatment :ozonation? %)}]
       [lui/checkbox
        {:label     (tr :water-treatment/uv-treatment?)
         :value     (-> data :water-treatment :uv-treatment?)
         :on-change #(set-field :water-treatment :uv-treatment? %)}]
       [lui/checkbox
        {:label     (tr :water-treatment/activated-carbon?)
         :value     (-> data :water-treatment :activated-carbon?)
         :on-change #(set-field :water-treatment :activated-carbon? %)}]
       [lui/multi-select
        {:label     (tr :water-treatment/filtering-method)
         :value     (-> data :water-treatment :filtering-method)
         :items     (map #(hash-map :value %
                                    :label (tr (keyword :filtering-methods %)))
                         (keys filtering-methods))
         :on-change #(set-field :water-treatment :filtering-method %)}]
       [lui/text-field
        {:label     (tr :general/comment)
         :value     (-> data :water-treatment :comment)
         :on-change #(set-field :water-treatment :comment %)}]]]

     ;; Pools
     (when (-> dialogs :pool :open?)
       [pools/dialog {:tr tr}])

     [lui/form-card {:title (tr :pools/headline)}
      [pools/table {:tr    tr
                    :items (-> data :pools)}]]

     ;; Saunas
     (when (-> dialogs :sauna :open?)
       [saunas/dialog {:tr tr}])

     [lui/form-card {:title (tr :saunas/headline)}
      [saunas/table {:tr    tr
                     :items (-> data :saunas)}]]

     ;; Slides
     (when (-> dialogs :slide :open?)
       [slides/dialog {:tr tr}])

     [lui/form-card {:title (tr :slides/headline)}
      [slides/table {:tr    tr
                     :items (-> data :slides vals)}]]

     ;; Other services
     [lui/form-card {:title (tr :other-services/headline)}
      [mui/form-group
       [lui/text-field
        {:label     (tr :other-services/platforms-1m-count)
         :adornment (tr :units/pcs)
         :value     (-> data :other-services :platforms-1m-count)
         :on-change #(set-field :other-services :platforms-1m-count %)}]
       [lui/text-field
        {:label     (tr :other-services/platforms-3m-count)
         :adornment (tr :units/pcs)
         :value     (-> data :other-services :platforms-3m-count)
         :on-change #(set-field :other-services :platforms-3m-count %)}]
       [lui/text-field
        {:label     (tr :other-services/platforms-5m-count)
         :adornment (tr :units/pcs)
         :value     (-> data :other-services :platforms-5m-count)
         :on-change #(set-field :other-services :platforms-5m-count %)}]
       [lui/text-field
        {:label     (tr :other-services/platforms-7.5m-count)
         :adornment (tr :units/pcs)
         :value     (-> data :other-services :platforms-7.5m-count)
         :on-change #(set-field :other-services :platforms-7.5m-count %)}]
       [lui/text-field
        {:label     (tr :other-services/platforms-10m-count)
         :adornment (tr :units/pcs)
         :value     (-> data :other-services :platforms-10m-count)
         :on-change #(set-field :other-services :platforms-10m-count %)}]
       [lui/text-field
        {:label     (tr :other-services/hydro-massage-spots-count)
         :adornment (tr :units/pcs)
         :value     (-> data :other-services :hydro-massage-spots-count)
         :on-change #(set-field :other-services :hydro-massage-spots-count %)}]
       [lui/text-field
        {:label     (tr :other-services/hydro-neck-massage-spots-count)
         :adornment (tr :units/pcs)
         :value     (-> data :other-services :hydro-neck-massage-spots-count)
         :on-change #(set-field :other-services :hydro-neck-massage-spots-count %)}]
       [lui/checkbox
        {:label     (tr :other-services/kiosk?)
         :value     (-> data :other-services :kiosk?)
         :on-change #(set-field :other-services :kiosk? %)}]]]

     ;; Showers and lockers
     [lui/form-card {:title (tr :facilities/headline)}
      [mui/form-group
       [lui/text-field
        {:label     (tr :facilities/showers-men-count)
         :adornment (tr :units/pcs)
         :value     (-> data :facilities :showers-men-count)
         :on-change #(set-field :facilities :showers-men-count %)}]
       [lui/text-field
        {:label     (tr :facilities/showers-women-count)
         :adornment (tr :units/pcs)
         :value     (-> data :facilities :showers-women-count)
         :on-change #(set-field :facilities :showers-women-count %)}]
       [lui/text-field
        {:label     (tr :facilities/lockers-men-count)
         :adornment (tr :units/pcs)
         :value     (-> data :facilities :lockers-men-count)
         :on-change #(set-field :facilities :lockers-men-count %)}]
       [lui/text-field
        {:label     (tr :facilities/lockers-women-count)
         :adornment (tr :units/pcs)
         :value     (-> data :facilities :lockers-women-count)
         :on-change #(set-field :facilities :lockers-women-count %)}]]]

     ;; Actions
     [lui/form-card {}
      [mui/button {:full-width true
                   :color      "secondary"
                   :variant    "raised"
                   :on-click   #(==> [::events/submit data])}
       (tr :actions/save)]]]))

(defn form-tab [tr]
  [lui/form-card (tr :energy/consumption-info)
   [mui/form-group
    [lui/select {:label (tr :actions/select-hall)
                 :value "h2"
                 :items [{:value "h1" :label "Halli 1"}
                         {:value "h2" :label "Halli 2"}
                         {:value "h3" :label "Halli 3"}]
                 :on-change #(js/alert "FIXME")}]
    [lui/year-selector {:label (tr :time/year)
                        :value 2018
                        :on-change #(js/alert "FIXME")}]
    [lui/text-field {:label (tr :energy/electricity)
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment (tr :physical-units/mwh)])}}]
    [lui/text-field {:label (tr :energy/heat)
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment (tr :physical-units/mwh)])}}]
    [lui/text-field {:label (tr :energy/water)
                     :type "number"
                     :Input-props
                     {:end-adornment
                      (r/as-element
                       [mui/input-adornment (tr :physical-units/m3)])}}]
    [mui/button {:color "secondary"
                 :size "large"}
     (tr :actions/save)]]])

(defn create-panel [tr url]
  (let [active-tab (re-frame/subscribe [::subs/active-tab])]
    [mui/grid {:container true}
     [mui/grid {:item true
                :xs 12}
      [mui/card
       [mui/card-content
        [mui/tabs {:scrollable true
                   :full-width true
                   :text-color "secondary"
                   :on-change #(==> [::events/set-active-tab %2])
                   :value @active-tab}
         [mui/tab {:label (tr :ice-rinks/headline)
                   :icon (r/as-element [mui-icons/info])}]
         [mui/tab {:label (tr :ice-energy/headline)
                   :icon (r/as-element [mui-icons/flash-on])}]
         [mui/tab {:label (tr :ice-basic-data/headline)
                   :icon (r/as-element [mui-icons/edit])}]
         [mui/tab {:label (tr :ice-form/headline)
                   :icon (r/as-element [mui-icons/add])}]]]]]
     [mui/grid {:item true
                :xs 12}
      (case @active-tab
        0 (info-tab url)
        1 (energy-tab tr)
        2 (basic-data-tab tr)
        3 (form-tab tr))]]))

(defn main [tr]
  (let [url "https://liikuntaportaalit.sportvenue.net/Uimahalli"]
    (create-panel tr url)))
