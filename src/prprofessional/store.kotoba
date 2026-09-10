(ns prprofessional.store
  "SSoT for the ISCO-08 2432 community public relations professionals
  actor (itonami actor pattern, ADR-2607011000 / CLAUDE.md Actors
  section). Modeled on cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client  — a registered organization (:client-id, :name)
    release — a registered press release {:release-id :client-id
              :name :embargo-lift-day int
              :approved-spokespersons #{name-str}}.
              `:embargo-lift-day` is the registered earliest day
              (simple monotonic day clock, day 0 = epoch for this
              release) the release may be published; an embargo is a
              registered day, not a suggestion.
              `:approved-spokespersons` is the registered set a
              release's quoted speakers must be members of (no
              unauthorized attribution).
    record  — a committed operating record (approved release) —
              written ONLY via commit-record!.
    ledger  — append-only audit trail, commit or hold. The ledger is
              CHAINED: `append-ledger!` routes every entry through
              `prprofessional.ledger/append`, which stamps `:ledger/seq`,
              `:ledger/prev` and `:ledger/hash`. That is what makes
              'append-only' a property of the artifact rather than of the
              code path that produced it — see `prprofessional.ledger`."
  ;; alias is `led`, not `ledger`: this namespace also defines a protocol
  ;; method named `ledger`, and shadowing the two names in one file is
  ;; how a chained append quietly becomes a plain conj again.
  (:require [prprofessional.ledger :as led]))

(defprotocol Store
  (client [s client-id])
  (release [s release-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-release! [s r])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (release [_ release-id] (get-in @a [:releases release-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-release! [s r]
    (swap! a assoc-in [:releases (:release-id r)] r) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger led/append fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :releases {} :records [] :ledger []}
                                   seed)))))
