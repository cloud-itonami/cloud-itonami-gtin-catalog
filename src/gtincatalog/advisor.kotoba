(ns gtincatalog.advisor
  "CanonicalizationAdvisor -- the *contained intelligence node* for the
  independent product master-data (PIM) canonicalization actor.

  It drafts exactly five kinds of proposal from a closed allowlist:
  registering a new canonical product, binding an alias code to an
  existing one, merging two canonical products, splitting a conflated
  identity apart again, and flagging a data-quality concern.

  CRITICAL: it is a smart-but-untrusted advisor. It returns a *proposal*
  (with a rationale + the fields it cited), never a committed record and
  NEVER a direct actuation -- every proposal's `:effect` is always
  `:propose`. Every output is censored downstream by
  `gtincatalog.governor` before anything touches the SSoT.

  Matching is exactly the task where a confident model is most dangerous:
  two records that read alike may be a 6-pack and a single can. The
  advisor may therefore SUGGEST a merge with whatever confidence it
  likes; the governor compares the two records' distinguishing
  attributes itself and HARD-blocks a conflict regardless of that
  confidence. The advisor's similarity opinion never decides an
  identity.

  Like every sibling actor's advisor this is a deterministic mock so the
  actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (or a classical
  record-linkage model) with the same proposal shape."
  (:require [kotoba.product-party :as pp]))

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-register
  "Draft a new canonical product record from a raw incoming record."
  [_db {:keys [patch]}]
  (let [gtin (:gtin patch)
        pid  (or (:product-id patch)
                 (when gtin (pp/product-id {:gtin gtin}))
                 (:fallback-id patch))]
    {:op         :register-product
     :product-id pid
     :summary    (str "新規正規商品として登録を提案: " (pr-str (:name patch)))
     :rationale  "既存の正規商品に一致する候補が見つからないため、新規識別子の発行を提案する。既存識別子との統合は行わない。"
     :cites      (vec (keep identity [gtin (:name patch)]))
     :effect     :propose
     :value      {:product (merge {:product-id pid :status :active} patch)}
     :confidence 0.9}))

(defn- propose-alias
  "Draft binding a raw code to an EXISTING canonical product."
  [_db {:keys [product-id patch]}]
  {:op         :propose-alias
   :product-id product-id
   :summary    (str (:code patch) " を " product-id " のエイリアスとして紐付けを提案")
   :rationale  "同一取引商品を指す別コードとしての紐付け提案のみ。正規識別子そのものは変更しない。"
   :cites      (vec (keep identity [(:code patch) product-id]))
   :effect     :propose
   :value      {:code (:code patch) :product-id product-id}
   :confidence (or (:confidence patch) 0.87)})

(defn- propose-merge
  "Draft merging `from` into `into`. ALWAYS escalates; and if the two
  records disagree on a distinguishing attribute the governor HARD-blocks
  it no matter what confidence appears here."
  [_db {:keys [patch]}]
  {:op         :propose-merge
   :product-id (:into patch)
   :summary    (str (:from patch) " を " (:into patch) " へ統合する候補を提示")
   :rationale  "同一取引商品の重複登録である可能性が高いという候補提示のみ。識別属性の突合と最終判断は人間が行う。"
   :cites      (vec (keep identity [(:from patch) (:into patch)]))
   :effect     :propose
   :value      {:from (:from patch) :into (:into patch)
                :evidence (:evidence patch)}
   :confidence (or (:confidence patch) 0.72)})

(defn- propose-split
  "Draft splitting a conflated identity: a new canonical product is
  minted and a code that was wrongly pointing at `from` moves to it."
  [_db {:keys [patch]}]
  {:op         :propose-split
   :product-id (:from patch)
   :summary    (str (:from patch) " に混在している別商品の分離候補を提示")
   :rationale  "1つの正規識別子に別商品が混在している疑いの提示のみ。分離の実行可否は人間が判断する。"
   :cites      (vec (keep identity [(:from patch) (:code patch)]))
   :effect     :propose
   :value      {:from (:from patch) :code (:code patch)
                :product (:product patch)}
   :confidence (or (:confidence patch) 0.68)})

(defn- propose-catalog-concern
  "Surface an observed data-quality/ambiguity concern for HUMAN triage.
  ALWAYS escalates -- never auto-committed at any phase. Reports the
  OBSERVATION only, so the default rationale never trips
  `scope-excluded-terms`."
  [_db {:keys [product-id patch]}]
  {:op         :flag-catalog-concern
   :product-id product-id
   :summary    (str product-id " のカタログ品質に関する懸念フラグ: "
                    (pr-str (:concern patch "unknown")))
   :rationale  "観察されたデータ品質・曖昧性の事実報告のみ。同一性の判断や統合の実施は行わない。"
   :cites      [product-id]
   :effect     :propose
   :value      (merge {:product-id product-id} patch)
   :confidence (or (:confidence patch) 0.8)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :register-product     (propose-register db request)
                   :propose-alias        (propose-alias db request)
                   :propose-merge        (propose-merge db request)
                   :propose-split        (propose-split db request)
                   :flag-catalog-concern (propose-catalog-concern db request)
                   {})]
    ;; Test hook: inject scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared
    ;; before production use.
    (if out-of-scope?
      (update proposal :rationale str
              " -- actually merged the products and authoritatively determined they are one item")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t          :advisor-proposal
   :op         (:op proposal)
   :product-id (:product-id proposal)
   :summary    (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
