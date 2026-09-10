(ns prprofessional.facts
  "Well-formedness of the values the ISCO-08 2432 public relations actor
  governs: the client record, the request, and the proposal envelope.

  Runtime: portable `.cljc` (pure predicates, no host interop). Deliberately
  no `clojure.string` dependency — `blank?` is spelled out below so this
  namespace adds no coordinate to `deps.edn`.

  Why this namespace exists — three measurements on the pre-change tree.

  1. Client provenance was written as `(nil? client-record)`. That asks
     whether the store returned something, not whether that something
     identifies a client. Registering the empty map put a record under the key
     `nil`, after which a request carrying no `:client-id` resolved to it:

         (register-client! s {})
         (governor/check {} {} <clean approve of a registered release> s)
         => {:ok? true :violations []}

     `nil?` is a fact about the store's return value. Provenance is a fact
     about the record. Those are different questions, and the second one needs
     a place to live.

  2. The embargo floor was guarded by `(integer? as-of-day)`, so a proposal
     that simply omitted the day skipped the comparison entirely:

         <approve, release embargoed to day 10, :as-of-day nil>
         => {:ok? true :violations []}

     A HARD invariant that is bypassed by leaving out a field is not a floor.
     For a release-bound operation the day is required, and its absence is the
     violation rather than the reason not to check.

  3. `:confidence` is compared against `confidence-floor` to decide
     escalation, but nothing constrained it, so `:confidence 99.0` passed as
     high confidence. A missing confidence already reads as 0.0 in the
     governor and thus escalates, which is correct and left alone; a *present
     but unusable* one is the defect.

  Every function here returns a vector of `{:rule .. :detail ..}` maps —
  empty means well-formed — so violations compose with the Governor's own
  rules without a second shape."
  (:require [prprofessional.operation :as op]))

(defn- blank?
  "True for nil, non-strings, and strings that are empty or all whitespace.
  Identifiers that are not strings are as unusable as absent ones."
  [v]
  (or (nil? v)
      (not (string? v))
      (every? #(contains? #{\space \tab \newline \return \formfeed} %) v)))

(defn client-record-violations
  "A registered client must identify itself. A record without a usable
  `:client-id` cannot establish provenance for anything committed against it,
  so the governor must not treat its mere existence as provenance."
  [client-record]
  (cond-> []
    (not (map? client-record))
    (conj {:rule :client-not-a-record
           :detail "client record must be a map"})

    (and (map? client-record) (blank? (:client-id client-record)))
    (conj {:rule :client-without-id
           :detail "registered client record has no usable :client-id"})))

(defn request-violations
  "A request must name the client it is about."
  [request]
  (cond-> []
    (not (map? request))
    (conj {:rule :request-not-a-map :detail "request must be a map"})

    (and (map? request) (blank? (:client-id request)))
    (conj {:rule :request-without-client-id
           :detail "request has no usable :client-id"})))

(defn proposal-violations
  "The proposal envelope. Payload contents are the domain's business; the
  envelope is the governor's, because routing decisions are read off it."
  [proposal]
  (cond-> []
    (not (map? proposal))
    (conj {:rule :proposal-not-a-map :detail "proposal must be a map"})

    (and (map? proposal) (not (keyword? (:op proposal))))
    (conj {:rule :proposal-without-op :detail "proposal :op must be a keyword"})

    (and (map? proposal)
         (contains? proposal :confidence)
         (not (and (number? (:confidence proposal))
                   (<= 0 (:confidence proposal) 1))))
    (conj {:rule :confidence-out-of-range
           :detail "proposal :confidence must be a number in [0.0, 1.0]"})

    ;; `some?`, not `contains?`. The advisor emits a fixed proposal shape, so
    ;; an operation that quotes nobody still carries the key with a nil value.
    ;; Quoting nobody is legitimate -- a briefing schedule has no speakers --
    ;; and nil is read as the empty set of quotes downstream, which cannot be
    ;; an unauthorized attribution. Only a present, non-nil, non-name-bearing
    ;; value is the defect. Measured: with `contains?` here, the two clean
    ;; non-release scenarios in `prprofessional.sim` held instead of
    ;; committing.
    (and (map? proposal)
         (some? (:quoted-spokespersons proposal))
         (not (and (coll? (:quoted-spokespersons proposal))
                   (every? string? (:quoted-spokespersons proposal)))))
    (conj {:rule :spokespersons-not-names
           :detail "proposal :quoted-spokespersons, when present, must be a collection of name strings"})))

(defn release-binding-violations
  "For an operation that binds to a registered release, the proposal must
  actually name the release and the day it proposes to act on.

  The day is required rather than optional because the embargo floor is a
  numeric comparison against it: without a usable day there is no comparison
  to make, and the pre-change tree resolved that by not comparing — which
  admitted the proposal. Absence is the violation."
  [proposal]
  (let [o (:op proposal)]
    (if-not (op/release-op? o)
      []
      (cond-> []
        (blank? (:release-id proposal))
        (conj {:rule :release-not-named
               :detail (str "operation " o " binds to a registered release but names none")})

        (not (integer? (:as-of-day proposal)))
        (conj {:rule :as-of-day-missing
               :detail (str "operation " o " needs an integer :as-of-day to compare against the embargo floor; "
                            "an absent day is not a reason to skip the floor")})))))

(defn vocabulary-violations
  "The operation must be one this repo declared. Undeclared and reserved are
  reported as different rules on purpose — see `prprofessional.operation`."
  [proposal]
  (let [o (:op proposal)]
    (cond
      (op/reserved? o)
      [{:rule :no-legal-authority :detail (op/reserved-reason o)}]

      (and (keyword? o) (not (op/supported? o)))
      [{:rule :undeclared-operation
        :detail (str "operation " o " is not in prprofessional.operation/supported")}]

      :else [])))
