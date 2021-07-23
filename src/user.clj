(ns user)

(defmacro rr [sym]
  "shorthand macro for requiring-resolve"
  `(requiring-resolve '~sym))

(defmacro rr> [sym & args]
  "shorthand macro for requiring-resolve and calling the result as a function"
  `((requiring-resolve '~sym) ~@args))

;; development REPL snippets
(comment
  ;; start
  (def server (rr> de.npcomplete.twtwlcm.core/-main))
  ;; stop
  (server)
  )