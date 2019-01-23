(ns lipas.ui.front-page.routes
  (:require
   [lipas.ui.utils :as utils :refer [==>]]))

(def routes
  ["etusivu"
   {:name :lipas.ui.routes/front-page
    :controllers
    [{:start
      (fn []
        (==> [:lipas.ui.events/set-active-panel :front-page-panel]))}]}])