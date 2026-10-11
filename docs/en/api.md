# API contract and integration.

The machine-readable OpenAPI 3.1.1 document is `invoice-server/src/main/resources/openapi.json`, served to authenticated users at `/api/openapi.json`. It describes 38 operations. Monetary values in extracted invoices are decimal strings; do not convert them to binary floating-point for accounting decisions. Responses identify failures by stable `error.code` and a request identifier.

## Authentication.

Browser sessions use an HttpOnly cookie. Login is `POST /api/auth/login` with `tenant`, `login`, and `password`. The successful response contains `csrf`; every subsequent state-changing session request must send `X-CSRF-Token`. Cross-origin mutation is refused. New users must change their initial password. Never persist session secrets in localStorage.

Administrators can create expiring intake API keys from Team & access. Use `Authorization: Bearer <token>`. Keys can upload and retrieve their own case; they cannot review, approve, export, manage users, or read another actor's cases. Tokens are shown once and hashed at rest. Prefer them to embedding an administrator password in an importer.

## Intake.

```http
POST /api/invoices
Authorization: Bearer <intake-token>
Content-Type: application/xml
X-Filename: supplier-invoice.xml
Idempotency-Key: <unique-key-for-this-submission>

<Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2">...</Invoice>
```

Send raw XML, not multipart or a JSON wrapper. The maximum body is 2 MiB. Reusing an idempotency key with different bytes yields a conflict. A correction uses `?supersedes=<case-id>` and still requires a new immutable document. The key should remain stable across a retry of the same logical submission.

## Versioned actions.

`GET /api/invoices/{id}` returns the current `version`. Send it with `review`, `approve`, `reject`, `comment`, `hold`, `revalidate`, or `export` actions. Review additionally requires `costCenter`, `glAccount`, and `note`. Export also needs an `Idempotency-Key`. `GET .../report?attempt=0` selects the first preserved validation run; omitting `attempt` requests the latest report. Missing reports return a clear error instead of generated evidence.

## Accounting adapter.

The operator configures one HTTPS destination. The outbound JSON includes invoice identity, amounts, coding, source hash, and validation evidence. Its idempotency key is stable. A successful destination must return JSON containing the same `invoiceId` and a non-empty `receiptId`. A 200 response alone is insufficient. Demo jobs never call the external URL. There is no certified DATEV/SAP adapter, automatic payment, inbound webhook, or Peppol endpoint in this release.
