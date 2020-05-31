;;  Copyright (c) Cosmin Stejerean, Sean Corfield. All rights reserved.
;;  The use and distribution terms for this software are covered by the
;;  Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;  which can be found in the file epl-v10.html at the root of this
;;  distribution.  By using this software in any fashion, you are agreeing to
;;  be bound by the terms of this license.  You must not remove this notice,
;;  or any other, from this software.

(ns
  ^{:author "Cosmin Stejerean, Sean Corfield",
    :doc "Support for recursively converting Java beans to Clojure and vice versa."}
  clojure.java.data
  (:require [clojure.string :as str]
            [clojure.tools.logging :as logger]))

(set! *warn-on-reflection* true)

(def ^:dynamic
  *to-java-object-missing-setter*
  "Specify the behavior of missing setters in to-java in the
  default object case, using one of :ignore, :log, :error"
  :ignore)

(defmulti to-java
  "Convert Clojure data to an instance of the specified Java class.
  Several basic types have obvious conversions, but for a hash map
  reflection is used to set the properties. If the class is an interface, we
  can't create an instance of it, unless the Clojure map already implements it.

  When java.time.Instant is available (Java 8+), we can convert a hash map
  containing :nano and :epochSecond to Instant, as this is the reverse of
  Instant->map.

  A XMLGregorianCalendar object can be constructed from the following keys
  :year, :month, :day, :hour, :minute, :second, and :timezone."
  (fn [destination-type value] [destination-type (class value)]))
(defmulti from-java
  "Convert a Java object to a Clojure map."
  class)
(defmulti from-java-shallow
  "Convert a Java object to a Clojure map (but do not convert deeply).

  The second argument is a hash map that offers some control over the
  conversion:
  * :add-class -- if true, add :class with the actual class of the object
          being converted -- this mimics clojure.core/bean.
  * :omit -- a set of properties (keywords) to omit from the conversion
          so that unsafe methods are not called."
  (fn [obj _] (class obj)))

(defn- get-property-descriptors [clazz]
  (.getPropertyDescriptors (java.beans.Introspector/getBeanInfo clazz)))

(comment
  (mapv bean (.getPropertyDescriptors (java.beans.Introspector/getBeanInfo java.sql.Statement))))

;; getters

(defn- is-getter [^java.lang.reflect.Method method]
  (and method
       (= 0 (alength ^"[Ljava.lang.Class;" (.getParameterTypes method)))))

(defn- make-getter-fn [^java.lang.reflect.Method method]
  (fn [instance]
    (from-java (.invoke method instance nil))))

(defn- make-shallow-getter-fn [^java.lang.reflect.Method method]
  (fn [instance]
    (.invoke method instance nil)))

(defn- add-getter-fn [the-map ^java.beans.PropertyDescriptor prop-descriptor]
  (let [name (.getName prop-descriptor)
        method (.getReadMethod prop-descriptor)]
    (if (and (is-getter method) (not (= "class" name)))
      (assoc the-map (keyword name) (make-getter-fn method))
      the-map)))

(defn- add-shallow-getter-fn [the-map ^java.beans.PropertyDescriptor prop-descriptor]
  (let [name (.getName prop-descriptor)
        method (.getReadMethod prop-descriptor)]
    (if (and (is-getter method) (not (= "class" name)))
      (assoc the-map (keyword name) (make-shallow-getter-fn method))
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
        from (fn [obj] (map from-java obj))
        from-shallow (fn [obj opts] (map from-java-shallow obj opts))]
    (.addMethod ^clojure.lang.MultiFn to-java [acls Iterable] to)
    (.addMethod ^clojure.lang.MultiFn from-java acls from)
    (.addMethod ^clojure.lang.MultiFn from-java-shallow acls from-shallow)
    {:to to :from from :from-shallow from-shallow}))

;; constructor support

