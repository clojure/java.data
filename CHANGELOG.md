## Change Log

* Release 1.0.next in progress
  * Add `from-java-shallow` to provide functionality similar to `clojure.core/bean` (a shallow conversion) but with options to control behavior (so "dangerous" methods that appear as getters can be omitted).
  * Bump `org.clojure/tools.logging` to `1.1.0`.
  * Move change log to a separate file.
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