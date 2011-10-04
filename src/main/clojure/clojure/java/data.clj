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
  (:use [clojure.tools.logging :only (info)]))
        

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

(defn- is-getter [method]
  (and method (= 0 (alength (. method (getParameterTypes))))))

(defn- make-getter-fn [method]
  (fn [instance]
    (from-java (.invoke method instance nil))))

(defn- add-getter-fn [the-map prop-descriptor]
  (let [name (.getName prop-descriptor)
        method (.getReadMethod prop-descriptor)]
    (if (and (is-getter method) (not (= "class" name)))
      (assoc the-map (keyword name) (make-getter-fn method))
      the-map)))


;; setters

(defn- is-setter [method]
  (and method (= 1 (alength (. method (getParameterTypes))))))

(defn- get-setter-type [method]
  (get (.getParameterTypes method) 0))

(defn- make-setter-fn [method]
    (fn [instance value]
      (.invoke method instance (into-array [(to-java (get-setter-type method) value)]))))

(defn- add-setter-fn [the-map prop-descriptor]
  (let [name (.getName prop-descriptor)
        method (.getWriteMethod prop-descriptor)]
    (if (is-setter method)
      (assoc the-map (keyword name) (make-setter-fn method))
      the-map)))

(defn- add-array-methods [acls]
  (let [cls (.getComponentType acls)
        to (fn [_ sequence] (into-array cls (map (partial to-java cls)
                                                sequence)))
        from (fn [obj] (map from-java obj))]
    (.addMethod to-java [acls Iterable] to)
    (.addMethod from-java acls from)
    {:to to :from from}))

;; common to-java definitions

(defmethod to-java :default [^Class cls value]
  (if (.isArray cls)
                                        ; no method for this array type yet
    ((:to (add-array-methods cls))
     cls value)
    value))

(defmethod to-java [Enum String] [enum value]
           (.invoke (.getDeclaredMethod enum "valueOf" (into-array [String])) nil (into-array [value])))


(defn- throw-log-or-ignore-missing-setter [key clazz]
  (let [message (str "Missing setter for " key " in " (.getCanonicalName clazz))]
    (cond (= *to-java-object-missing-setter* :error)
          (throw (new NoSuchFieldException message))
          (= *to-java-object-missing-setter* :log)
          (info message))))

(defmethod to-java [Object clojure.lang.APersistentMap] [clazz props]
  "Convert a Clojure map to the specified class using reflection to set the properties"
  (let [instance (.newInstance clazz)
        setter-map (reduce add-setter-fn {} (get-property-descriptors clazz))]
    (doseq [[key value] props]
      (let [setter (get setter-map (keyword key))]
        (if (nil? setter)
          (throw-log-or-ignore-missing-setter key clazz)
          (apply setter [instance value]))))
    instance))

(defmethod to-java [BigInteger Object] [_ value] (biginteger value))

;; common from-java definitions

(defmethod from-java :default [^Object instance]
  "Convert a Java object to a Clojure map"
  (let [clazz (.getClass instance)]
    (if (.isArray clazz)
      ((:from (add-array-methods clazz))
       instance)
      (let [getter-map (reduce add-getter-fn {} (get-property-descriptors clazz))]
        (into {} (for [[key getter-fn] (seq getter-map)] [key (getter-fn instance)]))))))


(doseq [clazz [String Character Byte Short Integer Long Float Double Boolean BigInteger BigDecimal]]
  (derive clazz ::do-not-convert))

(defmacro ^{:private true} defnumber [box prim prim-getter]
  `(let [conv# (fn [_# number#]
                 (~(symbol (str box) "valueOf")
                  (. number# ~prim-getter)))]
     (.addMethod to-java [~prim Number] conv#)
     (.addMethod to-java [~box Number] conv#)))

(defmacro ^{:private true} defnumbers [& boxes]
  (cons `do
        (for [box boxes
              :let [box-cls (resolve box)
                    prim-cls (.get (.getField box-cls "TYPE")
                                   box-cls)
                    _ (assert (class? box-cls) (str box ": no class found"))
                    _ (assert (class? prim-cls) (str box " has no TYPE field"))
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
(defmethod from-java Enum [enum] (str enum))

;; definitions for interfacting with XMLGregorianCalendar

(defmethod to-java [javax.xml.datatype.XMLGregorianCalendar clojure.lang.APersistentMap] [clazz props]
  "Create an XMLGregorianCalendar object given the following keys :year :month :day :hour :minute :second :timezone"
  (let [instance (.newInstance clazz)
        undefined javax.xml.datatype.DatatypeConstants/FIELD_UNDEFINED
        getu #(get %1 %2 undefined)]
    (doto instance
      (.setYear (getu props :year))
      (.setMonth (getu props :month))
      (.setDay (getu props :day))
      (.setHour (getu props :hour))
      (.setMinute (getu props :minute))
      (.setSecond (getu props :second))
      (.setTimezone (getu props :timezone)))))

(defmethod from-java javax.xml.datatype.XMLGregorianCalendar [obj]
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