(defn- find-matching-constructors [^Class clazz args]
  (let [n (count args)]
    (filter (fn [^java.lang.reflect.Constructor ctr]
              (and (= n (.getParameterCount ctr))
                   (let [pts (map vector (.getParameterTypes ctr) args)]
                     (every? (fn [[^Class pt arg]]
                               ;; watch out for boxed types in Clojure
                               ;; passed to primitive constructor parameters
                               (cond (.isPrimitive pt)
                                     (and arg ; cannot pass nil to primitive
                                          (or (instance? Number arg)
                                              (instance? Boolean arg)))
                                     (nil? arg)
                                     true ; null is assignable to any non-primitive
                                     :else
                                     (.isAssignableFrom pt (class arg))))
                             pts))))
            (.getConstructors clazz))))

(defn- find-constructor ^java.lang.reflect.Constructor [^Class clazz args]
  (let [candidates (find-matching-constructors clazz args)]
    (condp = (count candidates)
           0 (throw (IllegalArgumentException.
                     (str (.getName clazz) " has no matching constructor"
                          " for the given argument list")))
           1 (first candidates)
           (throw (IllegalArgumentException.
                   (str (.getName clazz) " constructor is ambiguous"
                        " for the given argument list"))))))

(comment
  (find-constructor String ["arg"])
  (find-constructor String [(.getBytes "arg")])
  (find-constructor String [(.getBytes "arg") (int 0) (int 3)])
  (find-constructor String ["too" "many" "arguments"]))

;; common to-java definitions

(defmethod to-java :default [^Class cls value]
  (if (.isArray cls)
    ;; no method for this array type yet
    ((:to (add-array-methods cls)) cls value)
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
    (when-not (and (:nano props) (:epochSecond props))
      (throw (IllegalArgumentException. "java.time.Instant requires :nano and :epochSecond")))
    (java.time.Instant/ofEpochSecond (:epochSecond props) (:nano props))))

(defn- set-properties-on
  "Given an instance, its class, and a hash map of properties,
  call the appropriate setters and return the populated object.

  Used by to-java and set-properties below."
  [instance ^Class clazz props]
  (let [setter-map (reduce add-setter-fn {} (get-property-descriptors clazz))]
    (doseq [[key value] props]
      (let [setter (get setter-map (keyword key))]
        (if (nil? setter)
          (throw-log-or-ignore-missing-setter key clazz)
          (apply setter [instance value]))))
    instance))

(defmethod to-java [Object clojure.lang.APersistentMap] [^Class clazz props]
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
    (let [ctr-args (::constructor (meta props))
          ctr      (when ctr-args (find-constructor clazz ctr-args))
          instance (try
                     (if ctr
                       (.newInstance ctr (object-array ctr-args))
                       (.newInstance clazz))
                     (catch Throwable t
                       (throw (IllegalArgumentException.
                               (str (.getName clazz)
                                    " cannot be constructed")
                               t))))]
      (set-properties-on instance clazz props))))

(when-available
 biginteger
 (defmethod to-java [java.math.BigInteger Object] [_ value] (biginteger value)))

(when-not-available
 biginteger
 (defmethod to-java [java.math.BigInteger Object] [_ value] (bigint value)))

;; set properties on existing objects

(defn set-properties
  "Given an existing Java object and a Clojure map, use reflection to
  set the properties."
  [instance props]
  (set-properties-on instance (class instance) props))

;; common from-java definitions

(defmethod from-java :default [^Object instance]
  (let [clazz (.getClass instance)]
    (if (.isArray clazz)
      ((:from (add-array-methods clazz)) instance)
      (let [getter-map (reduce add-getter-fn {} (get-property-descriptors clazz))]
        (into {} (for [[key getter-fn] (seq getter-map)] [key (getter-fn instance)]))))))

