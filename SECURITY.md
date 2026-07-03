# Security Policy

This project handles product master data canonicalization workflows, including brand and product master
data. Treat vulnerabilities as potentially high impact even when the demo
data is synthetic.

## Do Not Disclose Publicly

Report privately before opening public issues for:

- credential exposure
- real brand, customer or product master data exposure
- authorization bypass
- Product Catalog Governor bypass
- audit-ledger tampering
- a path that lets an invalid or duplicate identifier commit without
  governor review

## Reporting

Use GitHub private vulnerability reporting when available for the
repository. If that is unavailable, contact the repository maintainers
through the cloud-itonami organization before publishing details.

Include:

- affected commit or version
- reproduction steps
- expected and actual behavior
- impact on product data, governor enforcement or audit logging
- suggested fix, if known

## Production Guidance

- Store secrets outside Git.
- Keep real brand/customer/product data outside this repository.
- Run governor tests before deployment.
- Export and review audit logs regularly.
- Use least privilege for operators and service accounts.
