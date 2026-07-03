# Contributing

`cloud-itonami-gtin-catalog` accepts contributions to the OSS actor, governor tests,
documentation, examples and open business blueprint.

## Development

```bash
clojure -M:dev:test
clojure -M:lint
```

Keep changes small and include tests for governor, validation or audit
behavior.

## Rules

- Do not commit real brand, customer or product master data.
- Keep production writes behind the Product Catalog Governor.
- Treat product master data canonicalization as a data-integrity-sensitive domain: add tests for
  validation correctness, duplicate detection and audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests

PRs should describe:

- what behavior changed
- which governor invariant is affected
- how it was tested
- whether operator or certification docs need updates
