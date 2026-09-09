(ns prprofessional.sim-test
  (:require [clojure.test :refer [deftest is testing]]
            [prprofessional.phase :as phase]
            [prprofessional.sim :as sim]))

(deftest the-scenario-table-passes
  (let [r (sim/run)]
    (is (:ok? r) (str "sim mismatches: " (pr-str (mapv :name (:mismatches r)))))
    (is (empty? (:mismatches r)))))

(deftest the-table-demonstrates-refusals
  (testing "a governed actor's claim is not that it acts -- it is that there
            exist actions it refuses. A table with no refusal has shown
            nothing, and run/:ok? is false when the count is zero."
    (is (pos? (:refusals (sim/run))))))

(deftest no-refusal-ever-wrote-a-record
  (testing "a refusal that still wrote is the worst outcome available and
            would otherwise hide inside a matching phase"
    (is (empty? (:wrote-anyway (sim/run))))))

(deftest every-ledger-left-behind-verifies
  (is (empty? (:ledger-breaks (sim/run)))))

(deftest the-table-covers-both-directions
  (testing "a table of only-refusals would prove the governor is a brick"
    (let [results (:results (sim/run))
          commits (filter #(= :commit (:actual %)) results)]
      (is (pos? (count commits)))
      (is (pos? (count (filter :refusal? results)))))))

(deftest report-refuses-to-report-a-pass-with-no-refusals
  (testing "the zero-refusal message is a distinct outcome, not a FAIL"
    (let [msg (sim/report {:results [] :refusals 0 :mismatches []
                           :wrote-anyway [] :ledger-breaks [] :ok? false})]
      (is (re-find #"REFUSING TO REPORT A PASS" msg)))))

(deftest publish-scenarios-reach-hold-not-escalation
  (testing "the three regression rows: before the release binding covered
            :publish-release these escalated with an EMPTY violation list,
            so the human reviewer was shown nothing to refuse"
    (let [by-name (into {} (map (juxt :name identity) (:results (sim/run))))]
      (doseq [n [:publish-before-embargo
                 :publish-unauthorized-attribution
                 :publish-unregistered-release]]
        (is (= :hold (:actual (get by-name n))) (str n " must hold"))
        (is (phase/refusal? (:actual (get by-name n))))))))
