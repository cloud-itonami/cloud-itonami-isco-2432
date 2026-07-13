(ns prprofessional.governor
  "PublicRelationsProfessionalsGovernor — the independent safety/
  traceability layer for the ISCO-08 2432 community public relations
  professionals actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section). Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. PR twist: an embargo lift day is a registered
  floor a proposed publication day must not precede, and every quoted
  spokesperson must be a member of the registered approved-
  spokespersons set — attribution is traceability, not narrative
  license.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance — the organization must be registered.
    2. no-actuation      — proposal :effect must be :propose.
    3. release basis       — an approval must cite a REGISTERED
                           release belonging to this client.
    4. embargo floor       — the proposed as-of day must be >= the
                           release's registered :embargo-lift-day (an
                           embargo is a registered day, not a
                           suggestion).
    5. spokesperson membership — every quoted spokesperson must be a
                           member of the release's registered
                           :approved-spokespersons set (no
                           unauthorized attribution).
  ESCALATION invariants (:escalate? true, human sign-off):
    6. :op :publish-release (external publication).
    7. low confidence (< `confidence-floor`)."
  (:require [clojure.set :as set]
            [prprofessional.store :as store]))

(def confidence-floor 0.6)

(defn- hard-violations [{:keys [request proposal]} client-record r]
  (let [{:keys [op as-of-day quoted-spokespersons]} proposal
        approve? (= :approve-release op)
        unauthorized (when r (set/difference (set quoted-spokespersons)
                                             (:approved-spokespersons r)))]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（直接書込禁止）"})

      (and approve? (nil? r))
      (conj {:rule :unknown-release :detail "未登録 release への承認は不可"})

      (and approve? r (not= (:client-id r) (:client-id request)))
      (conj {:rule :release-wrong-client :detail "release が別 client のもの"})

      (and approve? r (integer? as-of-day) (< as-of-day (:embargo-lift-day r)))
      (conj {:rule :embargo-not-lifted
             :detail (str "day " as-of-day " < エンバーゴ解禁日 "
                          (:embargo-lift-day r) "（エンバーゴは登録済み日付であって提案ではない）")})

      (and approve? r (seq unauthorized))
      (conj {:rule :unauthorized-attribution
             :detail (str "未承認の発言者引用 " (vec unauthorized)
                          "（引用は追跡性であって物語上の裁量ではない）")}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `prprofessional.store/Store`. Pure — never
  mutates the store."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        r (some->> (:release-id proposal) (store/release store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record r)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        risky-op? (= :publish-release (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not risky-op?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? risky-op?))}))
