(ns lipas.ui.effects
  (:require [re-frame.core :as re-frame]
            ["zipcelx"]
            ["filesaver"]))

(re-frame/reg-fx
 ::reset-scroll!
 (fn  [_]
   (js/window.scrollTo 0 0)))

(re-frame/reg-fx
 ::download-excel!
 (fn  [config]
   (zipcelx (clj->js config))))

(re-frame/reg-fx
 ::save-as!
 (fn [{:keys [blob filename]}]
   (filesaver/saveAs blob filename)))
