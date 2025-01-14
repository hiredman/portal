(ns portal.extensions.vs-code-notebook
  (:require [portal.colors :as c]
            [portal.runtime :as rt]
            [portal.ui.app :as app]
            [portal.ui.commands :as commands]
            [portal.ui.icons :as icons]
            [portal.ui.inspector :as ins]
            [portal.ui.options :as opts]
            [portal.ui.select :as select]
            [portal.ui.state :as state]
            [portal.ui.styled :as s]
            [portal.ui.theme :as theme]
            [reagent.core :as r]
            [reagent.dom :as dom]))

(defonce context (atom nil))

(defonce functional-compiler (r/create-compiler {:function-components true}))

(defn send! [msg]
  (when-let [f (get rt/ops (:op msg))]
    (js/Promise.
     (fn [resolve]
       (f msg resolve)))))

(defn- open-external [state value]
  (let [theme (theme/use-theme)]
    [icons/external-link
     {:size "1x"
      :style {:cursor :pointer
              :padding (:padding theme)}
      :on-click
      (fn open-editor [e]
        (.stopPropagation e)
        (.postMessage
         ^js @context
         #js {:type "open-editor"
              :data (binding [*print-meta* true]
                      (pr-str
                       (let [values (state/selected-values @state)]
                         (case (count values)
                           0 value
                           1 (first values)
                           values))))}))}]))

(defn- history-arrow [{:keys [icon title on-click enabled]}]
  (let [state (state/use-state)
        theme (theme/use-theme)
        disabled? (nil? (enabled @state))]
    [icon
     {:disabled disabled?
      :title  title
      :size "1x"
      :on-click #(state/dispatch! state on-click)
      :style    (merge
                 {:cursor :pointer
                  :padding (:padding theme)}
                 (when disabled?
                   {:opacity 0.45
                    :cursor  :default}))}]))

(defn command-button []
  (let [state (state/use-state)
        theme (theme/use-theme)]
    [icons/terminal
     {:size     "1x"
      :title    "Open command palette."
      :style    {:cursor :pointer
                 :padding (:padding theme)}
      :on-click #(commands/open-command-palette state)}]))

(defn- toolbar [& children]
  (let [theme (theme/use-theme)]
    [s/div
     {:class "toolbar"
      :style
      {:z-index 100
       :position :relative
       :min-height (* 2 (:padding theme))}}
     (into
      [s/div
       {:style
        {:right      (* 2 (:padding theme))
         :width      :fit-content
         :position   :absolute
         :border     [1 :solid (::c/border theme)]
         :background (::c/background theme)}}]
      children)]))

(defn command-container [child]
  (let [state (state/use-state)]
    [s/div
     {:on-click (fn [_] (commands/close state))
      :style
      {:box-sizing :border-box
       :max-height 400
       :overflow :auto}}
     child]))

(defn select-viewer []
  (let [theme              (theme/use-theme)
        state              (state/use-state)
        selected-context   (state/get-selected-context @state)
        viewer             (ins/get-viewer state selected-context)
        compatible-viewers (ins/get-compatible-viewers @ins/viewers selected-context)
        disabled?          (nil? selected-context)]
    [s/div
     {:style
      {:display :inline-block
       :position :relative
       :opacity (if disabled? 0.45 1)}}
     [s/select
      {:title "Select a different viewer."
       :disabled disabled?
       :style {:opacity 0
               :position :absolute
               :left 0
               :top 0
               :right 0
               :bottom 0
               :cursor (if disabled? :default :pointer)}
       :value (pr-str (:name viewer))
       :on-change
       (fn [e]
         (ins/set-viewer!
          state
          selected-context
          (keyword (subs (.. e -target -value) 1))))}
      (for [{:keys [name]} compatible-viewers]
        ^{:key name}
        [s/option {:value (pr-str name)} (pr-str name)])]
     [icons/ellipsis-h
      {:size  "1x"
       :style {:padding (:padding theme)
               :z-index 1}
       :style/hover {:background :pink}}]]))

(defn- app* [id value]
  (let [state (r/atom {:portal/value value})]
    (fn []
      (let [opts  (opts/use-options)
            theme (or (:theme @state)
                      (:theme opts)
                      (::c/theme opts))]
        [select/with-position {:id id :row 0 :column 0}
         [s/div
          {:class "app"
           :style {:overflow    :auto
                   :position    :relative
                   :min-height  :fit-content
                   :font-size   (:font-size theme)
                   :font-family (:font-family theme)
                   :on-click    (partial commands/close state)}}
          [state/with-state
           state
           [theme/with-theme
            theme
            [toolbar
             [history-arrow
              {:title    "Go back in portal history."
               :enabled  :portal/previous-state
               :icon     icons/arrow-left
               :on-click state/history-back}]
             [history-arrow
              {:title    "Go forward in portal history."
               :enabled  :portal/next-state
               :icon     icons/arrow-right
               :on-click state/history-forward}]
             [select-viewer]
             [open-external state value]
             [command-button]]
            [app/styles]
            [:style
             ".toolbar {opacity: 0}"
             ".app:hover .toolbar {opacity: 1}"]
            [commands/palette {:container command-container}]
            (when-not (::commands/input @state)
              [ins/inspector (:portal/value @state value)])]]]]))))

(defonce component (r/atom app*))

(defn app [id value] [@component id value])

(defn render-output-item [data element]
  (let [value (try
                (ins/read-string (.text data))
                (catch :default e
                  (ins/error->data e)))]
    (dom/render [app (.-id data) value] element functional-compiler)))

(defn activate [ctx]
  (reset! context ctx)
  (reset! state/sender send!)
  #js {:renderOutputItem #(render-output-item %1 %2)})

(defn reload [] (reset! component app*))
