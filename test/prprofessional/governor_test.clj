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
  (let [st (fresh-store)
        v (governor/check req {} {:op :publish-release :effect :propose
                                  :release-id "R-1" :quoted-spokespersons #{"CEO"}
                                  :confidence 0.9 :stake :high} st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (approve 250 #{"CEO"}) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
