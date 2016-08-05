

(ns jackdaw.buffy-test
  (:refer-clojure :exclude [read])
  (:require [clojure.test :refer :all]
            [clojurewerkz.buffy.util :refer :all]
            [clojurewerkz.buffy.core :refer :all]
            [clojurewerkz.buffy.frames :refer :all]
            [clojurewerkz.buffy.types.protocols :refer :all]))

(deftest complete-access-test
  (let [s (spec :first-field  (int32-type)
                :second-field (string-type 10)
                :third-field  (boolean-type))
        b (compose-buffer s)]

    (compose b {:first-field 101
                :second-field "string"
                :third-field true})
    (is (= {:third-field true :second-field "string" :first-field 101}
           (decompose b)))))

(def dynamic-string
 (frame-type
  (frame-encoder [value]
                 length (short-type) (count value)
                 string (string-type (count value))
                 value)
  (frame-decoder [buffer offset]
                 length (short-type)
                 string (string-type (read length buffer offset)))
  second))

(def key-value-pair
  (composite-frame
   dynamic-string
   dynamic-string))

(def dynamic-map
 (frame-type
  (frame-encoder [value]
                 length (short-type) (count value)
                 map    (repeated-frame key-value-pair (count value)) value)
  (frame-decoder [buffer offset]
                 length (short-type)
                 map    (repeated-frame key-value-pair (read length buffer offset)))
  second))

(deftest composinggs
  (let [dynamic-type (dynamic-buffer dynamic-map)]
  (compose dynamic-type [[["key1" "value1"] ["key1" "value1"] ["key1" "value1"]]]) ;; Returns a constructred buffer
    (let [ma-list [[["key1" "value1"] ["key1" "value1"] ["key1" "value1"]]]]
      (is (= ma-list
        (-> dynamic-type
          (compose ma-list)
          decompose)
      )))))
