(ns prprofessional.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [prprofessional.facts :as facts]))

(defn- rules [vs] (set (map :rule vs)))

(deftest a-usable-client-record-is-well-formed
  (is (empty? (facts/client-record-violations {:client-id "c1" :name "Acme"}))))

(deftest a-blank-client-record-cannot-establish-provenance
  (testing "registering {} put a record under the key nil, after which a
            request carrying no :client-id resolved to it and the provenance
            invariant returned ok? true"
    (is (contains? (rules (facts/client-record-violations {})) :client-without-id))
    (is (contains? (rules (facts/client-record-violations {:client-id "  "}))
                   :client-without-id))
    (is (contains? (rules (facts/client-record-violations {:client-id 7}))
                   :client-without-id))))

(deftest a-non-map-client-record-is-not-a-record
  (is (contains? (rules (facts/client-record-violations "acme")) :client-not-a-record)))

(deftest a-request-must-name-its-client
  (is (empty? (facts/request-violations {:client-id "c1"})))
  (is (contains? (rules (facts/request-violations {})) :request-without-client-id))
  (is (contains? (rules (facts/request-violations nil)) :request-not-a-map)))

(deftest proposal-envelope-well-formedness
  (is (empty? (facts/proposal-violations {:op :approve-release :confidence 0.9})))
  (is (contains? (rules (facts/proposal-violations {:op "approve"})) :proposal-without-op))
  (is (contains? (rules (facts/proposal-violations {:op :approve-release :confidence 99.0}))
                 :confidence-out-of-range))
  (is (contains? (rules (facts/proposal-violations {:op :approve-release :confidence "high"}))
                 :confidence-out-of-range)))

(deftest an-absent-confidence-is-left-alone
  (testing "a missing confidence already reads as 0.0 in the governor and
            thus escalates, which is correct; a present-but-unusable one is
            the defect"
    (is (empty? (facts/proposal-violations {:op :approve-release})))))

(deftest quoting-nobody-is-legitimate
  (testing "the advisor emits a fixed shape, so an op that quotes nobody
            still carries the key with a nil value"
    (is (empty? (facts/proposal-violations {:op :schedule-briefing
                                            :quoted-spokespersons nil})))
    (is (empty? (facts/proposal-violations {:op :approve-release
                                            :quoted-spokespersons #{}})))))

(deftest a-present-non-name-bearing-speaker-list-is-a-defect
  (is (contains? (rules (facts/proposal-violations {:op :approve-release
                                                    :quoted-spokespersons [7]}))
                 :spokespersons-not-names))
  (is (contains? (rules (facts/proposal-violations {:op :approve-release
                                                    :quoted-spokespersons "CEO"}))
                 :spokespersons-not-names)))

(deftest release-bound-ops-must-name-a-release-and-a-day
  (testing "an absent day is not a reason to skip the embargo floor"
    (is (empty? (facts/release-binding-violations
                 {:op :approve-release :release-id "R-1" :as-of-day 250})))
    (is (contains? (rules (facts/release-binding-violations
                           {:op :approve-release :release-id "R-1"}))
                   :as-of-day-missing))
    (is (contains? (rules (facts/release-binding-violations
                           {:op :approve-release :release-id "R-1" :as-of-day "250"}))
                   :as-of-day-missing))
    (is (contains? (rules (facts/release-binding-violations
                           {:op :approve-release :as-of-day 250}))
                   :release-not-named))))

(deftest publish-release-is-release-bound-too
  (testing "the op that actually publishes carries the same binding"
    (is (contains? (rules (facts/release-binding-violations
                           {:op :publish-release :release-id "R-1"}))
                   :as-of-day-missing))))

(deftest non-release-ops-need-no-release
  (is (empty? (facts/release-binding-violations {:op :schedule-briefing})))
  (is (empty? (facts/release-binding-violations {:op :flag-reputational-risk}))))

(deftest vocabulary-separates-undeclared-from-reserved
  (testing "an undeclared op is a vocabulary error; a reserved one is an
            authority boundary. Conflating them would let a future edit
            supported-list one of them by accident."
    (is (empty? (facts/vocabulary-violations {:op :approve-release})))
    (is (= #{:undeclared-operation}
           (rules (facts/vocabulary-violations {:op :shred-the-embargoed-draft}))))
    (is (= #{:no-legal-authority}
           (rules (facts/vocabulary-violations {:op :issue-legal-statement}))))
    (is (= #{:no-legal-authority}
           (rules (facts/vocabulary-violations {:op :speak-as-spokesperson}))))))
