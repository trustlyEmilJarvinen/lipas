(ns lipas.ui.map.map
  (:require
   ["ol"]
   [lipas.ui.map.projection :as proj]
   [clojure.string :as string]
   [goog.object :as gobj]
   [lipas.ui.map.events :as events]
   [lipas.ui.map.subs :as subs]
   [lipas.ui.mui :as mui]
   [lipas.ui.utils :refer [<== ==>] :as utils]
   [lipas.ui.map.styles :as styles]
   [re-frame.core :as re-frame]
   [reagent.core :as r]))

;; (set! *warn-on-infer* true)

(def temp-fid-prefix "temp")

(def mml-resolutions
  #js[8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5, 0.25])

(def mml-matrix-ids (clj->js (range (count mml-resolutions))))

(def geoJSON (ol.format.GeoJSON. #js{:dataProjection    "EPSG:4326"
                                     :featureProjection "EPSG:3067"}))

(defn ->ol-features [geoJSON-features]
  (.readFeatures geoJSON geoJSON-features))

(defn ->geoJSON [ol-features]
  (.writeFeaturesObject geoJSON ol-features))

(defn ->clj [x]
  (js->clj x :keywordize-keys true))

(def ->geoJSON-clj (comp ->clj ->geoJSON))

(defn ->wmts-url [layer-name]
  (str "/mapproxy/wmts/"
       layer-name
       "/{TileMatrixSet}/{TileMatrix}/{TileCol}/{TileRow}.png"))

(def urls
  {:taustakartta (->wmts-url "mml_taustakartta")
   :maastokartta (->wmts-url "mml_maastokartta")
   :ortokuva     (->wmts-url "mml_ortokuva")})

(defn ->wmts [{:keys [url layer-name visible?]
               :or   {visible? false}}]
  (ol.layer.Tile.
   #js{:visible visible?
       :source
       (ol.source.WMTS.
        #js{:url             url
            :layer           layer-name
            :projection      "EPSG:3067"
            :matrixSet       "mml_grid"
            :tileGrid        (ol.tilegrid.WMTS.
                              #js{:origin      proj/epsg3067-top-left
                                  :extent      proj/epsg3067-extent
                                  :resolutions mml-resolutions
                                  :matrixIds   mml-matrix-ids})
            :format          "png"
            :requestEncoding "REST"
            :isBaseLayer     true})}))

(defn init-layers []
  {:basemaps
   {:taustakartta
    (->wmts
     {:url (:taustakartta urls) :layer-name "MML-Taustakartta" :visible? true})
    :maastokartta
    (->wmts
     {:url (:maastokartta urls) :layer-name "MML-Maastokartta"}) :ortokuva
    (->wmts
     {:url (:ortokuva urls) :layer-name "MML-Ortokuva"})}
   :overlays
   {:vectors
    (ol.layer.Vector.
     #js{:source     (ol.source.Vector.)
         :style      styles/feature-style
         :renderMode "image"})
    :edits
    (ol.layer.Vector.
     #js{:source     (ol.source.Vector.)
         :style      #js[styles/edit-style styles/vertices-style]
         :renderMode "vector"})
    :markers
    (ol.layer.Vector.
     #js{:source     (ol.source.Vector.)
         :style      styles/red-marker-style
         :renderMode "image"})}})

(defn init-view [center zoom]
  (ol.View. #js{:center         #js[(:lon center) (:lat center)]
                :extent         proj/epsg3067-extent
                :zoom           zoom
                :projection     "EPSG:3067"
                :resolutions    mml-resolutions
                :units          "m"
                :enableRotation false}))

(defn init-overlay []
  (ol.Overlay. #js{:offset #js[-15 0]
                   :element
                   (js/document.getElementById "popup-anchor")}))

