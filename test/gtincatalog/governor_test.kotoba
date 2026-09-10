(ns gtincatalog.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [gtincatalog.advisor :as advisor]
            [gtincatalog.governor :as governor]
            [gtincatalog.store :as store]))

(def ctx {:actor-id "catalog-actor" :phase 3})

(defn- db [] (store/seed-db))

(def can  "gtin.05449000000996")          ; 330ml single can
(def six  "prod.coca-cola-330ml-6pack")   ; same liquid, pack-count 6
(def big  "prod.coca-cola-500ml")         ; 500ml
(def dup  "prod.coca-cola-330ml-dup")     ; genuine duplicate of `can`
(def zero "prod.coca-cola-330ml-zero")    ; zero-sugar variant

(defn- advise [op & [patch extra]]
  (advisor/-advise (advisor/mock-advisor) nil
                   (merge {:op op :patch (or patch {})} extra)))

(defn- check [st op & [patch extra]]
  (governor/check (merge {:op op} extra) ctx (advise op patch extra) st))

;; ───────────────────── the characteristic failure mode ─────────────────────

(deftest merging-a-pack-size-variant-is-a-hard-block
  (testing "a 6-pack folded into the identity of a single can is THE
            expensive automated-matching failure — permanently refused,
            not a confidence question"
    (let [v (check (db) :propose-merge {:from six :into can :confidence 0.99})]
      (is (true? (:hard? v)))
      (is (some #{:distinguishing-conflict} (mapv :rule (:violations v))))
      (let [c (:conflicts (first (filter #(= :distinguishing-conflict (:rule %))
                                         (:violations v))))]
        (is (= [:pack-count] (mapv :attribute c)))
        (is (= [6 1] [(:from (first c)) (:into (first c))]))))))

(deftest merging-a-different-net-content-is-a-hard-block
  (let [v (check (db) :propose-merge {:from big :into can :confidence 0.99})]
    (is (true? (:hard? v)))
    (is (= [:net-content]
           (mapv :attribute (:conflicts (first (:violations v))))))))

(deftest merging-a-different-variant-is-a-hard-block
  (testing "zero-sugar is a different trade item even though every size
            attribute agrees"
    (let [v (check (db) :propose-merge {:from zero :into can :confidence 0.99})]
      (is (true? (:hard? v)))
      (is (= [:variant]
             (mapv :attribute (:conflicts (first (:violations v)))))))))

(deftest a-genuine-duplicate-may-be-considered-but-never-automatically
  (testing "every distinguishing attribute agrees, so the governor does not
            HARD-block — but it still escalates, because a merge changes
            what an identity MEANS"
    (let [v (check (db) :propose-merge {:from dup :into can :confidence 0.95})]
      (is (false? (:hard? v)) (pr-str (:violations v)))
      (is (true? (:high-stakes? v)))
      (is (true? (:escalate? v)))
      (is (false? (:ok? v))
          "no confidence value can make an identity merge automatic"))))

(deftest high-confidence-never-rescues-a-conflicting-merge
  (doseq [conf [0.5 0.9 0.99 1.0]]
    (let [v (check (db) :propose-merge {:from six :into can :confidence conf})]
      (is (true? (:hard? v)) (str "confidence " conf))
      (is (false? (:ok? v)) (str "confidence " conf)))))

;; ───────────────────────── other hard checks ─────────────────────────

(deftest invalid-gtin-is-a-hard-block
  (testing "a code that cannot be a GTIN must never enter the alias table"
    (let [v (check (db) :propose-alias {:code "4902102072619"}   ; wrong check digit
                   {:product-id can})]
      (is (true? (:hard? v)))
      (is (some #{:invalid-gtin} (mapv :rule (:violations v))))))
  (testing "a valid GTIN passes"
    (let [v (check (db) :propose-alias {:code "4902102072618"} {:product-id can})]
      (is (false? (:hard? v)) (pr-str (:violations v)))))
  (testing "non-numeric junk is caught by the same check"
    (let [v (check (db) :propose-alias {:code "not-a-code"} {:product-id can})]
      (is (true? (:hard? v)))
      (is (some #{:invalid-gtin} (mapv :rule (:violations v)))))))

(deftest unknown-product-is-a-hard-block
  (doseq [[op patch extra] [[:propose-merge {:from "prod.nope" :into can} nil]
                            [:propose-alias {:code "4902102072618"} {:product-id "prod.nope"}]
                            [:propose-split {:from "prod.nope" :code "4902102072618"} nil]]]
    (let [v (check (db) op patch extra)]
      (is (true? (:hard? v)) (str op))
      (is (some #{:unknown-product} (mapv :rule (:violations v))) (str op)))))

(deftest self-merge-is-refused
  (testing "merging a product into itself would create the very
            :merged-into cycle resolve-canonical has to defend against"
    (let [v (check (db) :propose-merge {:from can :into can :confidence 0.9})]
      (is (true? (:hard? v)))
      (is (some #{:self-merge} (mapv :rule (:violations v)))))))

(deftest effect-must-be-propose
  (let [st (db)
        v (governor/check {:op :register-product} ctx
                          (assoc (advise :register-product {:gtin "5449000000996" :name "x"})
                                 :effect :commit)
                          st)]
    (is (true? (:hard? v)))
    (is (some #{:effect-not-propose} (mapv :rule (:violations v))))))

(deftest op-outside-the-allowlist-is-a-scope-violation
  (let [v (governor/check {:op :delete-product} ctx
                          {:op :delete-product :effect :propose :confidence 0.99}
                          (db))]
    (is (true? (:hard? v)))
    (is (some #{:op-not-allowed} (mapv :rule (:violations v))))))

(deftest scope-exclusion-blocks-completion-claims
  (let [st (db)
        p (advisor/infer nil {:op :propose-merge :patch {:from dup :into can}
                              :out-of-scope? true})
        v (governor/check {:op :propose-merge} ctx p st)]
    (is (true? (:hard? v)))
    (is (some #{:scope-excluded} (mapv :rule (:violations v))))))

(deftest default-mock-advisor-proposals-never-self-trip-scope-exclusion
  (testing "every legitimate proposal talks about merging and canonical
            identity — the excluded terms are phrased as the COMPLETED
            action so the happy path never self-blocks"
    (let [st (db)]
      (doseq [[op patch extra]
              [[:register-product {:gtin "5449000000996" :name "Coca-Cola 330ml Can"} nil]
               [:propose-alias {:code "4902102072618"} {:product-id can}]
               [:propose-merge {:from dup :into can} nil]
               [:propose-split {:from can :code "4902102072618"} nil]
               [:flag-catalog-concern {:concern "duplicate feed"} {:product-id can}]]]
        (let [v (check st op patch extra)]
          (is (not-any? #{:scope-excluded} (mapv :rule (:violations v))) (str op)))))))

;; ───────────────────────── escalation ─────────────────────────

(deftest identity-changing-ops-always-escalate
  (doseq [op [:propose-merge :propose-split :flag-catalog-concern]]
    (is (contains? governor/always-escalate-ops op) (str op))))

(deftest clean-registration-is-ok
  (let [v (check (db) :register-product {:gtin "5449000000996" :name "Coca-Cola 330ml Can"})]
    (is (true? (:ok? v)) (pr-str (:violations v)))
    (is (false? (:escalate? v)))))

(deftest low-confidence-escalates
  (let [st (db)
        v (governor/check {:op :register-product} ctx
                          (assoc (advise :register-product {:gtin "5449000000996" :name "x"})
                                 :confidence 0.3)
                          st)]
    (is (false? (:hard? v)))
    (is (true? (:escalate? v)))))

(deftest hold-fact-carries-the-basis
  (let [st (db)
        v (check st :propose-merge {:from six :into can})
        f (governor/hold-fact {:op :propose-merge :product-id can} ctx v)]
    (is (= :governor-hold (:t f)))
    (is (= [:distinguishing-conflict] (:basis f)))))
