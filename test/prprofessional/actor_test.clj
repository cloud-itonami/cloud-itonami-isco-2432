(ns prprofessional.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [prprofessional.actor :as actor]
            [prprofessional.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Trade"})
    (store/register-release! st {:release-id "R-1" :client-id "client-1"
                                 :name "product-launch"
                                 :embargo-lift-day 200
                                 :approved-spokespersons #{"CEO"}})
    st))

(deftest commits-a-post-embargo-authorized-release
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-release :stake :low
                 :release-id "R-1" :as-of-day 250 :quoted-spokespersons #{"CEO"}}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-a-pre-embargo-release
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-release :stake :low
                 :release-id "R-1" :as-of-day 100 :quoted-spokespersons #{"CEO"}}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-publishes-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        ;; :as-of-day 250 is new: a publication now has to name the day it
        ;; is compared against, because the embargo floor binds
        ;; :publish-release too. The assertions below are unchanged.
        request {:client-id "client-1" :op :publish-release :stake :high
                 :release-id "R-1" :as-of-day 250 :quoted-spokespersons #{"CEO"}}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
