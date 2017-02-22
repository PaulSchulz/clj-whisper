# clj-whisper

[![GitHub license](https://img.shields.io/badge/license-MIT-blue.svg)](https://raw.githubusercontent.com/cybem/clj-whisper/master/LICENSE)
[![Build Status](https://travis-ci.org/cybem/clj-whisper.svg?branch=master)](https://travis-ci.org/cybem/clj-whisper)
[![Dependencies Status](http://jarkeeper.com/cybem/clj-whisper/status.svg)](http://jarkeeper.com/cybem/clj-whisper)

A library for reading Whisper database files from Clojure

[![Clojars Project](http://clojars.org/clj-whisper/latest-version.svg)](http://clojars.org/clj-whisper)

# How to use it
```clojure
(ns your-package
  (:require [clj-whisper.core :as whisper]))
```

# Interface to Whisper files

## (whisper-create)
Not Implemented

## read-info (whisper-info)
```clojure
  (whisper/read-info filename)
```

## (whisper-diff)
Not Implemented

## (whisper-merge)
Not Implemented

## (whisper-dump)
Not Implemented

## (whiusper-resize)
Not Implemented

## fetch-archive-seq (whisper-fetch)
```clojure
(whisper/fetch-archive-seq filename archive from-time until-time)
```
archive - these are the archive details obtained from 'read-info'. A
  Whisper file can contain multiple archives.
```clojure
(-> (whisper/read-info filename) :archives first)
  {:offset 40,
   :seconds-per-point 1,
   :points 86400,
   :retention 86400N,
   :size 1036800N}

(def archive (-> (whisper/read-info filename) :archives first))
   
```

from-time until-time
```clojure
(fetch-archive-seq filename archive 0 1487769175)
```
## (whisper-set-aggregation-method)
Not Implemented

## (whisper-fill)
Not Implemented

## (whisper-update)
Not Implemented

# Utilities

## sort-series

## remove-nulls
Remove null time points from series.

## get-paths

## file-to-name

