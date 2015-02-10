(ns clj-whisper.core
  (:import (java.io RandomAccessFile))
  (:require [clj-struct.core :as struct]
            [clojure.string :as str]
            [clojure.set :as set]
            [clojure.java.io :as io]))

(def ^:const long-format "!l")
(def ^:const ^int long-size (struct/calcsize long-format))
(def ^:const float-format "!f")
(def ^:const ^int float-size (struct/calcsize float-format))
(def ^:const value-format "!d")
(def ^:const ^int value-size (struct/calcsize value-format))
(def ^:const point-format "!ld")
(def ^:const ^int point-size (struct/calcsize point-format))
(def ^:const metadata-format "!2lfl")
(def ^:const ^int metadata-size (struct/calcsize metadata-format))
(def ^:const archive-info-format "!3l")
(def ^:const ^int archive-info-size (struct/calcsize archive-info-format))

(def ^:const aggregation-type-to-method {1 :average
                                         2 :sum
                                         3 :last
                                         4 :max
                                         5 :min})
(def ^:const aggregation-method-to-type
  (set/map-invert aggregation-type-to-method))
(def ^:const aggregation-methods (vals aggregation-type-to-method))

(def ^:const epoch-future 2085436800)

(defn- read-struct
  "Read structure."
  [^RandomAccessFile file format size keys]
  (let [bytes (byte-array size)]
    (.readFully file bytes 0 size)
    (zipmap keys (struct/unpack format bytes))))

(defn- read-header-metadata
  "Read header metadata."
  [^RandomAccessFile file ^String filename]
  (.seek file 0)
  (read-struct file metadata-format metadata-size
               [:aggregation-type :max-retention :xff :archive-count]))

(defn- read-archive-metadata
  "Read archive metadata."
  [^RandomAccessFile file ^String filename]
  (let [metadata (read-struct file archive-info-format archive-info-size
                              [:offset :seconds-per-point :points])
        {:keys [seconds-per-point points]} metadata]
    (merge metadata {:retention (* seconds-per-point points)
                     :size (* points point-size)})))

(defn- read-archives-metadata
  "Read archives metadata."
  [^RandomAccessFile file ^String filename count]
  (map (fn [_] (read-archive-metadata file filename)) (range count)))

(defn read-info-ra
  "Read info from a random access file."
  [^RandomAccessFile file ^String filename]
  (let [original-offset (.getFilePointer file)
        header-metadata (read-header-metadata file filename)
        {:keys [aggregation-type max-retention xff archive-count]} header-metadata
        archives-metadata (read-archives-metadata file filename archive-count)]
    (dorun archives-metadata)
    (.seek file original-offset)
    {:aggregation-method (get aggregation-type-to-method
                              aggregation-type :average)
     :max-retention max-retention
     :x-files-factor xff
     :archives archives-metadata}))

(defn read-info
  "Read info."
  [^String file]
  (let [ra-file (RandomAccessFile. file "r")]
    (try
      (read-info-ra ra-file file)
      (finally
        (.close ra-file)))))

(defn- read-base-interval
  "Read base interval."
  [^RandomAccessFile file ^String filename offset]
  (.seek file offset)
  (read-struct file point-format point-size [:base-interval :base-value]))

(defn- read-series
  "Read series."
  [^RandomAccessFile file ^String filename offset size]
  (.seek file offset)
  (let [bytes (byte-array size)]
    (.readFully file bytes 0 size)
    (vec bytes)))

(defn align-time
  "Align time to seconds per point."
  [time seconds-per-point]
  (if (not= time nil)
    (-> time (quot seconds-per-point) (* seconds-per-point))
    nil))

(defn- get-point
  "Get point."
  [^clojure.lang.PersistentVector raw-series ^clojure.lang.BigInt ndx]
  (let [start (* ndx point-size)
        end (+ start point-size)
        ppoint (subvec raw-series start end)]
    (struct/unpack point-format ppoint)))

(defn- limit-series
  "Limit time series."
  [series from-time until-time]
  (if (and (= from-time nil)
           (= until-time nil))
    series
    (let [from-time (if from-time from-time 0)
          until-time (if until-time until-time epoch-future)]
      (filter #(let [time (first %)] (and (>= time from-time)
                                          (<= time until-time)))
              series))))

(defn fetch-archive-seq-ra
  "Fetch archive from a random access file."
  [^RandomAccessFile file ^String filename archive from-time until-time]
  (let [{:keys [offset seconds-per-point size]} archive
        from-interval (align-time from-time seconds-per-point)
        until-interval (align-time until-time seconds-per-point)
        {:keys [base-interval]} (read-base-interval file filename offset)]
    (if (= base-interval 0)
      {:series []
       :from from-interval
       :until until-interval
       :step seconds-per-point}
      (let [raw-series (read-series file filename offset size)
            points (/ (count raw-series) point-size)
            full-series (map (partial get-point raw-series) (range points))
            series (limit-series full-series from-interval until-interval)]
        {:series series
         :from from-interval
         :until until-interval
         :step seconds-per-point}))))

(defn fetch-archive-seq
  "Fetch archive."
  [^String file archive from-time until-time]
  (let [ra-file (RandomAccessFile. file "r")]
    (try
      (fetch-archive-seq-ra ra-file file archive from-time until-time)
      (finally
        (.close ra-file)))))

(defn get-paths
  "Get paths."
  [dir]
  (->> dir
       (clojure.java.io/file)
       (file-seq)
       (remove #(.isDirectory %))
       (map #(.getPath %))
       (filter #(re-matches #"^.*\.wsp$" %))))

(defn file-to-name
  "Convert file name to path name.
  Example: /tmp/whisper/example/sum/all.wsp, /tmp/whisper -> example.sum.all"
  [path dir]
  (-> path
      (io/file)
      (.getCanonicalPath)
      (subs (inc (count (.getCanonicalPath (io/file dir)))))
      (str/replace #"\.wsp$" "")
      (str/replace \/ \.)))
