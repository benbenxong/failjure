(ns failjure.core
  (:require [clojure.algo.monads :refer [domonad defmonad]]))

; Public API
; failed?, message part of prototype
(declare fail)
(declare attempt-all)
(declare if-failed)
(declare attempt->)
(declare attempt->>)


; Define a failure
(defrecord Failure [message])
(defn fail
 ([msg] (->Failure msg))
 ([msg & fmt-parts]
  (->Failure (apply format msg fmt-parts))))


; Exceptions are failures too, make them easier
(defmacro try* [& body]
  `(try
     ~@body
     (catch Exception e# e#)))


(defprotocol HasFailed
  (failed? [self])
  (message [self]))


(extend-protocol HasFailed
  Object
  (failed? [self] false)
  (message [self] (str self))

  Failure
  (failed? [self] true)
  (message [self] (:message self))

  Exception
  (failed? [self] true)
  (message [self] (.getMessage self)))


(defmonad error-m
  [m-result identity
   m-bind   (fn [m f] (if (failed? m)
                       m
                       (f m)))])


(defmacro if-failed [arglist & body]
  `(with-meta (fn ~arglist ~@body)
              {:else-fn? true}))


(defn else* [else-part result]
  (if (:else-fn? (meta else-part))
    (else-part result)
    else-part))


(defmacro attempt-all
  ([bindings return] `(domonad error-m ~bindings ~return))
  ([bindings return else]
   `(let [result# (attempt-all ~bindings ~return)]
      (if (failed? result#)
        (else* ~else result#)
        result#))))


(defmacro attempt->
  ([start] start)
  ([start form] `(domonad error-m [x# (-> ~start ~form)] x#))
  ([start form & forms]
   `(let [new-start# (attempt-> ~start ~form)]
      (if (failed? new-start#)
        new-start#
        (attempt-> new-start# ~@forms)))))


(defmacro attempt->>
  ([start] start)
  ([start form] `(domonad error-m [x# (->> ~start ~form)] x#))
  ([start form & forms]
   `(let [new-start# (attempt->> ~start ~form)]
      (if (failed? new-start#)
        new-start#
        (attempt->> new-start# ~@forms)))))
