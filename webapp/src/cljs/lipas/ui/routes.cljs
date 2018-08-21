(ns lipas.ui.routes
  (:require-macros [secretary.core :refer [defroute]])
  (:import goog.History)
  (:require [goog.events :as gevents]
            [goog.history.EventType :as EventType]
            [lipas.ui.utils :refer [==>] :as utils]
            [secretary.core :as secretary]))

(defn navigate!
  ([path]
   (navigate! path :comeback? false))
  ([path & {:keys [comeback?]}]
   (when comeback?
     (==> [:lipas.ui.login.events/set-comeback-path (utils/current-path)]))
   (set! (.-location js/window) path)))

(defn hook-browser-navigation! []
  (doto (History.)
    (gevents/listen
     EventType/NAVIGATE
     (fn [event]
       (let [token (.-token event)]
         (secretary/dispatch! token)
         (js/ga "send" "pageview" token))))
    (.setEnabled true)))

(defn app-routes []
  (secretary/set-config! :prefix "#")

  ;;; Front page ;;;

  (defroute "/" []
    (==> [:lipas.ui.events/set-active-panel :home-panel]))

  ;;; Sports sites ;;;

  (defroute "/liikuntapaikat" []
    (==> [:lipas.ui.events/set-active-panel :sports-panel]))

  ;;; Ice stadiums

  (defroute "/jaahalliportaali" []
    (==> [:lipas.ui.events/set-active-panel :ice-panel])
    (==> [:lipas.ui.ice-stadiums.events/display-site nil])
    (==> [:lipas.ui.ice-stadiums.events/set-active-tab 0]))

  (defroute "/jaahalliportaali/halli/:lipas-id" {lipas-id :lipas-id}
    (let [lipas-id (js/parseInt lipas-id)]
      (==> [:lipas.ui.events/set-active-panel :ice-panel])
      (==> [:lipas.ui.ice-stadiums.events/set-active-tab 0])
      (==> [:lipas.ui.ice-stadiums.events/display-site {:lipas-id lipas-id}])))

  (defroute "/jaahalliportaali/ilmoita-tiedot" []
    (==> [:lipas.ui.events/set-active-panel :ice-panel])
    (==> [:lipas.ui.ice-stadiums.events/set-active-tab 1]))

  (defroute "/jaahalliportaali/hallien-vertailu" []
    (==> [:lipas.ui.events/set-active-panel :ice-panel])
    (==> [:lipas.ui.ice-stadiums.events/set-active-tab 2]))

  (defroute "/jaahalliportaali/energia-info" []
    (==> [:lipas.ui.events/set-active-panel :ice-panel])
    (==> [:lipas.ui.ice-stadiums.events/set-active-tab 3]))

  (defroute "/jaahalliportaali/raportit" []
    (==> [:lipas.ui.events/set-active-panel :ice-panel])
    (==> [:lipas.ui.ice-stadiums.events/set-active-tab 4]))

   ;;; Swimming pools

  (defroute "/uimahalliportaali" []
    (==> [:lipas.ui.events/set-active-panel :swim-panel])
    (==> [:lipas.ui.swimming-pools.events/set-active-tab 0]))

  (defroute "/uimahalliportaali/hallit" []
    (==> [:lipas.ui.events/set-active-panel :swim-panel])
    (==> [:lipas.ui.swimming-pools.events/display-site nil])
    (==> [:lipas.ui.swimming-pools.events/set-active-tab 1]))

  (defroute "/uimahalliportaali/hallit/:lipas-id" {lipas-id :lipas-id}
    (let [lipas-id (js/parseInt lipas-id)]
      (==> [:lipas.ui.events/set-active-panel :swim-panel])
      (==> [:lipas.ui.swimming-pools.events/set-active-tab 1])
      (==> [:lipas.ui.swimming-pools.events/display-site {:lipas-id lipas-id}])))

  (defroute "/uimahalliportaali/ilmoita-tiedot" []
    (==> [:lipas.ui.events/set-active-panel :swim-panel])
    (==> [:lipas.ui.swimming-pools.events/set-active-tab 2]))

  (defroute "/uimahalliportaali/hallien-vertailu" []
    (==> [:lipas.ui.events/set-active-panel :swim-panel])
    (==> [:lipas.ui.swimming-pools.events/set-active-tab 3]))

  (defroute "/uimahalliportaali/energia-info" []
    (==> [:lipas.ui.events/set-active-panel :swim-panel])
    (==> [:lipas.ui.swimming-pools.events/set-active-tab 4]))

   ;;; User ;;;

  (defroute "/kirjaudu" []
    (==> [:lipas.ui.events/set-active-panel :login-panel]))

  (defroute "/passu-hukassa" []
    (==> [:lipas.ui.events/set-active-panel :reset-password-panel]))

  (defroute "/rekisteroidy" []
    (==> [:lipas.ui.events/set-active-panel :register-panel]))

  (defroute "/profiili" []
    (==> [:lipas.ui.events/set-active-panel :user-panel]))

  ;; --------------------
  (hook-browser-navigation!))
