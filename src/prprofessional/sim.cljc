(ns prprofessional.sim
  "Deterministic governed-scenario harness for the ISCO-08 2432 public
  relations actor: run a table of requests through the real StateGraph and
  report which ones the governor refused.

  Runtime: `run` and `report` are portable `.cljc`. `-main` is `:clj`-only,
  because process exit codes are a host concern; the `:cljs` branch throws
  rather than pretending to exit.

  Why this namespace exists, and why it fails loudly. A governed actor's
  claim is not that it acts — it is that there exist actions it refuses. A
  harness that ran only clean scenarios would print green while demonstrating
  nothing, which is the shape this workspace has repeatedly caught: a check
  that could not fail returning the same value as a check that passed.

  So `run` counts refusals, and `-main` exits non-zero when the count is zero.
  A scenario table that has stopped exercising the governor is a defect in the
  table, and it is reported as one rather than as a pass.

  The three questions this harness answers that a unit test does not:
    * does the *wired graph* refuse, or only the pure `check` function
    * does an escalated request actually interrupt rather than write
    * does the ledger it leaves behind verify, and does it record who
      approved each write"
  (:require [prprofessional.actor :as actor]
            [prprofessional.ledger :as led]
            [prprofessional.phase :as phase]
            [prprofessional.store :as store]))

(def registered-client
  {:client-id "sim-client-1" :name "Kobo Trade"})

