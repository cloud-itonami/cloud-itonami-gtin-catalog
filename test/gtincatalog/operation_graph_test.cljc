(ns gtincatalog.operation-graph-test
  "Integration tests for `gtincatalog.operation/build` -- proves the REAL
  compiled `langgraph.graph` StateGraph runs end-to-end through commit /
  hard-hold / escalate-approve / escalate-reject routes.

  The headline test is
  `pack-size-merge-never-even-reaches-a-human`: a conflicting merge must
  route straight to :hold, because offering it for approval would invite
  a tired reviewer to wave through exactly the failure this actor exists
  to prevent."
  (:require [clojure.test :refer [deftest is testing]]
            [gtincatalog.operation :as operation]
            [gtincatalog.store :as store]
            [langgraph.graph :as g]))

(def ^:private op-context {:actor-id "catalog-01" :phase 3})

(def can  "gtin.05449000000996")
(def six  "prod.coca-cola-330ml-6pack")
(def dup  "prod.coca-cola-330ml-dup")

(defn- exec
  ([actor tid request] (exec actor tid request op-context))
  ([actor tid request context]
   (g/run* actor {:request request :context context} {:thread-id tid})))

(deftest registration-auto-commits-in-phase-3
  (let [s (store/seed-db)
        actor (operation/build s)]
    (is (empty? (store/ledger s)))
    (let [result (exec actor "t-reg"
                       {:op :register-product
                        :patch {:gtin "4902102072618" :name "Irohasu 555ml"
                                :net-content 555 :uom "ml" :pack-count 1}})]
      (is (= :done (:status result)))
      (is (= :commit (:disposition (:state result))))
      (is (= :committed (:t (first (store/ledger s)))))
      (is (some? (store/product-record s "gtin.04902102072618"))))))

(deftest alias-auto-commits-and-becomes-resolvable
  (let [s (store/seed-db)
        actor (operation/build s)
        result (exec actor "t-alias"
                     {:op :propose-alias :product-id can
                      :patch {:code "4902102072618"}})]
    (is (= :done (:status result)))
    (is (= :commit (:disposition (:state result))))
    (is (= can (store/resolve-canonical s "4902102072618")))))

(deftest pack-size-merge-never-even-reaches-a-human
  (testing "a conflicting merge routes straight to :hold. Offering it for
            approval would invite a reviewer to wave through exactly the
            failure this actor exists to prevent"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-conflict"
                       {:op :propose-merge :patch {:from six :into can :confidence 0.99}})]
      (is (= :done (:status result)) "not :interrupted — no human is even asked")
      (is (= :hold (:disposition (:state result))))
      (is (some #{:distinguishing-conflict}
                (map :rule (:violations (first (store/ledger s))))))
      (testing "and the catalog is untouched"
        (is (= :active (:status (store/product-record s six))))
        (is (empty? (store/catalog-log s)))))))

(deftest genuine-duplicate-merge-escalates-then-commits-on-approval
  (let [s (store/seed-db)
        actor (operation/build s)
        held (exec actor "t-merge"
                   {:op :propose-merge :patch {:from dup :into can :confidence 0.95}})]
    (is (= :interrupted (:status held)))
    (is (= [:request-approval] (:frontier held)))
    (is (= :active (:status (store/product-record s dup)))
        "nothing merged yet — awaiting human sign-off")

    (let [approved (g/run* actor {:approval {:status :approved :by "steward-01"}}
                           {:thread-id "t-merge" :resume? true})]
      (is (= :done (:status approved)))
      (is (= :commit (:disposition (:state approved))))
      (is (= :merged-away (:status (store/product-record s dup))))
      (is (= can (store/resolve-canonical s dup)))
      (is (= "steward-01" (:approved-by (:payload (first (store/catalog-log s)))))))))

(deftest rejected-merge-leaves-the-catalog-untouched
  (let [s (store/seed-db)
        actor (operation/build s)
        _held (exec actor "t-merge-reject"
                    {:op :propose-merge :patch {:from dup :into can :confidence 0.95}})
        rejected (g/run* actor {:approval {:status :rejected :by "steward-01"}}
                         {:thread-id "t-merge-reject" :resume? true})]
    (is (= :done (:status rejected)))
    (is (= :hold (:disposition (:state rejected))))
    (is (= :active (:status (store/product-record s dup))))
    (is (= :approval-rejected (:t (first (store/ledger s)))))))

(deftest invalid-gtin-hard-holds-through-the-compiled-graph
  (let [s (store/seed-db)
        actor (operation/build s)
        result (exec actor "t-badgtin"
                     {:op :propose-alias :product-id can
                      :patch {:code "4902102072619"}})]
    (is (= :done (:status result)))
    (is (= :hold (:disposition (:state result))))
    (is (some #{:invalid-gtin} (map :rule (:violations (first (store/ledger s))))))
    (is (nil? (store/alias-target s "4902102072619"))
        "an unverifiable code never entered the alias table")))

(deftest catalog-concern-escalates-and-threads-the-real-proposal
  (let [distinctive (str "TEST-CONCERN-" (rand-int 1000000000))
        s (store/seed-db)
        actor (operation/build s)
        held (exec actor "t-concern"
                   {:op :flag-catalog-concern :product-id can
                    :patch {:concern distinctive}})]
    (is (= :interrupted (:status held)))
    (let [approved (g/run* actor {:approval {:status :approved :by "steward-01"}}
                           {:thread-id "t-concern" :resume? true})]
      (is (= :done (:status approved)))
      (is (= distinctive (:concern (:payload (first (store/catalog-log s)))))
          "proof the graph threads the Advisor's REAL proposal rather than
           hardcoding a pass-string"))))

(deftest phase-gates-are-wired-into-the-compiled-graph
  (testing "phase 1 has not enabled alias binding yet"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-phase1"
                       {:op :propose-alias :product-id can :patch {:code "4902102072618"}}
                       {:actor-id "catalog-01" :phase 1})]
      (is (= :hold (:disposition (:state result))))
      (is (= :phase-disabled (:phase-reason (first (store/ledger s)))))))
  (testing "phase 0 writes nothing"
    (let [s (store/seed-db)
          actor (operation/build s)
          result (exec actor "t-phase0"
                       {:op :register-product :patch {:gtin "4902102072618" :name "x"}}
                       {:actor-id "catalog-01" :phase 0})]
      (is (= :hold (:disposition (:state result))))
      (is (empty? (store/catalog-log s))))))
