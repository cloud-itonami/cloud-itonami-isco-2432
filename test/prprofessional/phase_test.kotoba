(ns prprofessional.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [prprofessional.phase :as phase]))

(deftest hard-outranks-escalate
  (testing "a proposal that is both hard-blocked and low-confidence must HOLD.
            Escalating it would put a question to a human that they have no
            authority to answer yes to."
    (is (= :hold (phase/of-verdict {:hard? true :escalate? true})))))

(deftest routes-each-verdict
  (is (= :hold (phase/of-verdict {:hard? true})))
  (is (= :request-approval (phase/of-verdict {:escalate? true})))
  (is (= :commit (phase/of-verdict {}))))

(deftest only-commit-writes
  (is (phase/writes? :commit))
  (is (not (phase/writes? :hold)))
  (is (not (phase/writes? :request-approval))))

(deftest request-approval-is-a-refusal-not-an-approval-in-waiting
  (is (phase/refusal? :hold))
  (is (phase/refusal? :request-approval))
  (is (not (phase/refusal? :commit))))

(deftest approved-commit-identifies-a-human-resumed-write
  (is (phase/approved-commit? :request-approval))
  (is (not (phase/approved-commit? :commit))))
