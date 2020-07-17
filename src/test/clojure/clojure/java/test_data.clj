;;  Copyright (c) Cosmin Stejerean, Sean Corfield. All rights reserved.
;;  The use and distribution terms for this software are covered by the
;;  Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;  which can be found in the file epl-v10.html at the root of this
;;  distribution.  By using this software in any fashion, you are agreeing to
;;  be bound by the terms of this license.  You must not remove this notice,
;;  or any other, from this software.

(ns clojure.java.test-data
  (:require [clojure.java.data :as j
             :refer [from-java from-java-deep from-java-shallow
                     set-properties to-java
                     *to-java-object-missing-setter*]]
            [clojure.string :as str]
            [clojure.tools.logging :refer [log* info]]
            [clojure.test :refer [deftest is testing]])
  (:import (clojure.java.data.test Person Address State Primitive
                                   TestBean6 TestBean9)))

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
          (to-java Person {:name "Bob" :foobar "Baz"})))))


(deftest clojure-to-java-ignore-on-missing-setter
  (binding [*to-java-object-missing-setter* :ignore]
    (let [person (to-java Person {:name "Bob" :foobar "Baz"})]
      (is (= "Bob" (.getName person))))))

(defmacro with-temporary-root [[var-name new-value] & body]
  `(let [current-var# ~var-name]
     (alter-var-root (var ~var-name) (fn [ignore#] ~new-value))
     ~@body
     (alter-var-root (var ~var-name) (fn [ignore#] current-var#))))


(deftest clojure-to-java-log-on-missing-setter
  (binding [*to-java-object-missing-setter* :log]
    (with-temporary-root [log* (fn [log level throwable message]
                                 (throw (new Exception (str "invoked " level))))]
      (is (thrown-with-msg? Exception #"invoked :info"
            (to-java Person {:name "Bob" :foobar "Baz"}))))))

;; feature testing macro, based on suggestion from Chas Emerick:
(defmacro ^{:private true} when-available
  [sym & body]
  (try
    (when (resolve sym)
      (list* 'do body))
    (catch ClassNotFoundException _#)))

(defmacro ^{:private true} when-not-available
  [sym & body]
  (try
    (when-not (resolve sym)
      (list* 'do body))
    (catch ClassNotFoundException _#)))

(when-available
 biginteger
 (defn- to-BigInteger [v] (biginteger v)))

(when-not-available
 biginteger
 (defn- to-BigInteger [v] (bigint v)))

(deftest java-to-clojure
  (testing "regular"
    (let [address (new Address "123 Main St" "Dallas" State/TX "75432")
          person (from-java (Person. "Bob" (to-BigInteger 30) address))]
      (is (= "Bob" (:name person)))
      (is (= 30 (:age person)))
      (is (= "123 Main St" (:line1 (:address person))))
      (is (= "TX" (:state (:address person))))))
  (testing "deep"
    (let [address (new Address "123 Main St" "Dallas" State/TX "75432")
          person (from-java-deep (Person. "Bob" (to-BigInteger 30) address) {})]
      (is (= "Bob" (:name person)))
      (is (= 30 (:age person)))
      (is (= "123 Main St" (:line1 (:address person))))
      (is (= "TX" (:state (:address person))))))
  (testing "shallow"
    (let [address (new Address "123 Main St" "Dallas" State/TX "75432")
          person (from-java-shallow (Person. "Bob" (to-BigInteger 30) address) {})]
      (is (= "Bob" (:name person)))
      (is (= 30 (:age person)))
      (is (instance? Address (:address person))))))

(deftest primitives
  (testing "regular"
    (let [datum {:boolMember true
                 :boolArray [true false]
                 :charMember \H
                 :charArray (map identity "Hello World")
                 :byteMember 127
                 :byteArray [1 2 3]
                 :shortMember 15000
                 :shortArray [13000 14000 15000]
                 :intMember 18000
                 :intArray [1 2 3]
                 :longMember 60000000
                 :longArray [1 2 3]
                 :floatMember 1.5
                 :floatArray [1.5 2.5 3.5]
                 :doubleMember 1.5
                 :doubleArray [1.5 2.0 2.5]
                 :nestedIntArray [[1 2] [3] [4 5 6] []]
                 :stringArray ["Argument" "Vector"]}]
      (is (= datum
             (from-java (to-java Primitive datum))))))
  (testing "deep"
    (let [datum {:boolMember true
                 :boolArray [true false]
                 :charMember \H
                 :charArray (map identity "Hello World")
                 :byteMember 127
                 :byteArray [1 2 3]
                 :shortMember 15000
                 :shortArray [13000 14000 15000]
                 :intMember 18000
                 :intArray [1 2 3]
                 :longMember 60000000
                 :longArray [1 2 3]
                 :floatMember 1.5
                 :floatArray [1.5 2.5 3.5]
                 :doubleMember 1.5
                 :doubleArray [1.5 2.0 2.5]
                 :nestedIntArray [[1 2] [3] [4 5 6] []]
                 :stringArray ["Argument" "Vector"]}]
      (is (= datum
             (from-java-deep (to-java Primitive datum) {})))))
  (testing "shallow"
    (let [datum {:boolMember true
                 :boolArray [true false]
                 :charMember \H
                 :charArray (map identity "Hello World")
                 :byteMember 127
                 :byteArray [1 2 3]
                 :shortMember 15000
                 :shortArray [13000 14000 15000]
                 :intMember 18000
                 :intArray [1 2 3]
                 :longMember 60000000
                 :longArray [1 2 3]
                 :floatMember 1.5
                 :floatArray [1.5 2.5 3.5]
                 :doubleMember 1.5
                 :doubleArray [1.5 2.0 2.5]
                 :nestedIntArray [[1 2] [3] [4 5 6] []]
                 :stringArray ["Argument" "Vector"]}]
      (reduce-kv (fn [_ k v]
                   (if (.endsWith ^String (name k) "Array")
                     (if (= k :nestedIntArray)
                       (is (= (get datum k) (map #(into [] %) v)))
                       (is (= (get datum k) (seq v))))
                     (is (= (get datum k) v))))
                 nil
                 (from-java-shallow (to-java Primitive datum) {})))))

(deftest java-map
  (let [datum {"a" "one" "b" 2 "c" (java.net.URI. "")}]
    (testing "regular" ; is actually shallow on hash maps
      (is (= datum (from-java (to-java java.util.Map datum)))))
    (testing "deep"
      (is (= (update datum "c" from-java)
             (from-java-deep (to-java java.util.Map datum) {}))))
    (testing "shallow"
      (is (= datum (from-java-shallow (to-java java.util.Map datum) {}))))))

(deftest jdata-6
  (testing "regular"
    (let [bean-instance (TestBean6.)
          _ (. bean-instance setFoo {"bar" "baz"})
          bean-instance-as-map (from-java bean-instance)
          new-bean-instance (to-java TestBean6 bean-instance-as-map)]
      (is (= {"bar" "baz"} (:foo bean-instance-as-map)))
      (is (= {"bar" "baz"} (.getFoo new-bean-instance)))))
  (testing "deep"
    (let [bean-instance (TestBean6.)
          _ (. bean-instance setFoo {"bar" "baz"})
          bean-instance-as-map (from-java-deep bean-instance {})
          new-bean-instance (to-java TestBean6 bean-instance-as-map)]
      (is (= {"bar" "baz"} (:foo bean-instance-as-map)))
      (is (= {"bar" "baz"} (.getFoo new-bean-instance)))))
  (testing "shallow"
    (let [bean-instance (TestBean6.)
          _ (. bean-instance setFoo {"bar" "baz"})
          bean-instance-as-map (from-java-shallow bean-instance {})
          new-bean-instance (to-java TestBean6 bean-instance-as-map)]
      (is (= {"bar" "baz"} (:foo bean-instance-as-map)))
      (is (= {"bar" "baz"} (.getFoo new-bean-instance))))))

(deftest jdata-8-11-date
  (testing "regular"
    (let [d (java.util.Date.)]
      (is (= d (to-java java.util.Date (from-java d))))))
  (testing "deep"
    (let [d (java.util.Date.)]
      (is (= d (to-java java.util.Date (from-java-deep d {}))))))
  (testing "shallow"
    (let [d (java.util.Date.)]
      (is (= d (to-java java.util.Date (from-java-shallow d {})))))))

(when-available
  java.time.Instant
  (deftest jdata-8-11-instant
    (testing "regular"
      (let [t (java.time.Instant/now)]
        (is (= t (to-java java.time.Instant (from-java t))))))
    (testing "deep"
      (let [t (java.time.Instant/now)]
        (is (= t (to-java java.time.Instant (from-java-deep t {}))))))
    (testing "shallow"
      (let [t (java.time.Instant/now)]
        (is (= t (to-java java.time.Instant (from-java-shallow t {}))))))))

(deftest jdata-9
  (let [bean-instance (TestBean9.)
        _ (.setAString bean-instance "something")
        _ (.setABool bean-instance true)
        _ (.setABoolean bean-instance false)]
    (testing "regular"
      (is (= {:AString "something" :ABool true}
             ;; :ABoolean missing because 'is' Boolean is not a getter
             (from-java bean-instance))))
    (testing "deep"
      (is (= {:AString "something" :ABool true}
             ;; :ABoolean missing because 'is' Boolean is not a getter
             (from-java-deep bean-instance {}))))
    (testing "shallow"
      (is (= {:AString "something" :ABool true}
             ;; :ABoolean missing because 'is' Boolean is not a getter
             (from-java-shallow bean-instance {}))))))

(deftest jdata-10
  (testing "regular"
    (is (if (:absolute (from-java (java.net.URI. ""))) false true))
    (is (if (:opaque (from-java (java.net.URI. ""))) false true)))
  (testing "deep"
    (is (if (:absolute (from-java-deep (java.net.URI. "") {})) false true))
    (is (if (:opaque (from-java-deep (java.net.URI. "") {})) false true)))
  (testing "shallow"
    (is (if (:absolute (from-java-shallow (java.net.URI. "") {})) false true))
    (is (if (:opaque (from-java-shallow (java.net.URI. "") {})) false true))))

(deftest jdata-12
  (let [eek1 (java.sql.SQLException. "SQL 1")
        eek2 (java.sql.SQLException. "SQL 2")
        eek3 (java.sql.SQLException. "SQL 3")]
    (.setNextException eek1 eek2)
    (.setNextException eek2 eek3)
    (testing "regular"
      (let [ex (from-java eek1)]
        (is (= "SQL 1" (get-in ex [:message])))
        (is (= "SQL 2" (get-in ex [:nextException :message])))
        (is (= "SQL 3" (get-in ex [:nextException :nextException :message])))
        (is (nil? (get-in ex [:nextException :nextException :nextException])))))
    (testing "deep"
      (let [ex (from-java-deep eek1 {})]
        (is (= "SQL 1" (get-in ex [:message])))
        (is (= "SQL 2" (get-in ex [:nextException :message])))
        (is (= "SQL 3" (get-in ex [:nextException :nextException :message])))
        (is (nil? (get-in ex [:nextException :nextException :nextException])))))
    (testing "shallow"
      (let [ex (from-java-shallow eek1 {})]
        (is (= "SQL 1" (get-in ex [:message])))
        (is (instance? java.sql.SQLException (get ex :nextException)))))))

;; set-properties tests

(deftest jdata-15
  (testing "flat maps"
    (let [address (set-properties (new Address)
                                  {:line1 "123 Main St"
                                   :city "Dallas"
                                   :state "TX"
                                   :zip "75432"})
          person (set-properties (new Person)
                                 {:name "Bob"
                                  :age 30
                                  :address address})]
      (is (= "Bob" (.getName person)))
      (is (= 30 (.getAge person)))
      (is (= "123 Main St" (.. person getAddress getLine1)))
      (is (= "Dallas" (.. person getAddress getCity)))
      (is (= State/TX (.. person getAddress getState)))
      (is (= "75432" (.. person getAddress getZip)))))
  (testing "nested map"
    (let [person (set-properties (new Person)
                                 {:name "Bob"
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
      (is (= "75432" (.. person getAddress getZip))))))

;; constructor tests

(deftest jdata-16
  (testing "just constructor arguments"
    (let [^Address address (to-java Address
                                    (with-meta {}
                                      {::j/constructor ["123 Main St"
                                                        "Dallas"
                                                        (to-java State "TX")
                                                        "75432"]}))
          person (to-java Person
                          (with-meta {}
                            {::j/constructor ["Bob" (biginteger 30) address]}))]
      (is (= "Bob" (.getName person)))
      (is (= 30 (.getAge person)))
      (is (= "123 Main St" (.. person getAddress getLine1)))
      (is (= "Dallas" (.. person getAddress getCity)))
      (is (= State/TX (.. person getAddress getState)))
      (is (= "75432" (.. person getAddress getZip)))))
  (testing "mixed constructor arguments and properties"
    (let [person (to-java Person
                          (with-meta {:address {:line1 "123 Main St"
                                                :city "Dallas"
                                                :state "TX"
                                                :zip "75432"}}
                            {::j/constructor ["Bob" (biginteger 30) nil]}))]
      (is (= "Bob" (.getName person)))
      (is (= 30 (.getAge person)))
      (is (= "123 Main St" (.. person getAddress getLine1)))
      (is (= "Dallas" (.. person getAddress getCity)))
      (is (= State/TX (.. person getAddress getState)))
      (is (= "75432" (.. person getAddress getZip))))
    (let [person (to-java Person
                          (with-meta {:address {:line1 "123 Main St"
                                                :city "Dallas"
                                                :state "TX"
                                                :zip "75432"}
                                      :age 30}
                            {::j/constructor ["Bob" nil nil]}))]
      (is (= "Bob" (.getName person)))
      (is (= 30 (.getAge person)))
      (is (= "123 Main St" (.. person getAddress getLine1)))
      (is (= "Dallas" (.. person getAddress getCity)))
      (is (= State/TX (.. person getAddress getState)))
      (is (= "75432" (.. person getAddress getZip))))))
