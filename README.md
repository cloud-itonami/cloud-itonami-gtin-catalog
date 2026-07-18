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

**Status: design blueprint, no code implemented yet.** This repository
has zero files under `src/` and no `test/` directory — the
Canonicalization Advisor and Product Catalog Governor described below
do not exist in code. It is not (yet) a governed Advisor⊣Governor
actuation actor; the Core Contract section specifies what that
pipeline is intended to enforce once built, not current behavior. See
[`cloud-itonami-isco-1324`](https://github.com/cloud-itonami/cloud-itonami-isco-1324)
for this fleet's minimal implemented reference (`actor`/`advisor`/
`governor`/`store`), and the `cloud-itonami-assoc-*` /
`cloud-itonami-municipality-*` / `cloud-itonami-lei-*` repos for this
fleet's honest not-an-actuation-actor disclaimer pattern.

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

## Core Contract (design intent — not yet implemented)

```text
raw product record (any code family: GTIN/JAN/UPC/EAN)
        |
        v
Canonicalization Advisor -> Product Catalog Governor -> merge/register, or human review
        |
        v
canonical product record + alias edges + audit ledger
```

**No code exists yet in this repo** -- no `src/`, no `test/`, only
this design document plus `blueprint.edn` and `docs/`. Once built, no
automated match will be able to merge two products into one canonical
identity, or split a pack-size variant into a shared identity, without
Product Catalog Governor clearance -- ambiguous merges will always
escalate to a human reviewer, and every merge/split decision will be
permanently logged. None of this is enforced today.

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