(defn init-map! [{:keys [center zoom]}]
  (let [layers        (init-layers)
        view          (init-view center zoom)
        popup-overlay (init-overlay)

        opts #js {:target   "map"
                  :layers   #js[(-> layers :basemaps :taustakartta)
                                (-> layers :basemaps :maastokartta)
                                (-> layers :basemaps :ortokuva)
                                (-> layers :overlays :vectors)
                                (-> layers :overlays :edits)
                                (-> layers :overlays :markers)]
                  :overlays #js[popup-overlay]
                  :view     view}

        hover (ol.interaction.Select.
               #js{:layers    #js[(-> layers :overlays :vectors)
                                  (-> layers :overlays :markers)]
                   :style     styles/feature-style-hover
                   :condition ol.events.condition.pointerMove})

        select (ol.interaction.Select.
                #js{:layers #js[(-> layers :overlays :vectors)]
                    :style  #js[styles/hover-style styles/red-marker-style]})

        lmap (ol.Map. opts)]

    (.on hover "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (gobj/get e "selected")]
             (.setPosition popup-overlay coords)
             (==> [::events/show-popup (when (not-empty selected)
                                         {:anchor-el (.getElement popup-overlay)
                                          :data      (-> selected ->geoJSON-clj)})]))))

    (.on select "select"
         (fn [e]
           (let [coords   (gobj/getValueByKeys e "mapBrowserEvent" "coordinate")
                 selected (aget (gobj/get e "selected") 0)]
             (.setPosition popup-overlay coords)
             (==> [::events/show-sports-site (when selected
                                               (.get selected "lipas-id"))]))))

    (.on lmap "click"
         (fn [e]
           (==> [::events/hide-address])))

    (.on lmap "moveend"
         (fn [e]
           (let [center (.getCenter view)
                 lonlat (ol.proj.toLonLat center proj/epsg3067)
                 zoom   (.getZoom view)
                 extent (.calculateExtent view)
                 width  (.getWidth ol.extent extent)
                 height (.getHeight ol.extent extent)]

             (when (and (> width 0) (> height 0))
               (==> [::events/set-view center lonlat zoom extent width height])))))

    {:lmap          lmap
     :view          view
     :center        center
     :zoom          zoom
     :layers        layers
     ;; We don't re-create :hover and :select each time when we toggle
     ;; them because it causes buggy behavior. We keep refs to
     ;; singleton instances under special :interactions* key in
     ;; map-ctx where we can find them when they need to be enabled.
     :interactions* {:select select :hover hover}
     :overlays      {:popup popup-overlay}}))

(def finite? (complement infinite?))

