(ns lipas.ui.user.views
  (:require
   [lipas.ui.mui :as mui]
   [lipas.ui.components :as lui]
   [lipas.ui.user.subs :as subs]
   [lipas.ui.user.events :as events]
   [lipas.ui.utils :refer [<== ==> navigate!]]))

(defn user-form [tr data]
  [mui/form-group

   ;; Email
   [lui/text-field
    {:label    (tr :lipas.user/email)
     :value    (:email data)
     :disabled true}]

   ;; Username
   [lui/text-field
    {:label    (tr :lipas.user/username)
     :value    (:username data)
     :disabled true}]

   ;; Firstname
   [lui/text-field
    {:label    (tr :lipas.user/firstname)
     :value    (-> data :user-data :firstname)
     :disabled true}]

   ;; Lastname
   [lui/text-field
    {:label    (tr :lipas.user/lastname)
     :value    (-> data :user-data :lastname)
     :disabled true}]

   ;; Permissions request
   [lui/text-field
    {:label    (tr :lipas.user/requested-permissions)
     :value    (-> data :user-data :permissions-request)
     :disabled true}]])

(defn actions-dialog [tr]
  (let [site     (<== [::subs/selected-sports-site])
        close    #(==> [::events/select-sports-site nil])
        lipas-id (:lipas-id site)]
    [mui/dialog {:open     (some? (seq site))
                 :on-close close}

     [mui/dialog-title
      (or (:name site) "")]

     [mui/dialog-content
      [mui/list

       ;; View basic info button
       [mui/list-item
        {:button   true
         :on-click (comp
                    close
                    #(==> [:lipas.ui.events/display lipas-id]))}
        [mui/list-item-icon
         [mui/icon "keyboard_arrow_right"]]
        [mui/typography {:variant "body2"}
         (tr :lipas.user/view-basic-info)]]

       ;; Report energy consumption button
       [mui/list-item
        {:button   true
         :on-click (comp
                    close
                    #(==> [:lipas.ui.events/report-energy-consumption lipas-id]))}
        [mui/list-item-icon
         [mui/icon "keyboard_arrow_right"]]
        [mui/typography {:variant "body2"}
         (tr :lipas.user/report-energy-and-visitors)]]]]

     [mui/dialog-actions
      [mui/button {:on-click close}
       (tr :actions/cancel)]]]))

(defn user-panel [tr user]
  (let [admin? (<== [::subs/admin?])
        cities (<== [::subs/permission-to-cities])
        types  (<== [::subs/permission-to-types])

        sites (<== [::subs/sports-sites (tr)])

        all-types?  (or admin? (-> user :permissions :all-types?))
        all-cities? (or admin? (-> user :permissions :all-cities?))

        locale     (tr)
        card-props {:square true}

        firstname (-> user :user-data :firstname)
        lastname  (-> user :user-data :lastname)

        saved-searches (<== [::subs/saved-searches])]

    [mui/grid {:container true :spacing 8 :style {:padding 8} :direction "column"}

     ;; Promo card
     (when (or admin? all-types? (some #{2150} (keys types)))
       [mui/grid {:item true :xs 12}
        [mui/card card-props
         [mui/card-header {:title (tr :user/promo-headline)}]
         [mui/card-content
          [mui/typography {:variant "h5"}
           (tr :user/promo1-topic)]
          [mui/typography {:style {:margin-top "1em" :margin-bottom "1em"}}
           (tr :user/promo1-text)]
          [mui/button
           {:variant  "contained"
            :color    "secondary"
            :on-click (fn []
                        (==> [:lipas.ui.search.events/set-filters-by-permissions])
                        (==> [:lipas.ui.search.events/set-type-filter [2150]])
                        (==> [:lipas.ui.events/navigate :lipas.ui.routes.map/map]))}
           (tr :user/promo1-link)]]]])

     ;; Profile card
     [mui/grid {:item true :xs 12}
      [mui/card card-props
       [mui/card-header {:title (tr :user/greeting firstname lastname)}]
       [mui/card-content
        [user-form tr user]]
       [mui/card-actions
        [mui/button {:href  "/etusivu"
                     :color :secondary}
         (str "> " (tr :user/front-page-link))]
        [mui/button {:href  "/passu-hukassa"
                     :color :primary}
         (str "> " (tr :reset-password/change-password))]
        (when admin?
          [mui/button {:href  "/admin"
                       :color :primary}
           (str "> " (tr :user/admin-page-link))])]]]

     ;; Permissions
     [mui/grid {:item true :xs 12}

      [actions-dialog tr]

      [mui/card (merge card-props)
       [mui/card-header {:title (tr :lipas.user/permissions)}]
       [mui/card-content

        (when admin?
          [lui/icon-text
           {:icon "lock_open"
            :text (tr :lipas.admin/access-all-sites)}])

        (when (and all-cities? (not admin?))
          [lui/icon-text
           {:icon "lock_open"
            :text (tr :lipas.user/permission-to-all-cities)}])

        (when (and (not-empty cities) (not all-cities?))
          [:<>
           [lui/icon-text
            {:icon "lock_open"
             :text (tr :lipas.user/permission-to-cities)}]
           (into
            [mui/list {:dense true}]
            (for [s (->> cities (map (comp locale :name second)))]
              [mui/list-item
               [mui/list-item-text s]]))])

        (when (and all-types? (not admin?))
          [lui/icon-text
           {:icon "lock_open"
            :text (tr :lipas.user/permission-to-all-types)}])

        (when (and (not-empty types) (not all-types?))
          [:<>
           [lui/icon-text
            {:icon "lock_open"
             :text (tr :lipas.user/permission-to-types)}]
           (into
            [mui/list {:dense true}]
            (for [t (->> types (map (comp locale :name second)))]
              [mui/list-item
               [mui/list-item-text t]]))])

        (when (and (not all-cities?) (not all-types?) (empty? sites)
                   (empty? cities) (empty? types))
          [mui/grid {:container true}
           [lui/icon-text
            {:icon "lock"
             :text (tr :lipas.user/no-permissions)}]])

        (when (and (not admin?) (not-empty sites))
          [:<>
           [lui/icon-text
            {:icon "lock_open"
             :text (tr :lipas.user/permission-to-portal-sites)}]
           [lui/table
            {:headers   [[:name (tr :lipas.sports-site/name-short)]
                         [:type (tr :lipas.sports-site/type)]
                         [:city (tr :lipas.location/city)]]
             :items     sites
             :on-select #(==> [::events/select-sports-site %])}]])]

       [mui/card-actions
        [mui/button {:href  "/liikuntapaikat"
                     :color :secondary}
         (str "> " (tr :sport/headline))]

        ;; (when (some #{2510 2520} (map :type-code sites))
        ;;   [mui/button {:href  "/jaahalliportaali"
        ;;                :color :secondary}
        ;;    (str "> " (tr :user/ice-stadiums-link))])
        ;; (when (some #{3110 3130} (map :type-code sites))
        ;;   [mui/button {:href  "/uimahalliportaali"
        ;;                :color :secondary}
        ;;    (str "> " (tr :user/swimming-pools-link))])

        ]]]

     ;; Saved searches
     (when saved-searches
       [mui/grid {:item true :xs 12}
        [mui/card card-props
         [mui/card-header {:title (tr :lipas.user/saved-searches)}]
         [mui/card-content
          [lui/select
           {:label     (tr :actions/select)
            :style     {:width "170px"}
            :items     saved-searches
            :label-fn  :name
            :value-fn  identity
            :on-change #(==> [::events/select-saved-search %])}]]]])]))

(defn main []
  (let [tr         (<== [:lipas.ui.subs/translator])
        logged-in? (<== [::subs/logged-in?])
        user       (<== [::subs/user-data])]

    (if logged-in?
      [user-panel tr user]
      (navigate! "/kirjaudu"))))
