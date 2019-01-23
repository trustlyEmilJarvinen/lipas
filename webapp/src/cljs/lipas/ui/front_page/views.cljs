(ns lipas.ui.front-page.views
  (:require
   [lipas.ui.components :as lui]
   [lipas.ui.mui :as mui]
   [lipas.ui.svg :as svg]
   [lipas.ui.utils :refer [==> navigate!]]
   [reagent.core :as r]))

(def links
  {:github    "https://github.com/lipas-liikuntapaikat"
   :lipas-api "http://lipas.cc.jyu.fi/api/index.html"
   :geoserver "http://lipas.cc.jyu.fi/geoserver"
   :lipasinfo "https://www.jyu.fi/sport/fi/yhteistyo/lipas-liikuntapaikat.fi"
   :open-data "https://bit.ly/2v6wE9t"
   :youtube   "https://www.youtube.com/channel/UC-QFmRIY1qYPXX79m23JC4g"})

(def logos
  [{:img "img/partners/okm.png"}
   {:img "img/partners/jaakiekkoliitto.svg" :full-height? true}
   {:img "img/partners/kuntaliitto.png"}
   {:img "img/partners/metsahallitus.svg"}
   {:img "img/partners/sport_venue.png"}
   {:img "img/partners/suh.png"}
   {:img "img/partners/syke.svg" :full-height? true}
   {:img "img/partners/ukty.png"}
   {:img "img/partners/vtt.svg"}
   {:img "img/partners/avi.png"}])

(defn ->logo [{:keys [img full-height?]}]
  [:img
   {:style
    (merge
     {:margin     "1em"
      :max-width  "200px"
      :max-height "100px"}
     (when full-height? ;; For IE11.
       {:height "100%"}))
    :src   img}])

(defn footer [tr]
  (into
   [mui/grid {:item  true :xs 12
              :style {:padding "2em" :background-color mui/gray1}}
    [mui/hidden {:smUp true}
     [mui/typography {:variant "h4" :style {:opacity 0.7}}
      (tr :partners/headline)]]
    [mui/hidden {:xsDown true}
     [mui/typography {:variant "h3" :style {:opacity 0.7}}
      (tr :partners/headline)]]]
   (map ->logo logos)))

(defn grid-card [{:keys [title style link link-text xs md lg]
                  :or   {xs 12 md 6 lg 4}} & children]
  [mui/grid {:item true :xs xs :md md :lg lg}
   [mui/card
    {:square true
     :raised true
     :style
     (merge
      {:background-color "rgb(250, 250, 250)"
       ;;:background-color "#e9e9e9"
       :font-size        "1.25em"
       :opacity          0.95}
      style)}
    [mui/card-header
     (merge
      {:title  title
       :action (when link
                 (r/as-element
                  [mui/icon-button
                   {:href  link
                    :color :secondary}
                   [mui/icon "arrow_forward_ios"]]))}
      (when link
        {:titleTypographyProps
         {:component "a"
          :href      link
          :style     {:font-weight     600
                      :text-decoration "none"}}}))]
    (into [mui/card-content] children)
    [mui/card-actions
     (when link-text
       [mui/button {:variant :text :color "secondary" :href link}
        (str "> " link-text)])]]])

(defn dense-list [{:keys [] :as props} & items]
  (into [mui/list {:dense true}] items))

(defn list-item [{:keys [href text icon]
                  :or   {icon "arrow_right"}}]
  [mui/list-item
   [mui/list-item-icon
    [mui/icon icon]]
   [mui/typography {:variant "body2"}
    text]])

