# cloud-itonami-isco-2432

Open Business Blueprint for **ISCO-08 2432**: Public Relations Professionals — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — PublicRelationsProfessionalsAdvisor ⊣
PublicRelationsProfessionalsGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
62 tests / 149 assertions green, plus a governed-scenario harness
(`clojure -M:sim`: 16 scenarios, 14 refusals).

The press-release HARD invariants — an embargo floor and attribution
traceability, not narrative license:

1. **Embargo floor** — the proposed as-of day must be ≥ the release's
   registered embargo-lift-day. An embargo is a registered day, not a
   suggestion. The day is **required** on a release-bound proposal:
   an absent day is not a reason to skip the floor.
2. **Spokesperson membership** — every quoted spokesperson must be a
   member of the release's registered approved-spokespersons set (no
   unauthorized attribution).
3. **Declared vocabulary** — the operation must be named in
   `prprofessional.operation/supported`. Operations reserved to a
   human officer (`:issue-legal-statement`,
   `:certify-financial-disclosure`, `:speak-as-spokesperson`) are
   refused as an authority boundary, never escalated: escalation
   would imply a human could delegate them to an actor.

**Both (1) and (2) bind `:publish-release`, not only
`:approve-release`.** They used to be keyed on `:approve-release`
alone, which exempted the one operation that actually publishes to
the outside world; such a proposal escalated to a human with an
*empty* violation list, showing the reviewer nothing to refuse.
Binding is declared per-operation (`:release-op?`) so a governor
cannot name one op and forget the other.

Also HARD: unregistered/foreign release, unregistered organization,
blank client registration, non-`:propose` effect, out-of-range
confidence. Escalations (always human sign-off): operations declaring
`:escalates?` (`:publish-release`, `:flag-reputational-risk`), low
confidence (< 0.6).

The audit ledger is **chained** (`:ledger/seq` / `:ledger/prev` /
`:ledger/hash`), so a dropped or reordered entry is detectable, and
each write records `:approved-by :human` or `:actor` — a publication
a human authorised and one the actor took itself are different acts.
Truncation is *not* detectable without an external anchor, and
`prprofessional.ledger/verify` claims only what it can show.

Run the governed scenarios with `clojure -M:sim`. It exits non-zero
when the table demonstrates **no** refusal: a governed actor that
refuses nothing has shown nothing, and a harness that printed green
there would be theatre.

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
