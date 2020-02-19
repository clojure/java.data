;;  Copyright (c) Sean Corfield. All rights reserved.
;;  The use and distribution terms for this software are covered by the
;;  Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;  which can be found in the file epl-v10.html at the root of this
;;  distribution.  By using this software in any fashion, you are agreeing to
;;  be bound by the terms of this license.  You must not remove this notice,
;;  or any other, from this software.

(ns
  ^{:author "Sean Corfield",
    :doc "Tests for the builder aspects of java.data."}
  clojure.java.data.builder-test
  (:require [clojure.java.data.builder :as b]
            [clojure.test :refer [deftest is]]))

(deftest locale-builder-tests
  (let [l (b/to-java java.util.Locale
                     {:language "fr" :region "FR"})]
    (is (= "fr" (.getLanguage l)))
    (is (= "FR" (.getCountry l))))
  (let [l (b/to-java java.util.Locale
                     {:language "fr" :region "FR"}
                     {:builder-class java.util.Locale$Builder
                      :builder-props {}
                      :build-fn "build"})]
    (is (= "fr" (.getLanguage l)))
    (is (= "FR" (.getCountry l))))
  (let [l (b/to-java java.util.Locale
                     (java.util.Locale$Builder.)
                     {:language "fr" :region "FR"}
                     {})]
    (is (= "fr" (.getLanguage l)))
    (is (= "FR" (.getCountry l))))
  (let [l (b/to-java java.util.Locale
                     (java.util.Locale$Builder.)
                     {:language "fr" :region "FR"}
                     {:builder-class java.util.Locale$Builder
                      :builder-props {}
                      :build-fn "build"})]
    (is (= "fr" (.getLanguage l)))
    (is (= "FR" (.getCountry l)))))
