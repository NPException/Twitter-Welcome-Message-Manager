(ns user)

(defmacro rr [sym]
  `(requiring-resolve '~sym))

;; development REPL snippets
(comment
  ;; start
  (def server ((rr de.npcomplete.twtwlcm.core/-main)))
  ;; stop
  (server)
  )