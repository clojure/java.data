# java.data

Functions for recursively converting Java beans to Clojure and vice
versa. Future home of Java beans and properties support from the old
clojure-contrib

## Releases and Dependency Information

This project follows the version scheme MAJOR.MINOR.COMMITS where MAJOR and MINOR provide some relative indication of the size of the change, but do not follow semantic versioning. In general, all changes endeavor to be non-breaking (by moving to new names rather than by breaking existing names). COMMITS is an ever-increasing counter of commits since the beginning of this repository.

Latest stable release: 1.0.64

* [All Released Versions](http://search.maven.org/#search%7Cga%7C1%7Corg.clojure%20java.data)
* [Development Snapshot Versions](https://repository.sonatype.org/index.html#nexus-search;gav~org.clojure~java.data~~~)

### Leiningen

```clojure
[org.clojure/java.data "1.0.64"]
```

### Maven

```xml
<dependency>
    <groupId>org.clojure</groupId>
    <artifactId>java.data</artifactId>
    <version>1.0.64</version>
</dependency>
```

## Example Usage

```clojure
(require '[clojure.java.data :as j])

;; construct YourJavaClass instance from Clojure data structure
;; (usually a Clojure hash map of properties to set on the instance):
(j/to-java YourJavaClass clojure-property-map)

;; the 0-arity constructor is called to construct the instance
;; and then the properties are added by calling setters

;; note that keys in the property map must follow the :camelCase
;; naming of the Java fields to which they correspond, so that
;; the appropriate setter methods can be invoked, e.g.,
(j/to-java SomeJavaClass {:stuff 42 :moreStuff "13"})
;; this is equivalent to:
(let [obj (SomeJavaClass.)]
  (.setStuff obj 42)
  (.setMoreStuff obj "13"))

;; represent a javaValue instance in a Clojure data structure:
(j/from-java javaValue)

;; populate javaValue instance from a Clojure property hash map
;; (calls a setter for each key/value pair in the hash map):
(j/set-properties javaValue clojure-property-map)

;; provide constructor arguments via metadata:
(j/to-java YourJavaClass
  (with-meta clojure-property-map
    {::j/constructor ["constructor" "arguments"]}))
;; constructor arguments must match the parameter types
;; so you may need type hints and coercions on them
```

Representing an instance of `YourJavaClass` in a Clojure data structure

```clojure
(defmethod j/from-java YourJavaClass [instance]
  ; your custom logic for turning this instance into a clojure data structure
)
```

Constructing an instance of `YourJavaClass` from a Clojure data structure

```clojure
(defmethod j/to-java [YourJavaClass clojure.lang.APersistentMap] [clazz props]
  ; your custom logic for constructing an instance from a property map
)
```

### Usage with Builder Classes

As of 0.2.0, `java.data` adds a new namespace and a new `to-java`
function that supports the Builder Pattern. Instead of just creating an instance
of the specified class and then setting properties on it, this variant works
with an associated "builder" class (or instance), setting properties on it,
and then producing an instance of the specified class from it.

In Java, that typically looks like:

```java
MyClass foo = new MyClass.Builder()
                .fooBar( 42 )
                .quux( "stuff" )
                .build();
```

That becomes:

```clojure
(require '[clojure.java.data.builder :as builder])

(def foo (builder/to-java MyClass {:fooBar 42 :quux "stuff"}))
```

By default, this assumes `MyClass` has a nested class called `Builder`, and the
property methods could be `.fooBar`, `.setFooBar`, or `.withFooBar` (and
`.quux`, `.setQuux`, or `.withQuux`), and then a `.build` method
that produces the `MyClass` object.

You can also specify an options hash map containing any of the following:

* `:builder-class` -- the class that should be used for the builder process; by default it will assume an inner class of `clazz` called `Builder`,
* `:builder-props` -- properties used to construct and initialize an instance of the builder class; defaults to an empty hash map; may have `:clojure.java.data/constructor` as metadata to provide constructor arguments for the builder instance,
* `:build-fn` -- the name of the method in the `Builder` class to use to complete the builder process and return the desired class; by default it will try to deduce it, preferring `build` if we find multiple candidates,
* `:ignore-setters?` -- a flag to indicate that methods on the builder class that begin with `set` should be ignored, which may be necessary to avoid ambiguous methods that look like builder properties; by default `setFooBar` and `withQuuxIt` will be treated as builder properties `fooBar` and `quuxIt` if they accept a single argument and return a builder instance.

Additional arities allow you to specify a builder instance, for cases where the
builder is not simply constructed from a (nested) class, and both a builder class
and a builder instance for more complex cases:

```clojure
;; requires the options hash map, even if it is empty:
(builder/to-java MyClass (MyClass/builder) {:bar 42 :quux "stuff"} {})

;; for cases where the type of the builder instance differs from the actual
;; builder class that should be used for property method return types:
(builder/to-java MyClass MyClassBuilder (MyClass/builder) {:bar 42 :quux "stuff"} {})
```

## Feature comparison to `clojure.core/bean`

Clojure core provides a `bean` function which has some overlap with java.data. Below is a more detailed comparison:

Dimension | `bean` | `java.data`
-- | ------ | -----------
find fields	| bean introspector	| bean introspector -  "class"
depth       | 1	                | recursive without cycle detection
field names	| keyword           | keyword
extensibility | none            | multimethod on class
special casing | none           | arrays, iterable, maps, enums, Instant, SQLException, XMLGregorianCalendar
map keys    | unhandled	        | untouched
exception defense | none        | none

## Developer Information

* [GitHub project](https://github.com/clojure/java.data)

* [Bug Tracker](http://dev.clojure.org/jira/browse/JDATA)

* [Continuous Integration](http://build.clojure.org/job/java.data/)

* [Compatibility Test Matrix](http://build.clojure.org/job/java.data-test-matrix/)

## Change Log

* Release 1.0.next in progress
  * Bump `org.clojure/tools.logging` to `1.0.0`.
  * Improve documentation around property naming and how it corresponds to setter function names.

* Release 1.0.64 on 2020-02-18
  * Switch to 1.0.x versioning.
  * Bump `org.clojure/tools.logging` to `0.6.0`.
  * Add basic tests for the builder [JDATA-20](https://clojure.atlassian.net/browse/JDATA-20).

* Release 0.2.0 on 2020-01-02
  * Add `clojure.java.data.builder/to-java` to construct Java objects from builders using hash maps of properties JDATA-18.

* Release 0.1.5 on 2019-12-20
  * Add `set-properties` to populate an existing object JDATA-15.
  * Add `:clojure.java.data/constructor` metadata support JDATA-16.

* Release 0.1.4 on 2019-10-13
  * Fix Clojure hash map conversion problems JDATA-14 (problems introduced in 0.1.3)

* Release 0.1.3 on 2019-10-13
  * Fix `java.util.Map`/Clojure hash map setter handling JDATA-6.
  * Fix `Boolean` conversion JDATA-10.
  * Fix `SQLException` handling JDATA-12.

* Release 0.1.2 on 2019-10-12
  * Fix reflection warnings JDATA-2 and JDATA-13.

* Release 0.1.1 on 2012-04-29
  * Initial release.
  * Clojure 1.2 compatibility.

## Copyright and License

Copyright (c) Rich Hickey and contributors. All rights reserved.

The use and distribution terms for this software are covered by the
[Eclipse Public License
1.0](http://opensource.org/licenses/eclipse-1.0.php) which can be
found in the file epl.html at the root of this distribution.  By using
this software in any fashion, you are agreeing to be bound by the
terms of this license. You must not remove this notice, or any other,
from this software.
