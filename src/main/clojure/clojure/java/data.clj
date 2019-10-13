;;  Copyright (c) Cosmin Stejerean. All rights reserved.  The use and
;;  distribution terms for this software are covered by the Eclipse Public
;;  License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can
;;  be found in the file epl-v10.html at the root of this distribution.  By
;;  using this software in any fashion, you are agreeing to be bound by the
;;  terms of this license.  You must not remove this notice, or any other,
;;  from this software.

(ns
  ^{:author "Cosmin Stejerean",
    :doc "Support for recursively converting Java beans to Clojure and vice versa."}
  clojure.java.data
  (:require [clojure.string :as str]
            [clojure.tools.logging :as logger]))

(set! *warn-on-reflection* true)

(def
 ^{:dynamic true,
   :doc "Specify the behavior of missing setters in to-java in the
 default object case, using one of :ignore, :log, :error"}
 *to-java-object-missing-setter* :ignore)

(defmulti to-java (fn [destination-type value] [destination-type (class value)]))
(defmulti from-java class)

(defn- get-property-descriptors [clazz]
  (.getPropertyDescriptors (java.beans.Introspector/getBeanInfo clazz)))

;; getters

(defn- is-getter [^java.lang.reflect.Method method]
  (and method
       (= 0 (alength ^"[Ljava.lang.Class;" (.getParameterTypes method)))))

(defn- make-getter-fn [^java.lang.reflect.Method method]
  (fn [instance]
    (from-java (.invoke method instance nil))))

(defn- add-getter-fn [the-map ^java.beans.PropertyDescriptor prop-descriptor]
  (let [name (.getName prop-descriptor)
        method (.getReadMethod prop-descriptor)]
    (if (and (is-getter method) (not (= "class" name)))
      (assoc the-map (keyword name) (make-getter-fn method))
      the-map)))


;; setters

(defn- is-setter [^java.lang.reflect.Method method]
  (and method (= 1 (alength (. method (getParameterTypes))))))

(defn- get-setter-type [^java.lang.reflect.Method method]
  (get (.getParameterTypes method) 0))

(defn- make-setter-fn [^java.lang.reflect.Method method]
    (fn [instance value]
      (.invoke method instance (into-array [(to-java (get-setter-type method) value)]))))

(defn- add-setter-fn [the-map ^java.beans.PropertyDescriptor prop-descriptor]
  (let [name (.getName prop-descriptor)
        method (.getWriteMethod prop-descriptor)]
    (if (is-setter method)
      (assoc the-map (keyword name) (make-setter-fn method))
      the-map)))

(defn- add-array-methods [^Class acls]
  (let [cls (.getComponentType acls)
        to (fn [_ sequence] (into-array cls (map (partial to-java cls)
                                                sequence)))
        from (fn [obj] (map from-java obj))]
    (.addMethod ^clojure.lang.MultiFn to-java [acls Iterable] to)
    (.addMethod ^clojure.lang.MultiFn from-java acls from)
    {:to to :from from}))

;; common to-java definitions

(defmethod to-java :default [^Class cls value]
  (if (.isArray cls)
                                        ; no method for this array type yet
    ((:to (add-array-methods cls))
     cls value)
    value))

(defmethod to-java [Enum String] [^Class enum value]
  (.invoke (.getDeclaredMethod enum "valueOf" (into-array [String]))
           nil (into-array [value])))


(defn- throw-log-or-ignore-missing-setter [key ^Class clazz]
  (let [message (str "Missing setter for " key " in " (.getCanonicalName clazz))]
    (cond (= *to-java-object-missing-setter* :error)
          (throw (new NoSuchFieldException message))
          (= *to-java-object-missing-setter* :log)
          (logger/info message))))

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

;; Clojure hash map conversions

(when-available
  java.time.Instant
  (defmethod to-java [java.time.Instant clojure.lang.APersistentMap] [_ props]
    "Instant->map produces :nano, :epochSecond so do the reverse"
    (when-not (and (:nano props) (:epochSecond props))
      (throw (IllegalArgumentException. "java.time.Instant requires :nano and :epochSecond")))
    (java.time.Instant/ofEpochSecond (:epochSecond props) (:nano props))))

(defmethod to-java [Object clojure.lang.APersistentMap] [^Class clazz props]
  "Convert a Clojure map to the specified class using reflection to set the
  properties. If the class is an interface, we can't create an instance of
  it, unless the Clojure map already implements it."
  (if (.isInterface clazz)
    (if (instance? clazz props)
      (condp = clazz
        ;; make a fresh (mutabl) hash map from the Clojure map
        java.util.Map (java.util.HashMap. ^java.util.Map props)
        ;; Iterable, Serializable, Runnable, Callable
        ;; we should probably figure out actual objects to create...
        props)
      (throw (IllegalArgumentException.
               (str (.getName clazz) " is an interface "
                    "and cannot be constructed from "
                    (str/join ", " (map name (keys props)))))))
    (let [instance (try (.newInstance clazz)
                     (catch Throwable t
                       (throw (IllegalArgumentException.
                                (str (.getName clazz)
                                     " cannot be constructed")
                                t))))
          setter-map (reduce add-setter-fn {} (get-property-descriptors clazz))]
      (doseq [[key value] props]
        (let [setter (get setter-map (keyword key))]
          (if (nil? setter)
            (throw-log-or-ignore-missing-setter key clazz)
            (apply setter [instance value]))))
      instance)))

(when-available
 biginteger
 (defmethod to-java [BigInteger Object] [_ value] (biginteger value)))

(when-not-available
 biginteger
 (defmethod to-java [BigInteger Object] [_ value] (bigint value)))

;; common from-java definitions

(defmethod from-java :default [^Object instance]
  "Convert a Java object to a Clojure map"
  (let [clazz (.getClass instance)]
    (if (.isArray clazz)
      ((:from (add-array-methods clazz))
       instance)
      (let [getter-map (reduce add-getter-fn {} (get-property-descriptors clazz))]
        (into {} (for [[key getter-fn] (seq getter-map)] [key (getter-fn instance)]))))))


(doseq [clazz [String Character Byte Short Integer Long Float Double BigInteger BigDecimal]]
  (derive clazz ::do-not-convert))

(defmacro ^{:private true} defnumber [box prim prim-getter]
  `(let [conv# (fn [_# ^Number number#]
                 (~(symbol (str box) "valueOf")
                  (. number# ~prim-getter)))]
     (.addMethod ^clojure.lang.MultiFn to-java [~prim Number] conv#)
     (.addMethod ^clojure.lang.MultiFn to-java [~box Number] conv#)))

(defmacro ^{:private true} defnumbers [& boxes]
  (cons `do
        (for [box boxes
              :let [^Class box-cls (resolve box)
                    ^Class prim-cls (.get (.getField box-cls "TYPE")
                                          box-cls)
                    ;; Clojure 1.3: (assert (class? box-cls) (str box ": no class found"))
                    _ (assert (class? box-cls))
                    ;; Clojure 1.3: (assert (class? prim-cls) (str box " has no TYPE field"))
                    _ (assert (class? prim-cls))
                    prim-getter (symbol (str (.getName prim-cls) "Value"))]]
          `(defnumber ~box ~(symbol (str box) "TYPE") ~prim-getter))))

(defnumbers Byte Short Integer Long Float Double)

(defmethod from-java ::do-not-convert [value] value)
(prefer-method from-java ::do-not-convert Object)

(defmethod from-java Iterable [instance] (for [each (seq instance)] (from-java each)))
(prefer-method from-java Iterable Object)

(defmethod from-java java.util.Map [instance] (into {} instance))
(prefer-method from-java java.util.Map Iterable)

(defmethod from-java nil [_] nil)
(defmethod from-java java.sql.SQLException [^Object ex]
  ((get-method from-java :default) ex))
(defmethod from-java Boolean [value] (boolean value))
(defmethod from-java Enum [enum] (str enum))

;; definitions for interfacting with XMLGregorianCalendar

(defmethod to-java [javax.xml.datatype.XMLGregorianCalendar clojure.lang.APersistentMap] [^Class clazz props]
  "Create an XMLGregorianCalendar object given the following keys :year :month :day :hour :minute :second :timezone"
  (let [^javax.xml.datatype.XMLGregorianCalendar instance (.newInstance clazz)
        undefined javax.xml.datatype.DatatypeConstants/FIELD_UNDEFINED
        getu #(get %1 %2 undefined)
        y (getu props :year)]
    ;; .setYear is unique in having an overload on int and BigInteger
    ;; whereas the other setters only have an int version so avoiding
    ;; reflection means special treatment for .setYear
    (if (instance? BigInteger y)
      (.setYear instance ^BigInteger y)
      (.setYear instance ^int y))
    (doto instance
      (.setMonth (getu props :month))
      (.setDay (getu props :day))
      (.setHour (getu props :hour))
      (.setMinute (getu props :minute))
      (.setSecond (getu props :second))
      (.setTimezone (getu props :timezone)))))

(defmethod from-java javax.xml.datatype.XMLGregorianCalendar
  [^javax.xml.datatype.XMLGregorianCalendar obj]
  "Turn an XMLGregorianCalendar object into a clojure map of year, month, day, hour, minute, second and timezone "
  (let [date {:year (.getYear obj)
              :month (.getMonth obj)
              :day (.getDay obj)}
        time {:hour (.getHour obj)
              :minute (.getMinute obj)
              :second (.getSecond obj)}
        tz {:timezone (.getTimezone obj)}
        is-undefined? #(= javax.xml.datatype.DatatypeConstants/FIELD_UNDEFINED %1)]
    (conj {}
          (if-not (is-undefined? (:year date))
            date)
          (if-not (is-undefined? (:hour time))
            time)
          (if-not (is-undefined? (:timezone tz))
            tz))))
