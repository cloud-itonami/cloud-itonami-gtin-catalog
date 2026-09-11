# cloud-itonami-gtin-catalog

Open Business Blueprint: **Independent Product Master Data &
Canonicalization Service**.

This repository designs a forkable OSS business for an independent
operator who runs a product master-data (PIM) service: canonicalizing
GTIN/JAN/UPC/EAN aliases to one product identity, resolving brand
ownership, and serving clean product data to downstream retail/e-commerce
systems -- the OSS-operator counterpart to
[`com-etzhayyim-gtin`](https://github.com/etzhayyim/com-etzhayyim-gtin)'s
own canonical-product-identity function, so a retailer or distributor can
self-host instead of paying a closed PIM SaaS.

**Status: implemented governed actor.** The Canonicalization Advisor and
Product Catalog Governor now exist in code
(`src/gtincatalog/`, 34 tests / 114 assertions), built on this
workspace's [`langgraph`](https://github.com/kotoba-lang/langgraph)
StateGraph runtime — the same **Advisor ⊣ Governor** pattern as every
implemented actor in this fleet. Implemented under
[ADR-2607264000](https://github.com/com-junkawasaki/root/blob/main/90-docs/adr/2607264000-marketplace-federated-commerce-layer.edn),
which needed a real canonical catalog underneath the federated
marketplace layer. Prior revisions of this file said no code existed;
that is no longer true.

## Why this is not code-keyed like ISIC/ISCO/COFOG/UNSPSC blueprints

GTIN is an **identifier system**, not a classification taxonomy (see
ADR-2607031800). `cloud-itonami-gtin-*` splits by FUNCTION instead: this
repo (catalog), plus
[`cloud-itonami-gtin-issuance`](https://github.com/cloud-itonami/cloud-itonami-gtin-issuance)
and
[`cloud-itonami-gtin-verification`](https://github.com/cloud-itonami/cloud-itonami-gtin-verification).

## No robotics premise

This is a pure data-service business (product/brand/alias canonicalization,
matching, deduplication) -- the same digital/data-service exemption class
as [`cloud-itonami-6310`](https://github.com/cloud-itonami/cloud-itonami-6310)
(HR SaaS replacement).

## Core Contract (implemented)

```text
raw product record (any code family: GTIN/JAN/UPC/EAN)
        |
        v
Canonicalization Advisor -> Product Catalog Governor -> merge/register, or human review
        |
        v
canonical product record + alias edges + audit ledger
```

This is now enforced, not aspirational. No automated match can merge two
products into one canonical identity, or collapse a pack-size variant
into a shared identity, without Product Catalog Governor clearance;
every merge/split decision is permanently logged.

### The characteristic failure mode this governor exists to stop

Automated product matching fails in one expensive, specific way:
collapsing records that differ on a **distinguishing attribute** — a
6-pack folded into the identity of a single can, a 500ml bottle into a
330ml one, a zero-sugar variant into the sugared one. Downstream a
retailer then prices, taxes and ships the wrong thing, and unwinding a
merge after aliases have accumulated against it is far harder than
refusing it.

So `net-content` / `uom` / `pack-count` / `variant` are compared **from
the two stored product records**, never from the proposal's own claim
that they match, and any disagreement is a HARD, permanent block —
never a confidence question. Deliberately a small closed list of facts
about the goods rather than a similarity score: a threshold on a score
is exactly the mechanism that lets a confident-enough model collapse a
6-pack into a single can.

A conflicting merge routes straight to `:hold` and is **never offered
for approval** — putting it in front of a tired reviewer would invite
exactly the failure this actor exists to prevent.

### Five HARD checks (permanent, un-overridable)

| Check | What it catches |
|---|---|
| **Invalid GTIN** | a code failing the GS1 mod-10 check digit — delegated to `kotoba.product-party`, one GS1 implementation in the workspace, not two |
| **Unknown product** | a merge/split/alias target that does not exist in the store |
| **Distinguishing conflict** | a merge whose two sides disagree on net content / uom / pack count / variant |
| **Self-merge** | merging a product into itself, which would create the very `:merged-into` cycle `resolve-canonical` defends against |
| **Effect not `:propose`** | a proposal claiming to directly actuate outside governance |

plus scope exclusion: any claim to have *already* merged/decided, and
any op outside the closed allowlist.

### A human always decides what an identity means

`:propose-merge`, `:propose-split` and `:flag-catalog-concern` are absent
from **every** phase's `:auto` set including phase 3, and the governor
marks them high-stakes independently — two layers, not one. Registering
a redundant product or binding one wrong alias is cheap to undo; merging
two identities is not, because aliases, prices and orders accumulate
against the survivor.

A merge retires the loser rather than deleting it: the retired record
keeps pointing at its winner, so an alias captured before the merge
still resolves and the history of the identity survives.

```bash
kbb -M:dev:run   # clean registration, HARD pack-size block, human-gated merge
kbb -M:test      # 34 tests, 114 assertions
kbb -M:lint
```

## Required capabilities

- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Product ↔ party join

Catalog operators that need to attach brand-owners / manufacturers /
merchants to GTINs should use the portable join contract
[`kotoba-lang/product-party`](https://github.com/kotoba-lang/product-party)
(ADR-2607106100) and the runtime facade `cloud-itonami.product-party` in
`gftdcojp/cloud-itonami`. This blueprint remains the OSS product-master
*service* design; the join lib is the edge graph, not a second PIM.

## License

AGPL-3.0-or-later.