(def registered-release
  {:release-id "R-1" :client-id "sim-client-1" :name "product-launch"
   :embargo-lift-day 200 :approved-spokespersons #{"CEO"}})

(def scenarios
  "Each scenario names the phase it must reach. `:expect` is asserted, not
  merely printed — a scenario whose actual phase differs is a mismatch and
  fails the run, so this table is a specification and not a log.

  The four scenarios marked (regression) reach `:hold` only because of the
  change that added this namespace. On the tree before it they reached
  `:commit` or escalated with an empty violation list."
  [{:name :clean-approve
    :request {:client-id "sim-client-1" :op :approve-release :release-id "R-1"
              :as-of-day 250 :quoted-spokespersons #{"CEO"}}
    :expect :commit
    :why "declared op, registered release, past embargo, approved spokesperson"}

   {:name :clean-schedule-briefing
    :request {:client-id "sim-client-1" :op :schedule-briefing}
    :expect :commit
    :why "declared op that does not bind to a release"}

   {:name :unregistered-client
    :request {:client-id "nobody" :op :approve-release :release-id "R-1"
              :as-of-day 250 :quoted-spokespersons #{"CEO"}}
    :expect :hold
    :why "provenance: the client was never registered"}

   {:name :request-without-client
    :request {:op :approve-release :release-id "R-1" :as-of-day 250}
    :expect :hold
    :why "provenance: the request names no client"}

   {:name :undeclared-operation
    :request {:client-id "sim-client-1" :op :shred-the-embargoed-draft}
    :expect :hold
    :why "vocabulary: op is not in prprofessional.operation/supported (regression)"}

   {:name :reserved-issue-legal-statement
    :request {:client-id "sim-client-1" :op :issue-legal-statement}
    :expect :hold
    :why "authority: a legal statement is counsel's (regression)"}

   {:name :reserved-certify-financial-disclosure
    :request {:client-id "sim-client-1" :op :certify-financial-disclosure}
    :expect :hold
    :why "authority: certifying a disclosure is the signing officer's"}

   {:name :reserved-speak-as-spokesperson
    :request {:client-id "sim-client-1" :op :speak-as-spokesperson}
    :expect :hold
    :why "authority: an actor cannot be the source of an attributed statement"}

   {:name :approve-before-embargo
    :request {:client-id "sim-client-1" :op :approve-release :release-id "R-1"
              :as-of-day 150 :quoted-spokespersons #{"CEO"}}
    :expect :hold
    :why "embargo floor: day 150 precedes the registered lift day 200"}

   {:name :approve-unauthorized-attribution
    :request {:client-id "sim-client-1" :op :approve-release :release-id "R-1"
              :as-of-day 250 :quoted-spokespersons #{"Random Intern"}}
    :expect :hold
    :why "attribution: the quoted speaker is not in the registered set"}

   {:name :publish-before-embargo
    :request {:client-id "sim-client-1" :op :publish-release :release-id "R-1"
              :as-of-day 150 :quoted-spokespersons #{"CEO"}}
    :expect :hold
    :why "embargo floor binds the op that actually publishes (regression)"}

   {:name :publish-unauthorized-attribution
    :request {:client-id "sim-client-1" :op :publish-release :release-id "R-1"
              :as-of-day 250 :quoted-spokespersons #{"Random Intern"}}
    :expect :hold
    :why "attribution binds the op that actually publishes (regression)"}

   {:name :publish-unregistered-release
    :request {:client-id "sim-client-1" :op :publish-release :release-id "R-ghost"
              :as-of-day 250 :quoted-spokespersons #{"CEO"}}
    :expect :hold
    :why "release basis binds the op that actually publishes (regression)"}

   {:name :approve-without-as-of-day
    :request {:client-id "sim-client-1" :op :approve-release :release-id "R-1"
              :quoted-spokespersons #{"CEO"}}
    :expect :hold
    :why "an absent day is not a reason to skip the embargo floor (regression)"}

   {:name :clean-publish-escalates
    :request {:client-id "sim-client-1" :op :publish-release :release-id "R-1"
              :as-of-day 250 :quoted-spokespersons #{"CEO"}}
    :expect :request-approval
    :why "external publication always requires human sign-off"}

   {:name :flag-reputational-risk-escalates
    :request {:client-id "sim-client-1" :op :flag-reputational-risk}
    :expect :request-approval
    :why "the operation itself always requires human sign-off"}])

(defn- run-one [scenario]
  (let [st (store/mem-store)
        _ (store/register-client! st registered-client)
        _ (store/register-release! st registered-release)
        graph (actor/build-graph {:store st})
        thread (str "sim-" (name (:name scenario)))
        result (actor/run-request! graph (:request scenario) {} thread)
        state (:state result)
        actual (or (:disposition state)
                   ;; A run that never reached :decide produced no phase at
                   ;; all; report that rather than defaulting it to a phase,
                   ;; which would make an unrun scenario look like a verdict.
                   :no-phase)]
    {:name (:name scenario)
     :expect (:expect scenario)
     :actual actual
     :why (:why scenario)
     :status (:status result)
     :match? (= actual (:expect scenario))
     :refusal? (and (not= actual :no-phase) (phase/refusal? actual))
     :wrote? (pos? (count (store/records-of st (:client-id (:request scenario)))))
     :ledger-verify (led/verify (store/ledger st))}))

(defn run
  "Run every scenario. Returns
  `{:results [..] :refusals n :mismatches [..] :ledger-breaks [..] :ok? bool}`.

  `:ok?` requires all four: every scenario reached its expected phase, no
  refusal wrote a record anyway, every ledger left behind verifies, and at
  least one refusal was demonstrated."
  []
  (let [results (mapv run-one scenarios)
        refusals (count (filter :refusal? results))
        mismatches (filterv (complement :match?) results)
        ;; A refusal that still wrote a record is the worst outcome available
        ;; and would otherwise hide inside a matching phase.
        wrote-anyway (filterv #(and (:refusal? %) (:wrote? %)) results)
        ledger-breaks (filterv #(not (:ok? (:ledger-verify %))) results)]
    {:results results
     :refusals refusals
     :mismatches mismatches
     :wrote-anyway wrote-anyway
     :ledger-breaks ledger-breaks
     :ok? (and (empty? mismatches)
               (empty? wrote-anyway)
               (empty? ledger-breaks)
               (pos? refusals))}))

(defn report
  "Human-readable run report. Pure: takes the result of `run`."
  [{:keys [results refusals mismatches wrote-anyway ledger-breaks ok?]}]
  (str
   "prprofessional.sim — governed scenario run\n"
   (apply str
          (for [r results]
            (str "  " (if (:match? r) "ok  " "BAD ")
                 (name (:name r))
                 " expect=" (name (:expect r))
                 " actual=" (name (:actual r))
                 (when (:refusal? r) " [refused]")
                 "\n")))
   "  scenarios=" (count results)
   " refusals=" refusals
   " mismatches=" (count mismatches)
   " wrote-anyway=" (count wrote-anyway)
   " ledger-breaks=" (count ledger-breaks)
   "\n"
   (cond
     (zero? refusals)
     "  REFUSING TO REPORT A PASS: the scenario table demonstrated no refusal.\n"
     ok? "  PASS\n"
     :else "  FAIL\n")))

#?(:clj
   (defn -main [& _]
     (let [r (run)]
       (print (report r))
       (flush)
       (System/exit (if (:ok? r) 0 1))))
   :cljs
   (defn -main [& _]
     (throw (ex-info "prprofessional.sim/-main is :clj-only (process exit codes are a host concern); call `run` and inspect the result instead" {}))))
