;;  Copyright (c) Cosmin Stejerean. All rights reserved.  The use and
;;  distribution terms for this software are covered by the Eclipse Public
;;  License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;;  be found in the file epl-v10.html at the root of this distribution.  By
;;  using this software in any fashion, you are agreeing to be bound by the
;;  terms of this license.  You must not remove this notice, or any other,
;;  from this software.

(ns clojure.java.test-data
  (:use clojure.java.data)
  (:use [clojure.tools.logging :only (log* info)])
  (:use clojure.test)
  (:import (clojure.java.data.test Person Address State)))

(deftest clojure-to-java
  (let [person (to-java Person {:name "Bob" 
                                :age 30 
                                :address {:line1 "123 Main St" 
                                          :city "Dallas" 
                                          :state "TX" 
                                          :zip "75432"}})]
    (is (= "Bob" (.getName person)))
    (is (= 30 (.getAge person)))
    (is (= "123 Main St" (.. person getAddress getLine1)))
    (is (= "Dallas" (.. person getAddress getCity)))
    (is (= State/TX (.. person getAddress getState)))
    (is (= "75432" (.. person getAddress getZip)))))

(deftest clojure-to-java-error-on-missing-setter
  (binding [*to-java-object-missing-setter* :error]
    (is (thrown-with-msg? NoSuchFieldException #"Missing setter for :foobar in clojure.java.data.test.Person"
          (to-java Person {:name "Bob" :foobar "Baz"})
          ))))

(deftest clojure-to-java-ignore-on-missing-setter
  (binding [*to-java-object-missing-setter* :ignore]
    (let [person (to-java Person {:name "Bob" :foobar "Baz"})]
      (is (= "Bob" (.getName person))))))

(defmacro with-temporary-root [[var-name new-value] & body]
  `(let [current-var# ~var-name]
     (alter-var-root (var ~var-name) (fn [ignore#] ~new-value))
     ~@body
     (alter-var-root (var ~var-name) (fn [ignore#] current-var#)))
  )

(deftest clojure-to-java-log-on-missing-setter
  (binding [*to-java-object-missing-setter* :log]
    (with-temporary-root [log* (fn [log level throwable message]
                                 (throw (new Exception (str "invoked " level))))]
      (is (thrown-with-msg? Exception #"invoked :info"
            (to-java Person {:name "Bob" :foobar "Baz"}))))))

(deftest java-to-clojure
  (let [address (new Address "123 Main St" "Dallas" State/TX "75432")
        person (from-java (Person. "Bob" (biginteger 30) address))]
    (is (= "Bob" (:name person)))
    (is (= 30 (:age person)))
    (is (= "123 Main St" (:line1 (:address person))))
    (is (= "TX" (:state (:address person))))))