(defn create-panel [tr]
  [mui/grid {:container true}

   [mui/mui-theme-provider {:theme mui/jyu-theme-dark}

    ;; "Jumbotron" header
    [mui/grid {:item true :xs 12}
     [mui/paper {:square true
                 :style  {:background-color mui/secondary
                          ;; :text-align       :center
                          :padding          "1em 2em 1em 2em"}}

      [mui/grid {:container   true
                 :align-items :center
                 :spacing     16}

       [mui/grid {:item true :xs 12}
        [mui/typography {:variant "h3" :style {:color :white}}
         "LIPAS"]]

       [mui/grid {:item true}
        [mui/typography {:variant "h6" :style {:color :white}}
         (tr :sport/headline)]]

       [mui/grid {:item true}
        [mui/typography {:variant "h6" :style {:color :white}}
         (tr :ice/headline)]]

       [mui/grid {:item true}
        [mui/typography {:variant "h6" :style {:color :white}}
         (tr :swim/headline)]]]]]]

   ;; Main section with background image
   [mui/grid {:item true :xs 12}
    [mui/grid
     {:container true
      :style
      {:background-position "right center"
       :background-color    mui/gray3
       :background-image    "url('/img/background_full.png')"
       :background-size     :contain
       :background-repeat   :no-repeat}}
     [mui/grid {:item true :xs 12 :md 12 :lg 12}
      [mui/grid {:container true}

       [mui/grid {:item true :xs 12}
        [:div {:style {:padding "1em"}}
         [mui/grid {:container true
                    :spacing   16}

          ;; Sports sites
          [grid-card
           {:title     (tr :sport/headline)
            :link      "/#/liikuntapaikat"
            :link-text (tr :actions/browse)}
           [mui/typography {:variant "body1"}
            (tr :sport/description)]
           [:ul
            [lui/li (tr :sport/up-to-date-information)]
            [lui/li (tr :sport/updating-tools)]
            [lui/li (tr :sport/open-interfaces)]]]

          ;; Ice stadiums portal
          [grid-card
           {:title     (tr :ice/headline)
            :link      "/#/jaahalliportaali"
            :link-text (tr :actions/browse-to-portal)}
           [mui/typography {:variant "body1"}
            (tr :ice/description)]
           [:ul
            [lui/li (tr :ice/basic-data-of-halls)]
            [lui/li (tr :ice/entering-energy-data)]
            [lui/li (tr :ice/updating-basic-data)]]]

          ;; Swimming pools portal
          [grid-card
           {:title     (tr :swim/headline)
            :link      "/#/uimahalliportaali"
            :link-text (tr :actions/browse-to-portal)}
           [mui/typography {:variant "body1"}
            (tr :swim/description)]
           [:ul
            [lui/li (tr :swim/basic-data-of-halls)]
            [lui/li (tr :swim/entering-energy-data)]
            [lui/li (tr :swim/updating-basic-data)]]]

          ;; Open Data
          [grid-card {:title (tr :open-data/headline)}
           [mui/list

            ;; info
            [mui/list-item {:button   true
                            :on-click #(navigate! (:open-data links))}
             [mui/list-item-icon
              [mui/icon "info"]]
             [mui/list-item-text {:primary "Info"}]]

            ;; Lipas-API
            [mui/list-item {:button   true
                            :on-click #(navigate! (:lipas-api links))}
             [mui/list-item-icon
              [:img {:height "24px"
                     :src    "/img/swagger_logo.svg"}]]
             [mui/list-item-text {:primary "Lipas API"}]]

            ;; Geoserver
            [mui/list-item {:button   true
                            :on-click #(navigate! (:geoserver links))}
             [mui/list-item-icon
              [:img {:height "24px"
                     :src    "/img/geoserver_logo.svg"}]]
             [mui/list-item-text "Geoserver"]]

            ;; Github
            [mui/list-item {:button   true
                            :on-click #(navigate! (:github links))}
             [mui/list-item-icon
              [mui/svg-icon
               [svg/github-icon]]]
             [mui/list-item-text {:primary "GitHub"}]]]]

          ;; Help
          [grid-card {:title (tr :help/headline)}

           [mui/list

            ;; Lipasinfo
            [mui/list-item {:button true :on-click #(navigate! (:lipasinfo links))}
             [mui/list-item-icon
              [mui/icon "library_books"]]
             [mui/list-item-text "lipasinfo.fi"]]

            ;; Youtube
            [mui/list-item {:button true :on-click #(navigate! (:lipasinfo links))}
             [mui/list-item-icon
              ;;[mui/icon "play_circle_filled"]
              [mui/icon "video_library"]]
             [mui/list-item-text "Youtube"]]

            ;; Email
            [mui/list-item {:button true :on-click #(navigate! "mailto:lipasinfo@jyu.fi")}
             [mui/list-item-icon
              [mui/icon "email"]]
             [mui/list-item-text "lipasinfo@jyu.fi"]]

            ;; Phone
            [mui/list-item {:button true :on-click #(navigate! "tel:+358400247980")}
             [mui/list-item-icon
              [mui/icon "phone"]]
             [mui/list-item-text "0400 247 980"]]]]]]]]]]]

   [footer tr]])

(defn main [tr]
  (create-panel tr))
