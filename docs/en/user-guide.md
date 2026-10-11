# User guide and acceptance walkthrough.

## Roles and responsibility.

`INTAKE` uploads originals. `REVIEWER` checks business facts and coding. `APPROVER` authorizes independently. `FINANCE` controls accounting export and ambiguous delivery. `AUDITOR` reads evidence without changing the business process. `ADMIN` manages access and configuration, but cannot bypass separation-of-duties checks merely by being administrator.

A normal invoice requires three different identities: uploader, reviewer, and approver. At or above the snapshotted second-approval threshold, two independent approvers are required, making four distinct participants. The default threshold is EUR 5,000; absolute credit-note values use the same threshold. This is stronger than merely requiring two people overall. Small organizations should confirm that this model is operationally suitable.

## Intake and corrections.

Upload one or more XML files, each at most 2 MiB. Original bytes are encrypted and content-addressed before the case proceeds. Malformed or unsafe input becomes an exception case; it never receives a fabricated valid status. The server uses exact-byte and supplier/number/type duplicate checks. A corrected document is a new linked submission. An eligible earlier case becomes `SUPERSEDED`; its original and history are retained. Approved or exported invoices cannot be silently replaced.

## Supplier verification.

Create the supplier identity from the invoice's tax identifier, registration, endpoint, or fallback identity. Verify the IBAN using a trusted out-of-band source, not the invoice email itself. Another authorized person must verify data entered by its creator. A bank mismatch blocks review and export. Changing supplier data invalidates related approvals and blocks pending jobs. Supplier changes are refused while delivery is in flight or unknown.

## Review and approval.

Inspect validation, buyer reference, purchase-order policy, totals, supplier, and bank data. Enter cost center, ledger account, and a meaningful note. Validation does not establish that goods were supplied or that payment is authorized. Approval captures the policy and supplier versions. Concurrent edits receive a version conflict rather than overwriting newer work. Reject with a reason, or request a corrected submission; do not edit the uploaded XML.

## Accounting and evidence.

Finance queues an idempotent export. `DELIVERED` requires an acknowledged receipt. `UNKNOWN` means the destination outcome must be investigated before any retry. The action is an accounting handoff, not a bank transfer. Evidence downloads contain the original, case JSON, and available current and historical validation reports. Retention holds are recorded controls; this release performs no automatic deletion or legally certified archiving.
