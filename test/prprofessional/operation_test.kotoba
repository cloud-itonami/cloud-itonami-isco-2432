(ns prprofessional.operation-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [prprofessional.operation :as op]))

(deftest supported-and-reserved-are-disjoint
  (testing "an op cannot be both proposable and reserved to a human officer"
    (is (empty? (set/intersection (set (keys op/supported))
                                          (set (keys op/reserved)))))))

(deftest undeclared-ops-are-not-supported
  (is (not (op/supported? :shred-the-embargoed-draft)))
  (is (not (op/declared? :shred-the-embargoed-draft))))

(deftest reserved-ops-are-declared-but-not-supported
  (testing "declared-but-unsupported is what lets the refusal say WHY"
    (doseq [o (keys op/reserved)]
      (is (op/declared? o))
      (is (not (op/supported? o)))
      (is (string? (op/reserved-reason o))))))

(deftest publish-release-binds-to-a-registered-release
  (testing "the op that actually publishes is release-bound; this is the
            property whose absence exempted it from every release invariant"
    (is (op/release-op? :publish-release))
    (is (op/release-op? :approve-release))))

(deftest non-release-ops-do-not-bind
  (is (not (op/release-op? :schedule-briefing)))
  (is (not (op/release-op? :draft-release)))
  (is (not (op/release-op? :flag-reputational-risk))))

(deftest escalation-is-a-property-of-the-operation
  (is (op/escalates? :publish-release))
  (is (op/escalates? :flag-reputational-risk))
  (is (not (op/escalates? :approve-release))))

(deftest undeclared-ops-neither-escalate-nor-bind
  (testing "a false here is not an admission -- the governor hard-blocks first"
    (is (not (op/escalates? :no-such-op)))
    (is (not (op/release-op? :no-such-op)))))
