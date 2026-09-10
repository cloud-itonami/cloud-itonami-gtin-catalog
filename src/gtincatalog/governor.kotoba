(ns gtincatalog.governor
  "ProductCatalogGovernor -- the independent compliance layer that earns
  the CanonicalizationAdvisor the right to commit.

  The advisor has no notion of whether two records that LOOK alike are
  actually the same trade item, whether a code it was handed is even a
  valid GTIN, whether the products it wants to merge exist, or whether
  its own `:effect` secretly claims a direct actuation. So this MUST be
  a separate system able to *reject* a proposal and fall back to HOLD.

  ## The characteristic failure mode this governor exists to stop

  Automated product matching fails in one expensive, specific way:
  collapsing records that differ on a DISTINGUISHING ATTRIBUTE. A 6-pack
  folded into the identity of a single can, a 500ml bottle into a 330ml
  one, a zero-sugar variant into the sugared one. Downstream, a retailer
  then prices, taxes and ships the wrong thing, and unwinding a merge
  after aliases have accumulated against it is far harder than refusing
  it. So a merge whose two sides disagree on net content, unit of
  measure, pack count or variant is a HARD, permanent block -- never a
  confidence question. This is exactly the guarantee this repository's
  README has promised since it was design-only: *no automated match will
  merge two products into one canonical identity, or collapse a
  pack-size variant into a shared identity, without governor clearance.*

  Five HARD checks, ALL permanent, un-overridable by any human approval:

    1. Invalid GTIN            -- every code named by the proposal must
                                  pass `kotoba.product-party`'s GS1
                                  mod-10 check digit. A code that cannot
                                  be a GTIN must never enter the alias
                                  table, because everything downstream
                                  trusts that table.
    2. Unknown product         -- merge/split/alias targets must exist in
                                  the store. Never trusts the proposal's
                                  own id claims without a lookup -- the
                                  'ground truth, not self-report'
                                  discipline every sibling governor uses.
    3. Distinguishing conflict -- a `:propose-merge` whose two sides
                                  disagree on net-content / uom /
                                  pack-count / variant. HARD, permanent.
                                  Re-derived from the two PRODUCT
                                  RECORDS, never from the proposal's own
                                  claim that they match.
    4. Effect not :propose     -- any other value is a claim to directly
                                  actuate outside governance.
    5. Scope exclusion         -- any proposal claiming to have already
                                  merged/decided an identity, plus any
                                  op outside the closed allowlist.

  Two ESCALATE (SOFT) gates, either forces human sign-off:
    - LLM confidence below the floor.
    - The op is `:propose-merge`, `:propose-split` or
      `:flag-catalog-concern` -- ALWAYS escalates, regardless of
      confidence. `gtincatalog.phase` independently keeps all three out
      of every phase's `:auto` set -- two layers, not one.

  A merge that passes check 3 is therefore still never automatic; check
  3 separates 'refuse outright' from 'a human may consider it', which is
  the distinction the README's 'ambiguous merges always escalate'
  promise actually requires."
  (:require [kotoba.lang.text :as str]
            [gtincatalog.store :as store]
            [kotoba.product-party :as pp]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist."
  #{:register-product :propose-alias :propose-merge :propose-split
    :flag-catalog-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not. Every op that
  changes what a canonical identity MEANS is in here."
  #{:propose-merge :propose-split :flag-catalog-concern})

(def distinguishing-attributes
  "The attributes that make two superficially similar records genuinely
  different trade items. Disagreement on ANY of them makes a merge a
  HARD block.

  Deliberately a small, explicit, closed list rather than a similarity
  score: a threshold on a score is exactly the mechanism that lets a
  confident-enough model collapse a 6-pack into a single can. These are
  facts about the goods, and they either match or they do not."
  [:net-content :uom :pack-count :variant])

(def scope-excluded-terms
  "Case-insensitive substrings marking a proposal as claiming an
  authority this actor lacks.

  CRITICAL: every term is phrased as the COMPLETED action ('merged the
  products'), never a bare noun like 'merge' or 'canonical identity' --
  a bare noun would match inside this actor's own legitimate merge
  PROPOSALS (whose whole job is to talk about merging) and self-block
  the happy path. See
  `gtincatalog.governor-test/default-mock-advisor-proposals-never-self-trip-scope-exclusion`."
  ["merged the products" "merged the records" "have merged the"
   "already merged" "completed the merge" "finalized the merge"
   "split the identity" "have split the" "completed the split"
   "deduplicated the catalog" "deduplicated the records"
   "authoritatively determined" "authoritatively resolved"
   "confirmed these are the same product" "confirmed they are the same trade item"
   "統合を完了した" "統合を確定した" "既に統合した" "名寄せを完了した"
   "同一商品と確定した" "同一と断定した" "分割を完了した"])

;; ----------------------------- checks -----------------------------

(defn- proposal-codes
  "Every raw code the proposal wants to bind into the alias table."
  [proposal]
  (keep identity
        [(get-in proposal [:value :code])
         (get-in proposal [:value :gtin])
         (get-in proposal [:value :product :gtin])]))

(defn- invalid-gtin-violations
  "Every code named by the proposal must pass the GS1 mod-10 check
  digit. Delegated to `kotoba.product-party/valid-gtin?` rather than
  reimplemented -- one GS1 implementation in the workspace, not two.

  A non-numeric or wrong-length code is caught by the same call
  (`normalize-gtin` yields something `valid-gtin?` rejects)."
  [proposal]
  (seq
   (for [c (proposal-codes proposal)
         :let [norm (pp/normalize-gtin (str c))]
         :when (not (and norm (pp/valid-gtin? norm)))]
     {:rule :invalid-gtin
      :detail (str c " は GS1 mod-10 チェックディジットを満たさない -- エイリアス表に入れられない")})))

(defn- unknown-product-violations
  "Merge/split/alias targets must exist in the store."
  [proposal st]
  (let [op (:op proposal)
        ids (case op
              :propose-merge [(get-in proposal [:value :from]) (get-in proposal [:value :into])]
              :propose-alias [(get-in proposal [:value :product-id])]
              :propose-split [(get-in proposal [:value :from])]
              [])]
    (seq
     (for [id ids
           :when (not (store/product-record st id))]
       {:rule :unknown-product
        :detail (str (or id "(product-id missing)") " はカタログに存在しない正規商品")}))))

(defn- distinguishing-conflicts
  "The attributes on which two product records genuinely disagree.
  Returns a seq of `{:attribute k :from v :into v}`.

  Compared from the two RECORDS, never from the proposal's own claim
  that they match -- an advisor cannot assert its way past a pack-size
  difference."
  [a b]
  (for [k distinguishing-attributes
        :let [va (get a k) vb (get b k)]
        :when (not= va vb)]
    {:attribute k :from va :into vb}))

(defn- merge-conflict-violations
  "HARD block for a `:propose-merge` whose sides differ on a
  distinguishing attribute. Skipped when either side is unknown --
  `unknown-product-violations` already reports that, and reporting both
  would bury the real cause."
  [proposal st]
  (when (= :propose-merge (:op proposal))
    (let [from (store/product-record st (get-in proposal [:value :from]))
          into* (store/product-record st (get-in proposal [:value :into]))]
      (when (and from into*)
        (when-let [conflicts (seq (distinguishing-conflicts from into*))]
          [{:rule :distinguishing-conflict
            :detail (str "識別属性が一致しないため統合できない: "
                         (str/join ", "
                                   (map #(str (name (:attribute %))
                                              " " (pr-str (:from %))
                                              " ≠ " (pr-str (:into %)))
                                        conflicts)))
            :conflicts (vec conflicts)}])))))

(defn- self-merge-violations
  "Merging a product into itself is a no-op that would nonetheless
  retire the record and point it at itself, creating the exact
  `:merged-into` cycle `store/resolve-canonical` has to defend against.
  Refuse it at the source."
  [proposal]
  (when (= :propose-merge (:op proposal))
    (let [{:keys [from into]} (:value proposal)]
      (when (and from (= from into))
        [{:rule :self-merge :detail (str from " を自身に統合することはできない")}]))))

(defn- effect-not-propose-violations
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- text-blob [proposal]
  (str/lower (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block, evaluated UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "統合・分割を既に実施した/同一と確定した等の確定行為に触れる提案は永久に禁止"}])))

(defn check
  "Censors a CanonicalizationAdvisor proposal. Returns
  {:ok? bool :violations [..] :confidence c :escalate? bool
   :high-stakes? bool :hard? bool}."
  [_request _context proposal store]
  (let [hard (into []
                   (concat (invalid-gtin-violations proposal)
                           (unknown-product-violations proposal store)
                           (self-merge-violations proposal)
                           (merge-conflict-violations proposal store)
                           (effect-not-propose-violations proposal)
                           (scope-exclusion-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :product-id (:product-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
