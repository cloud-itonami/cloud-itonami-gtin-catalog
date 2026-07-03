# Business Model: Independent Product Master Data & Canonicalization Service

## Classification

- Repository: `cloud-itonami-gtin-catalog`
- Domain: product-identity / catalog canonicalization (function-keyed, not
  code-keyed -- see README)
- Social impact: retailers/distributors keep product-data sovereignty
  instead of renting a closed PIM SaaS

## Customer

- small-to-mid retailers and distributors without an enterprise PIM budget
- multi-channel sellers needing one canonical product record across
  marketplaces
- co-ops and buying groups sharing a product catalog across members

## Offer

- canonical product identity resolution across GTIN/JAN/UPC/EAN aliases
- brand ownership resolution and duplicate-brand merging
- pack-size-aware product splitting (same brand/model, different pack size
  = different canonical product)
- governed merge/split workflow with human review for ambiguous cases
- canonical-catalog export to downstream retail/e-commerce systems

## Revenue

- self-host setup and integration fee
- managed hosting subscription per catalog size
- data-quality audit and cleanup package for legacy catalogs
- buying-group shared-catalog hosting

## Trust Controls

- every merge/split requires Product Catalog Governor clearance
- ambiguous identity claims (conflicting pack size, disputed brand
  ownership) always escalate to human review
- every canonicalization decision is logged
- public catalog-size/accuracy claims must reference the audit ledger
