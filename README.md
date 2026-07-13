# cloud-itonami-isco-2432

Open Business Blueprint for **ISCO-08 2432**: Public Relations Professionals — an ISCO
**Wave 0 (cognitive substrate)** occupation per ADR-2607121000:
pure-cognitive work, the LLM-first wave, **no robotics gate** —
eligible for actor implementation now.

**Maturity: `:implemented`** — PublicRelationsProfessionalsAdvisor ⊣
PublicRelationsProfessionalsGovernor as a langgraph StateGraph
(`intake → advise → govern → decide → commit/hold`, human-approval
interrupt), modeled on cloud-itonami-isco-4311's bookkeeping actor.
13 tests / 27 assertions green.

The press-release HARD invariants — an embargo floor and attribution
traceability, not narrative license:

1. **Embargo floor** — the proposed as-of day must be ≥ the release's
   registered embargo-lift-day. An embargo is a registered day, not a
   suggestion.
2. **Spokesperson membership** — every quoted spokesperson must be a
   member of the release's registered approved-spokespersons set (no
   unauthorized attribution).

Also HARD: unregistered/foreign release, unregistered organization,
non-`:propose` effect. Escalations (always human sign-off):
`:publish-release` (external publication), low confidence (< 0.6).

AGPL-3.0-or-later, forkable by any qualified operator. Part of the
[cloud-itonami](https://itonami.cloud) open business fleet.