(defmethod from-java-shallow :default [^Object instance opts]
  (let [clazz (.getClass instance)]
    (if (.isArray clazz)
      ((:from-shallow (add-array-methods clazz)) instance opts)
      (let [getter-map (reduce add-shallow-getter-fn {} (get-property-descriptors clazz))]
        (into (if (:add-class opts) {:class (class instance)} {}) 
              (for [[key getter-fn] (seq getter-map)
                    :when (not (contains? (:omit opts) key))]
                [key (getter-fn instance)]))))))

(doseq [clazz [String Character Byte Short Integer Long Float Double
               java.math.BigInteger java.math.BigDecimal]]
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

(defmethod from-java-shallow ::do-not-convert [value _] value)
(prefer-method from-java-shallow ::do-not-convert Object)

(defmethod from-java Iterable [instance]
  (for [each (seq instance)] (from-java each)))
(prefer-method from-java Iterable Object)

(defmethod from-java-shallow Iterable [instance opts]
  (for [each (seq instance)] (from-java-shallow each opts)))
(prefer-method from-java-shallow Iterable Object)

(defmethod from-java java.util.Map [instance] (into {} instance))
(prefer-method from-java java.util.Map Iterable)

(defmethod from-java-shallow java.util.Map [instance _] (into {} instance))
(prefer-method from-java-shallow java.util.Map Iterable)

(defmethod from-java nil [_] nil)
(defmethod from-java java.sql.SQLException [^Object ex]
  ((get-method from-java :default) ex))
(defmethod from-java Boolean [value] (boolean value))
(defmethod from-java Enum [enum] (str enum))

(defmethod from-java-shallow nil [_ _] nil)
(defmethod from-java-shallow java.sql.SQLException [^Object ex opts]
  ((get-method from-java-shallow :default) ex opts))
(defmethod from-java-shallow Boolean [value _] (boolean value))
(defmethod from-java-shallow Enum [enum _] (str enum))

;; definitions for interfacting with XMLGregorianCalendar

(defmethod to-java [javax.xml.datatype.XMLGregorianCalendar clojure.lang.APersistentMap] [^Class clazz props]
  (let [^javax.xml.datatype.XMLGregorianCalendar instance (.newInstance clazz)
        undefined javax.xml.datatype.DatatypeConstants/FIELD_UNDEFINED
        getu #(get %1 %2 undefined)
        y (getu props :year)]
    ;; .setYear is unique in having an overload on int and BigInteger
    ;; whereas the other setters only have an int version so avoiding
    ;; reflection means special treatment for .setYear
    (if (instance? java.math.BigInteger y)
      (.setYear instance ^java.math.BigInteger y)
      (.setYear instance ^int y))
    (doto instance
      (.setMonth (getu props :month))
      (.setDay (getu props :day))
      (.setHour (getu props :hour))
      (.setMinute (getu props :minute))
      (.setSecond (getu props :second))
      (.setTimezone (getu props :timezone)))))

(defn- from-xml-gregorian-calendar
  "Turn an XMLGregorianCalendar object into a clojure map of year, month, day, hour, minute, second and timezone "
  [^javax.xml.datatype.XMLGregorianCalendar obj]
  (let [date {:year (.getYear obj)
              :month (.getMonth obj)
              :day (.getDay obj)}
        time {:hour (.getHour obj)
              :minute (.getMinute obj)
              :second (.getSecond obj)}
        tz {:timezone (.getTimezone obj)}
        is-undefined? #(= javax.xml.datatype.DatatypeConstants/FIELD_UNDEFINED %1)]
    (conj {}
          (when-not (is-undefined? (:year date))
            date)
          (when-not (is-undefined? (:hour time))
            time)
          (when-not (is-undefined? (:timezone tz))
            tz))))

(defmethod from-java javax.xml.datatype.XMLGregorianCalendar
  [obj] (from-xml-gregorian-calendar obj))
(defmethod from-java-shallow javax.xml.datatype.XMLGregorianCalendar
  [obj {:keys [add-class]}]
  (cond-> (from-xml-gregorian-calendar obj)
    add-class (assoc :class (class obj))))
