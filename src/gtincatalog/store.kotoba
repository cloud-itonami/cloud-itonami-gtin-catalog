(ns gtincatalog.store
  "SSoT for the independent product master-data (PIM) canonicalization
  actor, behind a `Store` protocol so the backend is a swap, not a
  rewrite -- the same seam every `cloud-itonami-*` actor uses.

  Two directories, both keyed by STRING ids (never keywords -- consistent
  keying from the start, avoiding the silent-miss bug that has plagued
  earlier sibling actors):

    products  canonical product records, keyed by canonical product id
              (`gtin.<14>` / `prod.<slug>`, minted by
              `kotoba.product-party/product-id`). Each carries the
              DISTINGUISHING ATTRIBUTES that make two superficially
              similar records genuinely different trade items --
              `:net-content`, `:uom`, `:pack-count`, `:variant`.
    aliases   alias code -> canonical product id. This is the actual
              product of the service: many raw codes (JAN/UPC/EAN, a
              distributor's internal SKU) resolving to ONE identity.

  The distinguishing attributes are not decoration. Collapsing a
  6-pack into the identity of a single can, or a 500ml bottle into a
  330ml one, is the characteristic and expensive failure mode of
  automated product matching -- a retailer then prices, taxes and ships
  the wrong thing. `gtincatalog.governor` HARD-blocks any merge whose
  two sides disagree on one of these, which is why they live on the
  record rather than being inferred from the product name at merge time.

  The ledger stays append-only: which products a proposal touched, on
  what basis, committed/held/escalated and approved by whom is always a
  query over an immutable log.")

(defprotocol Store
  (product-record [s product-id]
    "Canonical product record, or nil.
     {:product-id .. :name .. :brand .. :gtin ..
      :net-content 500 :uom \"ml\" :pack-count 1 :variant nil
      :status :active|:merged-away :merged-into ..}")
  (all-product-records [s])
  (alias-target [s code] "Canonical product id this alias code resolves to, or nil.")
  (all-aliases [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (catalog-log [s] "the append-only committed-proposal history")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-product-records [s products])
  (with-aliases [s aliases]))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A self-contained catalog covering the happy path and each of the
  governor's own hard checks, so the actor + tests run offline.

    p-cola-330      Coca-Cola 330ml can, single
    p-cola-330-6    the SAME liquid as a 6-pack -- pack-count differs,
                    so merging it into p-cola-330 is a HARD block
    p-cola-500      500ml bottle -- net-content differs, HARD block
    p-cola-330-dup  a genuine duplicate of p-cola-330 from another
                    distributor's feed: every distinguishing attribute
                    agrees, so this is the one merge that may proceed"
  []
  {:products
   {"gtin.05449000000996"
    {:product-id "gtin.05449000000996" :name "Coca-Cola 330ml Can"
     :brand "Coca-Cola" :gtin "05449000000996"
     :net-content 330 :uom "ml" :pack-count 1 :variant nil :status :active}

    "prod.coca-cola-330ml-6pack"
    {:product-id "prod.coca-cola-330ml-6pack" :name "Coca-Cola 330ml Can 6-Pack"
     :brand "Coca-Cola" :gtin nil
     :net-content 330 :uom "ml" :pack-count 6 :variant nil :status :active}

    "prod.coca-cola-500ml"
    {:product-id "prod.coca-cola-500ml" :name "Coca-Cola 500ml Bottle"
     :brand "Coca-Cola" :gtin nil
     :net-content 500 :uom "ml" :pack-count 1 :variant nil :status :active}

    "prod.coca-cola-330ml-dup"
    {:product-id "prod.coca-cola-330ml-dup" :name "COCA COLA CAN 330ML"
     :brand "Coca-Cola" :gtin nil
     :net-content 330 :uom "ml" :pack-count 1 :variant nil :status :active}

    "prod.coca-cola-330ml-zero"
    {:product-id "prod.coca-cola-330ml-zero" :name "Coca-Cola Zero 330ml Can"
     :brand "Coca-Cola" :gtin nil
     :net-content 330 :uom "ml" :pack-count 1 :variant "zero" :status :active}}

   :aliases
   {"4902102072618" "gtin.05449000000996"}})

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (product-record [_ id] (get-in @a [:products id]))
  (all-product-records [_] (sort-by :product-id (vals (:products @a))))
  (alias-target [_ code] (get-in @a [:aliases code]))
  (all-aliases [_] (into (sorted-map) (:aliases @a)))
  (ledger [_] (:ledger @a))
  (catalog-log [_] (:catalog-log @a))
  (commit-record! [_ record]
    (swap! a update :catalog-log conj record)
    (let [{:keys [op value]} record]
      (case op
        :register-product
        (when-let [p (:product value)]
          (swap! a assoc-in [:products (:product-id p)] p))

        :propose-alias
        (when-let [c (:code value)]
          (swap! a assoc-in [:aliases c] (:product-id value)))

        ;; A merge retires the loser rather than deleting it: the retired
        ;; record keeps pointing at its winner, so an alias resolved
        ;; before the merge still leads somewhere and the history of the
        ;; identity is not destroyed.
        ;; NOTE: the winner is bound as `into-id`, never `into` --
        ;; destructuring `{:keys [from into]}` would shadow
        ;; `clojure.core/into`, which the alias re-pointing below calls.
        :propose-merge
        (let [from (:from value)
              into-id (:into value)]
          (swap! a update :products
                 (fn [ps] (cond-> ps
                            (contains? ps from)
                            (update from assoc :status :merged-away :merged-into into-id))))
          (swap! a update :aliases
                 (fn [al] (into {} (map (fn [[k v]] [k (if (= v from) into-id v)]) al))))
          (swap! a assoc-in [:aliases from] into-id))

        :propose-split
        (when-let [p (:product value)]
          (swap! a assoc-in [:products (:product-id p)] p)
          (when-let [c (:code value)]
            (swap! a assoc-in [:aliases c] (:product-id p))))

        nil))
    record)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-product-records [s products]
    (when (seq products) (swap! a assoc :products products)) s)
  (with-aliases [s aliases]
    (when (seq aliases) (swap! a assoc :aliases aliases)) s))

(defn seed-db
  "A MemStore seeded with the demo catalog."
  []
  (->MemStore (atom (assoc (demo-data) :ledger [] :catalog-log []))))

(defn mem-store
  ([products] (mem-store products {}))
  ([products aliases]
   (->MemStore (atom {:products (or products {}) :aliases (or aliases {})
                      :ledger [] :catalog-log []}))))

;; ----------------------------- resolution -----------------------------

(defn resolve-canonical
  "Follow a code (or a product id) to the CURRENT canonical product,
  chasing `:merged-into` pointers.

  Bounded to `max-hops` -- a merge chain that loops (which a bug or a
  hand-edited store could create) must terminate rather than hang the
  actor. When the bound is hit the walk stops and returns where it got
  to, never spinning.

  Returns nil when the chain does not terminate at a real product:
  either the code is unknown, or a `:merged-into` pointer dangles. Both
  are genuinely unresolvable, and reporting the half-way id instead
  would hand the caller an identity the catalog cannot stand behind."
  ([s code] (resolve-canonical s code 16))
  ([s code max-hops]
   (let [start (or (alias-target s code) code)]
     (loop [id start hops 0]
       (let [p (product-record s id)]
         (cond
           (nil? p) nil
           (>= hops max-hops) id
           (and (= :merged-away (:status p)) (:merged-into p))
           (recur (:merged-into p) (inc hops))
           :else id))))))
