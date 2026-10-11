# Commercial readiness: explicit release gates.

This source is publishable and the local application is runnable. Those facts are not evidence that it is legally certified, production-qualified, universally compatible, or ready to hold customer accounting records without further work. A license permits specified reuse; it does not grant conformance certification or a warranty.

## Implemented foundation.

XML UBL/CII intake, immutable encrypted originals, bounded parsing, selected plausibility rules, official-validator integration boundary, recorded validation history, supplier/IBAN verification, separated review and approvals, policy snapshots, optimistic concurrency, duplicate/correction handling, persistent outbox, sandbox and generic HTTPS accounting, roles, CSRF, expiring intake keys, evidence bundles, retention controls, three-language UI, and offline backup verification are implemented.

## Mandatory acceptance before a commercial deployment.

1. Install genuine pinned KoSIT inputs, run official positive/negative corpora, verify the child API and reports, and document tax/profile coverage. This environment did not execute that acceptance.
2. Review the storage architecture against expected volume and recovery requirements. Migrate to a qualified transactional database and archive design where single-node snapshot limits are unsuitable. Perform measured restore, upgrade, concurrency, and load exercises.
3. Obtain independent security testing, native-browser CSP checks, key-lifecycle design, identity/MFA or SSO integration as required, operational access review, and network hardening.
4. Have qualified German accounting/legal professionals review retention, procedures, tax cases, deletion, privacy, and the actual customer deployment. No GoBD, GDPR, EN 16931, XRechnung, ISO, or BSI certification is asserted.
5. Qualify each accounting destination with its receipt and idempotency contract. No certified DATEV, SAP, Peppol, payment, hybrid-PDF, OCR, or signature capability is present.
6. Verify English, German and Persian wording with native professional reviewers, accessibility against the chosen standard, installer/Windows/container behavior, and support/incident procedures.

## Product roadmap, not existing features.

Recommended next increments are PostgreSQL-backed storage, enterprise identity and MFA, independently verifiable archive anchoring, real validator acceptance automation, reviewed hybrid-PDF support, dedicated ERP adapters, notification delivery, operational metrics/SLOs, and upgrades with migration/recovery rehearsal. AI extraction or autonomous financial actions are deliberately not needed for the core. Keep human accountability and evidence boundaries intact.

A safe public release description is: “Java e-invoice workflow engineering preview with explicit validation and approval evidence.” Do not replace it with “fully compliant invoicing system” before the above evidence exists.
