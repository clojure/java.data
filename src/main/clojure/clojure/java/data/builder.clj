;;  Copyright (c) Sean Corfield. All rights reserved.
;;  The use and distribution terms for this software are covered by the
;;  Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;  which can be found in the file epl-v10.html at the root of this
;;  distribution.  By using this software in any fashion, you are agreeing to
;;  be bound by the terms of this license.  You must not remove this notice,
;;  or any other, from this software.

(ns
  ^{:author "Sean Corfield",
    :doc "A variant of clojure.java.data/to-java that uses a Builder class
  to build the requested class from a hash map of properties."}
  clojure.java.data.builder
  (:require [clojure.java.data :as j]))

(set! *warn-on-reflection* true)

(defn- get-builder-class [^Class clazz]
  (try
    (resolve (symbol (str (.getName clazz) "$Builder")))
    (catch Throwable _)))

(defn- get-builder ^java.lang.reflect.Method [^Class clazz methods opts]
  (let [build-name (:build-fn opts)
        candidates
        (filter (fn [^java.lang.reflect.Method m]
                  (and (= 0 (alength ^"[Ljava.lang.Class;" (.getParameterTypes m)))
                       (= clazz (.getReturnType m))
                       (or (nil? build-name)
                           (= build-name (.getName m)))))
                methods)]
    (case (count candidates)
      0 (throw (IllegalArgumentException.
                (str "Cannot find builder method that returns "
                     (.getName clazz))))
      1 (first candidates)
      (let [builds (filter (fn [^java.lang.reflect.Method m]
                             (= "build" (.getName m)))
                           candidates)]
        (case (count builds)
          0 (throw (IllegalArgumentException.
                    (str "Cannot find 'build' method that returns "
                         (.getName clazz))))
          (first builds))))))

(defn- find-setters [^Class builder methods opts]
  (let [candidates
        (filter (fn [^java.lang.reflect.Method m]
                  (and (= 1 (alength ^"[Ljava.lang.Class;" (.getParameterTypes m)))
                       (= builder (.getReturnType m))
                       (or (not (re-find #"^set[A-Z]" (.getName m)))
                           (not (:ignore-setters? opts)))))
                methods)]
    (reduce (fn [setter-map ^java.lang.reflect.Method m]
              (let [prop (keyword
                          (cond (re-find #"^set[A-Z]" (.getName m))
                                (let [^String n (subs (.getName m) 3)]
                                  (str (Character/toLowerCase (.charAt n 0)) (subs n 1)))
                                (re-find #"^with[A-Z]" (.getName m))
                                (let [^String n (subs (.getName m) 4)]
                                  (str (Character/toLowerCase (.charAt n 0)) (subs n 1)))
                                :else
                                (.getName m)))]
                (if (contains? setter-map prop)
                  (throw (IllegalArgumentException.
                          (str "Duplicate setter found for " prop
                               " in " (.getName builder) " class")))
                  (assoc setter-map prop (#'j/make-setter-fn m)))))
            {}
            candidates)))

(defn- build-on [instance setters ^Class clazz props]
  (reduce-kv (fn [builder k v]
               (if-let [setter (get setters (keyword k))]
                 (apply setter [instance v])
                 (#'j/throw-log-or-ignore-missing-setter k clazz)))
             instance
             props))

(comment
  ;; given a class, see if it has a nested Builder class
  ;; otherwise we'll need to be told the builder class
  ;; and possibly how to create it
  (get-builder-class java.util.Locale)

  ;; from the builder class, look for arity-0 methods then return
  ;; the original class -- if there's only one, use it, if there
  ;; are multiple and one is called "build", use it, else error
  (get-builder java.util.Locale (.getMethods java.util.Locale$Builder) {})

  ;; setters on a builder will have single arguments and will
  ;; return the builder class, and will either be:
  ;; * B propertyName( T )
  ;; * B setPropertyName( T )
  ;; treat both as setters; thrown exception if they clash
  ;; (maybe an option to ignore setXyz( T ) methods?)
  (find-setters java.util.Locale$Builder (.getMethods java.util.Locale$Builder) {})

  ;; general pattern will be to:
  ;; * get the builder class somehow
  ;; * get its public methods
  ;; * identify its builder method (or be told it)
  ;; * identity its setters by name
  ;; * construct the builder (or be given an instance?)
  ;; * reduce over the input hash map,
  ;; * -- if setter matches key,
  ;; * -- then invoke, use result (use j/to-java to build value here?)
  ;; * -- else either log, ignore, or throw (per j/*to-java-object-missing-setter*)
  ;; * invoke builder on result, return that
  (let [clazz   java.util.Locale
        props   {:language "fr"}
        opts    {}
        ^Class builder (get-builder-class clazz)]
    (.invoke (get-builder clazz (.getMethods builder) opts)
             (build-on (j/to-java builder ^clojure.lang.APersistentMap {})
                       (find-setters builder (.getMethods builder) opts)
                       builder
                       props)
             nil)))

(defn to-java
  "Given a class and a hash map of properties, figure out the Builder class,
  figure out the setters for the Builder, construct an instance of it and
  produce an instance of the original class.

  The following options may be provided:
  * :builder-class -- the class that should be used for the builder process;
      by default we'll assume an inner class of clazz called 'Builder',
  * :builder-props -- properties used to construct and initialize an instance
      of the builder class; defaults to an empty hash map; may have
      :clojure.java.data/constructor as metadata to provide constructor
      arguments for the builder instance,
  * :build-fn -- the name of the method in the Builder class to use to
      complete the builder process and return the desired class;
      by default we'll try to deduce it, preferring 'build' if we find
      multiple candidates,
  * :ignore-setters? -- a flag to indicate that methods on the builder
      class that begin with 'set' should be ignored, which may be
      necessary to avoid ambiguous methods that look like builder properties;
      by default 'setFooBar` will be treated as a builder property 'fooBar'
      if it accepts a single argument and returns a builder instance."
  ([clazz props] (to-java clazz props {}))
  ([^Class clazz props opts]
   (if-let [^Class builder (or (:builder-class opts)
                               (get-builder-class clazz))]
     (.invoke (get-builder clazz (.getMethods builder) opts)
              (build-on (j/to-java builder (get opts :builder-props {}))
                        (find-setters builder (.getMethods builder) opts)
                        builder
                        props)
              nil))))

(comment
  (to-java java.util.Locale {:language "fr" :region "EG"}
           ;; these options are all defaults
           {:builder-class java.util.Locale$Builder
            :builder-props {}
            :build-fn "build"}))
