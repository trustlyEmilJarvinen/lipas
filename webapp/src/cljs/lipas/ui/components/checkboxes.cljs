(ns lipas.ui.components.checkboxes
  (:require
   [lipas.ui.mui :as mui]
   [reagent.core :as r]))

(defn checkbox
  [{:keys [label value on-change disabled style icon checked-icon tooltip]
    :or   {tooltip ""}}]
  [mui/tooltip {:title tooltip}
   [mui/form-control-label
    {:label   (r/as-element [mui/typography {:variant "body1"} label])
     :style   (merge {:width :fit-content} style)
     :control (r/as-element
               [mui/checkbox
                (merge
                 {:value     (str (boolean value))
                  :checked   (boolean value)
                  :disabled  disabled
                  ;; %2 = checked?
                  :on-change #(on-change %2)}
                 (when icon
                   {:icon (r/as-element icon)})
                 (when checked-icon
                   {:checked-icon (r/as-element checked-icon)}))])}]])
