(ns gtincatalog.phase
  "Phase 0->3 staged rollout for the product master-data canonicalization
  actor.

    Phase 0  read-only              -- no writes, still governor-gated.
    Phase 1  assisted-registration  -- new canonical products may be
                                       registered, every write needs
                                       human approval.
    Phase 2  assisted-alias         -- adds alias binding, still
                                       approval-gated.
    Phase 3  supervised auto        -- governor-clean, high-confidence
                                       `:register-product`/`:propose-alias`
                                       may auto-commit.

  `:propose-merge`, `:propose-split` and `:flag-catalog-concern` are
  deliberately ABSENT from every phase's `:auto` set, INCLUDING phase 3 --
  a permanent structural fact, not a rollout milestone still to come.

  The reason is asymmetry of repair cost. Registering a redundant
  product or binding one wrong alias is cheap to undo. Merging two
  identities is not: aliases, prices and orders accumulate against the
  survivor, and unwinding it later is far harder than declining it now.
  Anything that changes what a canonical identity MEANS therefore needs
  a human, permanently.

  `gtincatalog.governor`'s own `always-escalate-ops` enforces the same
  invariant independently -- two layers, not one, agree on this."
  (:require [gtincatalog.governor :as governor]))

(def read-ops #{})
(def write-ops governor/allowed-ops)

;; NOTE the invariant: the three identity-changing ops are members of
;; `write-ops` (governor-gated like any write) but are NEVER members of
;; any phase's `:auto` set below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed
  to auto-commit when governor-clean>}."
  {0 {:label "read-only"             :writes #{}                    :auto #{}}
   1 {:label "assisted-registration" :writes #{:register-product}    :auto #{}}
   2 {:label "assisted-alias"        :writes #{:register-product :propose-alias} :auto #{}}
   3 {:label "supervised-auto"       :writes write-ops
      :auto #{:register-product :propose-alias}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE
    (:phase-approval), even if the governor was clean."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a ProductCatalogGovernor verdict to a base disposition before the
  phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
