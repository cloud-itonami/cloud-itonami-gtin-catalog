# Governance

`cloud-itonami-gtin-catalog` is an OSS open-business blueprint. Governance covers both code
and the operator model.

## Maintainers

Maintainers may merge changes that preserve these invariants:

- the advisor cannot directly commit an unvalidated identifier or product
  record.
- the Product Catalog Governor remains independent of the advisor.
- hard validation/compliance violations cannot be overridden by human
  approval.
- every commit, hold and approval path is auditable.
- real brand/customer/product data stays outside Git.

## Decision Records

Architecture decisions live in `docs/adr/`. Changes to the trust model,
storage contract, public business model, operator certification or license
should add or update an ADR.

## Operator Governance

Anyone may fork and operate independently. itonami.cloud certification is a
separate trust mark and should require security, audit, support and
data-flow review, including proof of the operator's GS1 (or equivalent)
membership status where the function requires one.

Certified operators can lose certification for:

- bypassing governor checks
- mishandling brand or customer data
- misrepresenting certification status
- failing to respond to security incidents
- hiding material changes to customer-facing operation
