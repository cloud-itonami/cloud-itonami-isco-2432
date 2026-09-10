(ns prprofessional.operation
  "The closed vocabulary of operations the ISCO-08 2432 public relations actor
  may propose.

  Runtime: portable `.cljc` (pure data + pure predicates, no host interop).

  Why this namespace exists. Before it, the operation vocabulary lived in two
  places that could not disagree loudly: the README's prose list, and the
  Governor's private `(= :approve-release op)` test plus a single named
  escalating op. That made the Governor a *denylist* — it bound two named ops
  and admitted everything else. Measured on the pre-change tree, against a
  registered client:

      {:op :shred-the-embargoed-draft :effect :propose :confidence 0.95}
      => {:ok? true :violations []}

  An actor whose operation set is open cannot be governed, because the
  governor is answering a question about a vocabulary nobody declared. So the
  vocabulary is declared here, once, as an allowlist, and
  `prprofessional.governor` refuses anything outside it.

  Two disjoint maps:

  * `supported` — what the actor may propose. `:escalates?` and
    `:release-op?` are properties of the operation, not of the governor's
    mood, so they live beside it.
  * `reserved` — operations that name authority belonging exclusively to a
    human officer. These are *declared* rather than merely absent so the
    refusal can say why: an undeclared op is a vocabulary error, a reserved op
    is a professional-authority boundary. Conflating them would let a future
    edit `supported`-list one of them by accident.

  `:release-op?` is the field that closes the gap this repo shipped with. The
  embargo floor and the spokesperson-membership invariant used to be gated on
  `(= :approve-release op)`, so `:publish-release` — the one operation that
  actually publishes to the outside world — was exempt from both. Binding is a
  property of the operation, so it is declared here and the governor reads it,
  rather than the governor naming one op and forgetting the other."
  (:refer-clojure :exclude [supported]))

(def supported
  "Operations the actor may propose.

  `:escalates?` true means human sign-off is required regardless of advisor
  confidence. `:release-op?` true means the proposal binds to a REGISTERED
  release, and therefore must satisfy every registered fact about it —
  ownership, the embargo floor, and spokesperson membership."
  {:draft-release
   {:escalates? false
    :release-op? false
    :summary "draft release copy for the communications lead's review"}

   :approve-release
   {:escalates? false
    :release-op? true
    :summary "approve a registered release as ready for publication"}

   :publish-release
   {:escalates? true
    :release-op? true
    :summary "publish a registered release externally"}

   :schedule-briefing
   {:escalates? false
    :release-op? false
    :summary "press-briefing scheduling proposal"}

   :flag-reputational-risk
   {:escalates? true
    :release-op? false
    :summary "surface a reputational risk to the communications lead"}})

(def reserved
  "Operations reserved to a human officer. Naming one in a proposal is a
  permanent hard block, never an escalation: escalation would imply a human
  could approve the *actor* doing it, and no human can delegate this."
  {:issue-legal-statement
   {:reason "a legal statement on behalf of the organization is counsel's exclusive responsibility"}

   :certify-financial-disclosure
   {:reason "certifying a material financial disclosure is the signing officer's exclusive responsibility"}

   :speak-as-spokesperson
   {:reason "an actor cannot be the source of an attributed statement; a quote requires the human who said it"}})

(defn supported? [op] (contains? supported op))
(defn reserved? [op] (contains? reserved op))

(defn declared?
  "True if `op` is named anywhere in this vocabulary. An op that is neither
  supported nor reserved is undeclared — the governor refuses it."
  [op]
  (or (supported? op) (reserved? op)))

(defn escalates?
  "True if the operation itself always requires human sign-off. Unsupported
  ops are never reached by this predicate (the governor hard-blocks first),
  so a false here is not an admission."
  [op]
  (boolean (get-in supported [op :escalates?])))

(defn release-op?
  "True if the operation binds to a registered release and must therefore
  satisfy every registered fact about it. False for undeclared and reserved
  ops, which the governor hard-blocks before this is consulted."
  [op]
  (boolean (get-in supported [op :release-op?])))

(defn reserved-reason [op] (get-in reserved [op :reason]))
