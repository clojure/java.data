# java.data

Future home of Java beans and properties support from contrib. Currently contains functions for recursively converting Java beans to Clojure and vice versa.

## Example

(use 'clojure.java.data)

(to-java YourJavaClass clojure-property-map)
(from-java javaValue)

## Extending

;; Representing an instance of YourJavaClass in a Clojure data structure
(defmethod from-java YourJavaClass [instance]
  ; your custom logic for turing this instance into a clojure data structure
)

;; Constructing an instance of YourJavaClass from a Clojure data structure
(defmethod to-java [YourJavaClass clojure.lang.APersistentMap] [clazz props]
  ; your custom logic for constructing an instance from a property map
)

## License

Copyright (c) Rich Hickey and contributors. All rights reserved.

The use and distribution terms for this software are covered by the
Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
which can be found in the file epl.html at the root of this distribution.
By using this software in any fashion, you are agreeing to be bound by
the terms of this license.
You must not remove this notice, or any other, from this software.


