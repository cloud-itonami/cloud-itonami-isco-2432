(ns prprofessional.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [prprofessional.store :as store]
            [prprofessional.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-release! st {:release-id "R-1" :client-id "client-1"
                                 :name "product-launch"
                                 :embargo-lift-day 200
                                 :approved-spokespersons #{"CEO" "CTO"}})
    st))

(defn- approve [day spokes]
  {:op :approve-release :effect :propose :release-id "R-1"
   :as-of-day day :quoted-spokespersons spokes :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-after-embargo-and-approved-spokespersons
  (let [st (fresh-store)
        v (governor/check req {} (approve 250 #{"CEO"}) st)]
    (is (:ok? v))))

(deftest ok-at-exact-embargo-lift-day
  (testing "the embargo lift day boundary is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (approve 200 #{"CEO"}) st)]
      (is (:ok? v)))))

(deftest hard-on-before-embargo-lift
  (testing "an embargo is a registered day, not a suggestion"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (approve 150 #{"CEO"}) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :embargo-not-lifted (:rule %)) (:violations v))))))

(deftest hard-on-unauthorized-attribution
  (testing "attribution is traceability, not narrative license"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (approve 250 #{"Random Intern"}) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :unauthorized-attribution (:rule %)) (:violations v))))))

(deftest hard-on-unknown-release
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve 250 #{"CEO"}) :release-id "R-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-release (:rule %)) (:violations v)))))

(deftest hard-on-foreign-release
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (approve 250 #{"CEO"}) st)]
      (is (:hard? v))
      (is (some #(= :release-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (approve 250 #{"CEO"}) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve 250 #{"CEO"}) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest escalates-release-publication
  ;; :as-of-day 250 is new here, and its absence used to be the whole defect:
  ;; :publish-release was exempt from the release invariants, so this proposal
  ;; escalated with an EMPTY violation list no matter what day or speaker it
  ;; carried. The assertions are unchanged -- a clean publication still
  ;; escalates rather than hard-blocking. Only the input now carries the day
  ;; the embargo floor is compared against.
  (let [st (fresh-store)
        v (governor/check req {} {:op :publish-release :effect :propose
                                  :release-id "R-1" :as-of-day 250
                                  :quoted-spokespersons #{"CEO"}
                                  :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve 250 #{"CEO"}) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

;; ---------------------------------------------------------------------------
;; Regressions. Each of these returned {:ok? true :violations []} -- or
;; escalated with an empty violation list -- on the tree before
;; prprofessional.operation / .facts existed. Each asserts the RULE NAME, not
;; merely that something was refused: a negative test that passes for a
;; different reason than the one it names is how a governor gets credit for a
;; refusal it did not make.
;; ---------------------------------------------------------------------------

(deftest hard-on-undeclared-operation
  (testing "the governor was a denylist: it bound two named ops and admitted
            everything else"
    (let [st (fresh-store)
          v (governor/check req {} {:op :shred-the-embargoed-draft
                                    :effect :propose :confidence 0.95} st)]
      (is (:hard? v))
      (is (some #(= :undeclared-operation (:rule %)) (:violations v))))))

(deftest hard-on-reserved-operation
  (testing "reserved is an authority boundary, never an escalation -- no human
            can delegate to an actor what is theirs alone"
    (doseq [o [:issue-legal-statement :certify-financial-disclosure
               :speak-as-spokesperson]]
      (let [st (fresh-store)
            v (governor/check req {} {:op o :effect :propose :confidence 0.95} st)]
        (is (:hard? v) (str o " must hard-block"))
        (is (not (:escalate? v)) (str o " must not escalate"))
        (is (some #(= :no-legal-authority (:rule %)) (:violations v)))))))

(deftest publish-release-is-bound-by-the-embargo-floor
  (testing "the op that actually publishes was exempt from the floor and
            escalated with an EMPTY violation list, so the reviewer was shown
            a clean bill of health for an embargo-breaking publication"
    (let [st (fresh-store)
          v (governor/check req {} {:op :publish-release :effect :propose
                                    :release-id "R-1" :as-of-day 150
                                    :quoted-spokespersons #{"CEO"}
                                    :confidence 0.9} st)]
      (is (:hard? v))
      (is (some #(= :embargo-not-lifted (:rule %)) (:violations v))))))

(deftest publish-release-is-bound-by-spokesperson-membership
  (let [st (fresh-store)
        v (governor/check req {} {:op :publish-release :effect :propose
                                  :release-id "R-1" :as-of-day 250
                                  :quoted-spokespersons #{"Random Intern"}
                                  :confidence 0.9} st)]
    (is (:hard? v))
    (is (some #(= :unauthorized-attribution (:rule %)) (:violations v)))))

(deftest publish-release-is-bound-by-release-registration
  (let [st (fresh-store)
        v (governor/check req {} {:op :publish-release :effect :propose
                                  :release-id "R-ghost" :as-of-day 250
                                  :quoted-spokespersons #{"CEO"}
                                  :confidence 0.9} st)]
    (is (:hard? v))
    (is (some #(= :unknown-release (:rule %)) (:violations v)))))

(deftest hard-on-missing-as-of-day
  (testing "a HARD invariant bypassed by omitting a field is not a floor"
    (let [st (fresh-store)
          v (governor/check req {} (dissoc (approve 250 #{"CEO"}) :as-of-day) st)]
      (is (:hard? v))
      (is (some #(= :as-of-day-missing (:rule %)) (:violations v))))))

(deftest hard-on-out-of-range-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve 250 #{"CEO"}) :confidence 99.0) st)]
    (is (:hard? v))
    (is (some #(= :confidence-out-of-range (:rule %)) (:violations v)))))

(deftest a-blank-client-registration-does-not-establish-provenance
  (testing "registering {} landed a record under the key nil, after which a
            request carrying no :client-id resolved to it and the whole
            provenance invariant returned ok? true"
    (let [st (store/mem-store)]
      (store/register-client! st {})
      (store/register-release! st {:release-id "R-1" :client-id nil
                                   :name "n" :embargo-lift-day 10
                                   :approved-spokespersons #{"CEO"}})
      (let [v (governor/check {} {} {:op :approve-release :effect :propose
                                     :release-id "R-1" :as-of-day 250
                                     :quoted-spokespersons #{"CEO"}
                                     :confidence 0.95} st)]
        (is (:hard? v))
        (is (some #(= :client-without-id (:rule %)) (:violations v)))
        (is (some #(= :request-without-client-id (:rule %)) (:violations v)))))))

(deftest the-governor-is-not-a-brick
  (testing "a governor that refused everything would also close every defect
            above; the clean path must still pass"
    (let [st (fresh-store)]
      (is (:ok? (governor/check req {} (approve 250 #{"CEO"}) st)))
      (is (:ok? (governor/check req {} {:op :schedule-briefing :effect :propose
                                        :confidence 0.9} st))))))
