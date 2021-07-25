(ns com.twt-dm-tools.util)

(defmacro rr [sym]
  "shorthand macro for requiring-resolve"
  `(requiring-resolve '~sym))

(defmacro rr> [sym & args]
  "shorthand macro for requiring-resolve and calling the result as a function"
  `((requiring-resolve '~sym) ~@args))

;; functions to combine predicates/rules

(defn rule-and
  ([] (constantly true))
  ([rule] rule)
  ([rule1 rule2]
   #(and (apply rule1 %&) (apply rule2 %&)))
  ([rule1 rule2 & rules]
   (apply rule-and (rule-and rule1 rule2) rules)))

(defn rule-or
  ([] (constantly false))
  ([rule] rule)
  ([rule1 rule2]
   #(or (apply rule1 %&) (apply rule2 %&)))
  ([rule1 rule2 & rules]
   (apply rule-or (rule-or rule1 rule2) rules)))

(defn rule-not [rule]
  #(not (apply rule %&)))

;; logical IMPLY (single arrow): rule is true unless p is true and q is false. ("We care about q only if p is true.")
(defn rule-if [rule-p rule-q]
  (rule-or (rule-not rule-p) rule-q))

;; logical XNOR (double arrow): returns true only if the p and q have the same result.
(defn rule-iff [rule-p rule-q]
  #_(comment
      ;; this would be the implementation using just the established rule system instead of `=`
      (rule-and (rule-if rule-p rule-q)
                (rule-if rule-q rule-p)))
  #(= (apply rule-p %&)
      (apply rule-q %&)))