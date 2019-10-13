# java.data

Functions for recursively converting Java beans to Clojure and vice
versa. Future home of Java beans and properties support from the old
clojure-contrib

## Releases and Dependency Information

Latest stable release: 0.1.4

* [All Released Versions](http://search.maven.org/#search%7Cga%7C1%7Corg.clojure%20java.data)
* [Development Snapshot Versions](https://repository.sonatype.org/index.html#nexus-search;gav~org.clojure~java.data~~~)

### Leiningen

```clojure
[org.clojure/java.data "0.1.4"]
```

### Maven

```xml
<dependency>
    <groupId>org.clojure</groupId>
    <artifactId>java.data</artifactId>
    <version>0.1.4</version>
</dependency>
```

## Example Usage

```clojure
(use 'clojure.java.data)

(to-java YourJavaClass clojure-property-map)
(from-java javaValue)
```

Representing an instance of `YourJavaClass` in a Clojure data structure

```clojure
(defmethod from-java YourJavaClass [instance]
  ; your custom logic for turning this instance into a clojure data structure
)
```

Constructing an instance of `YourJavaClass` from a Clojure data structure

```clojure
(defmethod to-java [YourJavaClass clojure.lang.APersistentMap] [clazz props]
  ; your custom logic for constructing an instance from a property map
)
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
