(ns prprofessional.ledger-test
  (:require [clojure.test :refer [deftest is testing]]
            [prprofessional.ledger :as led]))

(deftest empty-ledger-verifies
  (is (:ok? (led/verify []))))

(deftest appended-chain-verifies
  (let [l (-> [] (led/append {:disposition :commit}) (led/append {:disposition :hold}))]
    (is (:ok? (led/verify l)))
    (is (= 2 (:length (led/verify l))))
    (is (= [0 1] (mapv :ledger/seq l)))))

(deftest tampering-with-content-breaks-the-chain
  (testing "this is the whole claim: append-only is a property of the artifact"
    (let [l (-> [] (led/append {:disposition :commit :record {:amount 1}})
                (led/append {:disposition :commit :record {:amount 2}}))
          tampered (assoc-in l [0 :record :amount] 999)
          v (led/verify tampered)]
      (is (not (:ok? v)))
      (is (= 0 (:broken-at v)))
      (is (= :hash-mismatch (:reason v))))))

(deftest reordering-breaks-the-chain
  (let [l (-> [] (led/append {:a 1}) (led/append {:a 2}))
        v (led/verify (vec (reverse l)))]
    (is (not (:ok? v)))
    (is (= :seq-mismatch (:reason v)))))

(deftest dropping-a-middle-entry-breaks-the-chain
  (let [l (-> [] (led/append {:a 1}) (led/append {:a 2}) (led/append {:a 3}))
        v (led/verify [(nth l 0) (nth l 2)])]
    (is (not (:ok? v)))
    (is (= :seq-mismatch (:reason v)))))

(deftest truncation-is-not-detectable-and-is-not-claimed-to-be
  (testing "a chain cannot detect entries it never saw. verify claims only
            what it can show -- stating the limit rather than implying more."
    (let [l (-> [] (led/append {:a 1}) (led/append {:a 2}))]
      (is (:ok? (led/verify [(first l)]))))))

(deftest commit-entries-record-who-approved-the-write
  (testing "a human-authorised publication and one the actor took itself are
            different acts; before this they were the same ledger entry"
    (let [human (led/commit-entry {:op :publish-release} :human)
          actor (led/commit-entry {:op :approve-release} :actor)]
      (is (= :human (:approved-by human)))
      (is (= :actor (:approved-by actor)))
      (is (not= (dissoc human :record) (dissoc actor :record))))))

(deftest hold-entries-carry-the-violations
  (let [e (led/hold-entry {:violations [{:rule :embargo-not-lifted}]})]
    (is (= :hold (:disposition e)))
    (is (= :none (:approved-by e)))
    (is (seq (get-in e [:verdict :violations])))))

(deftest chain-hash-is-deterministic
  (is (= (led/chain-hash 0 {:a 1}) (led/chain-hash 0 {:a 1})))
  (is (not= (led/chain-hash 0 {:a 1}) (led/chain-hash 1 {:a 1}))))
