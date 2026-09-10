(ns gtincatalog.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [gtincatalog.store :as store]))

(def can "gtin.05449000000996")
(def dup "prod.coca-cola-330ml-dup")

(deftest alias-resolution
  (let [s (store/seed-db)]
    (is (= can (store/resolve-canonical s "4902102072618")))
    (is (= can (store/resolve-canonical s can)) "a canonical id resolves to itself")
    (is (nil? (store/resolve-canonical s "unknown-code")))))

(deftest merge-retires-the-loser-rather-than-deleting-it
  (let [s (store/seed-db)]
    (store/commit-record! s {:op :propose-merge :value {:from dup :into can}})
    (testing "the retired record survives, pointing at its winner — the
              history of the identity is not destroyed"
      (let [retired (store/product-record s dup)]
        (is (some? retired))
        (is (= :merged-away (:status retired)))
        (is (= can (:merged-into retired)))))
    (testing "the loser's id still resolves, now to the winner, so an alias
              captured before the merge still leads somewhere"
      (is (= can (store/resolve-canonical s dup))))
    (is (= :active (:status (store/product-record s can))))))

(deftest merge-repoints-existing-aliases
  (let [s (store/mem-store
           {"a" {:product-id "a" :status :active}
            "b" {:product-id "b" :status :active}}
           {"4902102072618" "a"})]
    (store/commit-record! s {:op :propose-merge :value {:from "a" :into "b"}})
    (is (= "b" (store/alias-target s "4902102072618"))
        "an alias that pointed at the retired product now points at the winner")
    (is (= "b" (store/resolve-canonical s "4902102072618")))))

(deftest resolve-canonical-follows-a-chain
  (let [s (store/mem-store
           {"a" {:product-id "a" :status :merged-away :merged-into "b"}
            "b" {:product-id "b" :status :merged-away :merged-into "c"}
            "c" {:product-id "c" :status :active}})]
    (is (= "c" (store/resolve-canonical s "a")))))

(deftest resolve-canonical-terminates-on-a-cycle
  (testing "a merge cycle (which a bug or a hand-edited store could create)
            must terminate rather than hang the actor"
    (let [s (store/mem-store
             {"a" {:product-id "a" :status :merged-away :merged-into "b"}
              "b" {:product-id "b" :status :merged-away :merged-into "a"}})]
      (is (some? (store/resolve-canonical s "a")))
      (is (contains? #{"a" "b"} (store/resolve-canonical s "a"))))))

(deftest resolve-canonical-reports-a-dangling-pointer-as-unresolvable
  (testing "returning the half-way id would hand the caller an identity the
            catalog cannot stand behind"
    (let [s (store/mem-store
             {"a" {:product-id "a" :status :merged-away :merged-into "gone"}})]
      (is (nil? (store/resolve-canonical s "a"))))))

(deftest register-and-alias-commits
  (let [s (store/mem-store {})]
    (store/commit-record! s {:op :register-product
                             :value {:product {:product-id "p1" :name "X" :status :active}}})
    (is (= "X" (:name (store/product-record s "p1"))))
    (store/commit-record! s {:op :propose-alias :value {:code "c1" :product-id "p1"}})
    (is (= "p1" (store/alias-target s "c1")))))

(deftest split-mints-a-new-product-and-moves-the-code
  (let [s (store/seed-db)]
    (store/commit-record! s {:op :propose-split
                             :value {:from can
                                     :code "4902102072618"
                                     :product {:product-id "prod.new-split" :name "Split"
                                               :status :active}}})
    (is (some? (store/product-record s "prod.new-split")))
    (is (= "prod.new-split" (store/alias-target s "4902102072618"))
        "the wrongly-attached code now points at the newly separated product")))

(deftest ledger-is-append-only
  (let [s (store/seed-db)]
    (is (empty? (store/ledger s)))
    (store/append-ledger! s {:t :governor-hold :op :propose-merge})
    (store/append-ledger! s {:t :committed :op :register-product})
    (is (= [:governor-hold :committed] (mapv :t (store/ledger s))))))

(deftest demo-data-actually-exercises-each-conflict-kind
  (testing "the fixtures are not decorative — each names a distinct
            distinguishing attribute the governor must catch"
    (let [s (store/seed-db)
          base (store/product-record s can)]
      (is (= 1 (:pack-count base)))
      (is (= 6 (:pack-count (store/product-record s "prod.coca-cola-330ml-6pack"))))
      (is (= 500 (:net-content (store/product-record s "prod.coca-cola-500ml"))))
      (is (= "zero" (:variant (store/product-record s "prod.coca-cola-330ml-zero"))))
      (testing "and the duplicate agrees on all of them"
        (let [d (store/product-record s dup)]
          (is (= (select-keys base [:net-content :uom :pack-count :variant])
                 (select-keys d [:net-content :uom :pack-count :variant]))))))))