(defn fit-to-extent!
  [{:keys [^js/ol.View view ^js.ol.Map lmap] :as map-ctx} extent]
  (let [padding (or (-> map-ctx :mode :content-padding) #js[0 0 0 0])]
    (when (and view lmap (some finite? extent))
      (.fit view extent #js{:size                (.getSize lmap)
                            :padding             (clj->js padding)
                            :constrainResolution true})))
  map-ctx)

;; Popups are rendered 'outside' OpenLayers by React so we need to
;; inform the outside world.
(defn clear-popup! [map-ctx]
  (==> [::events/show-popup nil])
  map-ctx)

(defn update-geoms! [{:keys [layers] :as map-ctx} geoms]
  (let [vectors (-> layers :overlays :vectors)
        source  (.getSource vectors)]

    ;; Remove existing features
    (.clear source)

    ;; Add new geoms
    (doseq [g    geoms
            :let [fs (-> g clj->js ->ol-features)]]
      (.addFeatures source fs))

    (assoc map-ctx :geoms geoms)))

(defn set-basemap! [{:keys [layers] :as map-ctx} basemap]
  (doseq [[k v] (:basemaps layers)
          :let  [visible? (= k basemap)]]
    (.setVisible v visible?))
  map-ctx)

(defn select-features! [{:keys [interactions] :as map-ctx} features]
  (let [select (-> interactions :select)]
    (doto (.getFeatures select)
      (.clear)
      (.extend features))
    map-ctx))

(defn find-feature-by-id [{:keys [layers]} fid]
  (let [layer  (-> layers :overlays :vectors)
        source (.getSource layer)]
    (.getFeatureById source fid)))

(defn find-features-by-lipas-id [{:keys [layers]} lipas-id]
  (let [layer  (-> layers :overlays :vectors)
        source (.getSource layer)
        res    #js[]]
    (.forEachFeature source
                     (fn [f]
                       (when (-> (.getId f)
                                 (string/split "-")
                                 first
                                 (= (str lipas-id)))
                         (.push res f))
                       ;; Iteration stops if truthy val is returned
                       ;; but we want to find all matching features so
                       ;; nil is returned.
                       nil))
    res))

(defn clear-markers! [{:keys [layers] :as map-ctx}]
  (-> layers :overlays :markers .getSource .clear)
  map-ctx)

(defn select-sports-site! [map-ctx lipas-id]
  (if-let [features (not-empty (find-features-by-lipas-id map-ctx lipas-id))]
    (select-features! map-ctx features)
    map-ctx))

;; The snap interaction must be added after the Modify and Draw
;; interactions in order for its map browser event handlers to be
;; fired first. Its handlers are responsible of doing the snapping.
(defn enable-snapping! [{:keys [^js/ol.Map lmap layers] :as map-ctx}]
  (let [source (-> layers :overlays :edits .getSource)
        snap   (ol.interaction.Snap. #js{:source source})]
    (.addInteraction lmap snap)
    (assoc-in map-ctx [:interactions :snap] snap)))

;; Splitter needs to be added before other interactions
(defn enable-splitter! [{:keys [^js/ol.Map lmap layers] :as map-ctx}]
  ;; TODO figure out what's wrong
  ;; (let [source   (-> layers :overlays :edits .getSource)
  ;;       _ (prn "source...")
  ;;       _ (js/console.log (.getFeaturesCollection source))
  ;;       splitter (ol.interaction.Splitter. #js{:source source})]
  ;;   (.addInteraction lmap splitter)
  ;;   (assoc-in map-ctx [:interactions :splitter] splitter)
  ;;   )
  map-ctx)

(defn enable-hover! [{:keys [^js/ol.Map lmap interactions*] :as map-ctx}]
  (let [hover (:hover interactions*)]
    (-> hover .getFeatures .clear)
    (.addInteraction lmap hover)
    (assoc-in map-ctx [:interactions :hover] hover)))

(defn enable-select! [{:keys [^js/ol.Map lmap interactions*] :as map-ctx}]
  (let [select (:select interactions*)]
    (-> select .getFeatures .clear)
    (.addInteraction lmap select)
    (assoc-in map-ctx [:interactions :select] select)))

(defn enable-delete! [{:keys [^js/ol.Map lmap layers] :as map-ctx} on-delete]
  (let [layer  (-> layers :overlays :edits)
        delete (ol.interaction.Select. #js{:layers #js[layer]
                                           :style  styles/hover-style})
        source (.getSource layer)]
    (.addInteraction lmap delete)
    (.on delete "select"
         (fn [e]
           (let [selected (gobj/get e "selected")]
             ;; (.setPosition popup-overlay coords)
             (when (not-empty selected)
               (==> [:lipas.ui.events/confirm "Haluatko poistaa??"
                     (fn []
                       (doseq [f selected]
                         (.removeFeature source f))
                       (doto (.getFeatures delete)
                         (.clear))
                       (on-delete (->geoJSON-clj (.getFeatures source))))])))))
    (assoc-in map-ctx [:interactions :delete] delete)))

(defn start-drawing-hole! [{:keys [^js/ol.Map lmap layers] :as map-ctx}
                           on-modifyend]
  (let [layer     (-> layers :overlays :edits)
        draw-hole (js/ol.interaction.DrawHole. #js{:layers #js[layer]})
        source    (.getSource layer)]
    (.addInteraction lmap draw-hole)
    (.on draw-hole "drawend"
         (fn [e]
           (on-modifyend (->geoJSON-clj (.getFeatures source)))))
    (assoc-in map-ctx [:interactions :draw-hole] draw-hole)))

(defn start-editing! [{:keys [^js/ol.Map lmap layers] :as map-ctx}
                      geoJSON-feature on-modifyend]
  (let [layer     (-> layers :overlays :edits)
        source    (.getSource layer)
        features  (-> geoJSON-feature clj->js ->ol-features)
        _         (.addFeatures source features)
        modify    (ol.interaction.Modify. #js{:source source})
        geom-type (-> geoJSON-feature :features first :geometry :type)
        hover     (ol.interaction.Select.
                   #js{:layers    #js[layer]
                       :style     #js[styles/hover-style styles/vertices-style]
                       :condition ol.events.condition.pointerMove})]

    (.addInteraction lmap hover)

    (let [new-ctx (if (#{"LineString" "Polygon"} geom-type)
                    (enable-splitter! map-ctx)
                    map-ctx)]

      (.addInteraction lmap modify)
      (.on modify "modifyend"
           (fn [e]
             (on-modifyend (->geoJSON-clj (.getFeatures source)))))

      (-> new-ctx
          (assoc-in [:interactions :modify] modify)
          (assoc-in [:interactions :hover] hover)
          enable-snapping!
          (fit-to-extent! (.getExtent source))))))

(defn start-editing-site! [{:keys [layers] :as map-ctx} lipas-id geoms
                           on-modifyend]
  (let [layer    (-> layers :overlays :vectors)
        source   (.getSource layer)
        features (find-features-by-lipas-id map-ctx lipas-id)]
    ;; Remove from original source so we won't display duplicate when
    ;; feature is added to :edits layer.
    (.forEach features
              (fn [f]
                (.removeFeature source f)))
    (start-editing! map-ctx geoms on-modifyend)))

(defn start-drawing! [{:keys [^js/ol.Map lmap layers]
                       :as   map-ctx} geom-type on-draw-end]
  (let [layer  (-> layers :overlays :edits)
        source (.getSource layer)
        draw   (ol.interaction.Draw.
                #js{:snapTolerance 0 :source source :type geom-type})]

    (let [new-ctx (if  (#{"LineString" "Polygon"} geom-type)
                    (enable-splitter! map-ctx)
                    map-ctx)]

      (.addInteraction lmap draw)
      (.on draw "drawend"
           (fn [e]
             (let [f (gobj/get e "feature")
                   _ (.setId f (str (gensym temp-fid-prefix)))]
               (.addFeature source f)
               (on-draw-end (->geoJSON-clj (.getFeatures source))))))

      (-> new-ctx
          (assoc-in [:interactions :draw] draw)
          clear-markers!
          enable-snapping!))))

(defn update-center! [{:keys [^js/ol.View view] :as map-ctx}
                      {:keys [lon lat] :as center}]
  (.setCenter view #js[lon lat])
  (assoc map-ctx :center center))

(defn update-zoom! [{:keys [^js/ol.View view] :as map-ctx} zoom]
  (.setZoom view zoom)
  (assoc map-ctx :zoom zoom))

(defn show-feature! [{:keys [layers] :as map-ctx} geoJSON-feature]
  (let [vectors (-> layers :overlays :edits)
        source  (.getSource vectors)
        fs      (-> geoJSON-feature clj->js ->ol-features)]
    (.addFeatures source fs)
    map-ctx))

(defn clear-interactions! [{:keys [^js/ol.Map lmap interactions interactions*]
                            :as   map-ctx}]
  ;; Special treatment for 'singleton' interactions*. OpenLayers
  ;; doesn't treat 'copies' identical to original ones. Therefore we
  ;; need to pass the original ones explicitly.
  (doseq [v     (vals (merge interactions interactions*))
          :when (some? v)]
    (.removeInteraction lmap v))

  (assoc map-ctx :interactions {}))

;; (defn- existing-feature? [f]
;;   (-> (.getId f)
;;       (string/starts-with? temp-fid-prefix)
;;       not))

(defn clear-edits! [{:keys [layers] :as map-ctx}]
  (let [edits-source (-> layers :overlays :edits .getSource)]
       ;;geoms-source (-> layers :overlays :vectors .getSource)

    ;; We add existing features which have been transferred to :edits
    ;; layer back to :vectors layer.
    ;; (.forEachFeature edits-source
    ;;                  (fn [f]
    ;;                    (when (existing-feature? f)
    ;;                      (.addFeature geoms-source f))))
    (.clear edits-source)
    map-ctx))

;; Adding new features
(defn set-adding-mode! [map-ctx mode]
  (let [map-ctx (clear-interactions! map-ctx)]
    (case (:sub-mode mode)
      :drawing   (start-drawing! map-ctx (:geom-type mode)
                                 (fn [f] (==> [::events/new-geom-drawn f])))
      :editing   (start-editing! map-ctx (:geom mode)
                                 (fn [f] (==> [::events/update-new-geom f])))
      :finished  (show-feature! map-ctx (:geom mode)))))

(defn update-adding-mode! [map-ctx mode]
  (let [old-mode (:mode map-ctx)]
    (if (= (:sub-mode mode) (:sub-mode old-mode))
      map-ctx ;; Noop
      (set-adding-mode! map-ctx mode))))

(defn continue-editing! [{:keys [layers] :as map-ctx} on-modifyend]
  (let [layer (-> layers :overlays :edits)
        fs    (-> layer .getSource .getFeatures ->geoJSON-clj)]
    (-> map-ctx
        clear-edits!
        (start-editing! fs on-modifyend))))

(defn refresh-edits!
  [{:keys [layers] :as map-ctx}
   {:keys [lipas-id geoms]}]
  (let [source   (-> layers :overlays :edits .getSource)
        features (-> geoms clj->js ->ol-features)]

    ;; Remove existing features
    (doseq [f (.getFeatures source)]
      (.removeFeature source f))

    ;; Add geoms from props
    (.addFeatures source features)
    (==> [::events/update-geometries lipas-id geoms])
    (==> [:lipas.ui.map.events/continue-editing])
    map-ctx))

;; Editing existing features
(defn set-editing-mode!
  ([map-ctx mode]
   (set-editing-mode! map-ctx mode false))
  ([map-ctx {:keys [lipas-id geoms geom-type sub-mode] :as mode} continue?]
   (let [map-ctx      (clear-interactions! map-ctx)
         on-modifyend (fn [f]
                        (==> [::events/update-geometries lipas-id f])
                        (when (#{:drawing :drawing-hole :deleting} sub-mode)
                          ;; Switch back to editing normal :editing mode
                          (==> [::events/start-editing lipas-id :editing geom-type])))]

     (case sub-mode
       :drawing      (start-drawing! map-ctx geom-type on-modifyend)
       :drawing-hole (start-drawing-hole! map-ctx on-modifyend) ; For polygons
       :editing      (if continue?
                       (continue-editing! map-ctx on-modifyend)
                       (start-editing-site! map-ctx lipas-id geoms on-modifyend))
       :deleting     (-> map-ctx
                         ;;(continue-editing! on-modifyend)
                         (enable-delete! on-modifyend))
       :importing    (refresh-edits! map-ctx mode)))))

(defn update-editing-mode! [map-ctx mode]
  (let [old-mode (:mode map-ctx)]
    (if (= (:sub-mode mode) (:sub-mode old-mode))
      map-ctx ;; Noop
      (set-editing-mode! map-ctx mode :continue))))

(defn show-address-marker!
  [{:keys [layers] :as map-ctx} address]
  (let [f (.readFeature geoJSON (clj->js address))]
    (.setStyle f styles/blue-marker-style)
    (-> layers :overlays :markers .getSource (.addFeature f))
    map-ctx))

;; Browsing and selecting features
(defn set-default-mode! [map-ctx mode]
  (let [map-ctx (-> map-ctx
                    clear-interactions!
                    clear-edits!
                    clear-markers!
                    enable-hover!
                    enable-select!)]
    (let [lipas-id (:lipas-id mode)
          address  (:address mode)]
      (cond-> map-ctx
        lipas-id (select-sports-site! lipas-id)
        address (show-address-marker! address)))))

(defn update-default-mode!
  [{:keys [layers] :as map-ctx} {:keys [lipas-id fit-nonce address]}]
  (let [fit? (not= fit-nonce (-> map-ctx :mode :fit-nonce))]
    (cond-> map-ctx
      true     (clear-markers!)
      lipas-id (select-sports-site! lipas-id)
      fit?     (fit-to-extent! (-> layers :overlays :vectors .getSource .getExtent))
      address  (show-address-marker! address))))

(defn set-mode! [map-ctx mode]
  (let [map-ctx (case (:name mode)
                  :default   (set-default-mode! map-ctx mode)
                  :editing   (set-editing-mode! map-ctx mode)
                  :adding    (set-adding-mode! map-ctx mode))]
    (assoc map-ctx :mode mode)))

(defn update-mode! [map-ctx mode]
  (let [update? (= (-> map-ctx :mode :name) (:name mode))
        map-ctx (case (:name mode)
                  :default   (if update?
                               (update-default-mode! map-ctx mode)
                               (set-default-mode! map-ctx mode))
                  :editing   (if update?
                               (update-editing-mode! map-ctx mode)
                               (set-editing-mode! map-ctx mode))
                  :adding    (if update?
                               (update-adding-mode! map-ctx mode)
                               (set-adding-mode! map-ctx mode)))]
    (assoc map-ctx :mode mode)))

(defn map-inner []

  ;; Internal state
  (let [map-ctx* (atom nil)]

    (r/create-class

     {:reagent-render
      (fn [] [mui/grid {:id    "map"
                        :item  true
                        :style {:height "100%" :width "100%"}
                        :xs    12}])

      :component-did-mount
      (fn [comp]
        (let [opts    (r/props comp)
              basemap (:basemap opts)
              geoms   (:geoms opts)
              mode    (-> opts :mode)

              map-ctx (-> (init-map! opts)
                          (update-geoms! geoms)
                          (set-basemap! basemap)
                          (set-mode! mode))]

          (reset! map-ctx* map-ctx)))

      :component-did-update
      (fn [comp]
        (let [opts    (r/props comp)
              geoms   (-> opts :geoms)
              basemap (-> opts :basemap)
              center  (-> opts :center)
              zoom    (-> opts :zoom)
              mode    (-> opts :mode)]

          (cond-> @map-ctx*
            (not= (:geoms @map-ctx*) geoms)     (update-geoms! geoms)
            (not= (:basemap @map-ctx*) basemap) (set-basemap! basemap)
            (not= (:center @map-ctx*) center)   (update-center! center)
            (not= (:zoom @map-ctx*) zoom)       (update-zoom! zoom)
            (not= (:mode @map-ctx*) mode)       (update-mode! mode)
            true                                (as-> $ (reset! map-ctx* $)))))

      :display-name "map-inner"})))

(defn map-outer []
  (let [geoms   (re-frame/subscribe [::subs/geometries])
        basemap (re-frame/subscribe [::subs/basemap])
        center  (re-frame/subscribe [::subs/center])
        zoom    (re-frame/subscribe [::subs/zoom])
        mode    (re-frame/subscribe [::subs/mode])]
    (fn []
      [map-inner
       {:geoms   @geoms
        :basemap @basemap
        :center  @center
        :zoom    @zoom
        :mode    @mode}])))
