(ns portal.ui.viewer.csv
  (:require ["papaparse" :refer [parse]]
            [portal.ui.inspector :as ins]))

(defn parse-csv [csv-string]
  (try
    (with-meta
      (js->clj (.-data (parse csv-string)))
      {:portal.viewer/default :portal.viewer/table})
    (catch :default _e ::invalid)))

(defn csv? [value] (string? value))

(defn inspect-csv [csv-string]
  [ins/tabs
   {:portal.viewer/csv (parse-csv csv-string)
    "..."              csv-string}])

(def viewer
  {:predicate csv?
   :component inspect-csv
   :name :portal.viewer/csv})
