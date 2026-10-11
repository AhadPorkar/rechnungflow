# Validation: official evidence, not simulated compliance.

## Separate decisions.

Parsing identifies a supported XML syntax. `BasicRules` checks selected required values, dates, decimal lexical forms, totals, line and tax sums, invoice types, and IBAN checksums. These checks are deliberately not an implementation or certification of the complete EN 16931/XRechnung specification. Commercial review, supplier verification, authorization, and accounting acceptance remain different decisions.

STRICT mode requires an actual official-tool PASS. Missing configuration, busy workers, exceptions, timeouts, or undefined acceptance fail closed. DEMO mode permits the synthetic walkthrough but labels the absent official result and restricts delivery to the sandbox. An engine's successful execution is not the same as invoice acceptance.

## Pinned external engine.

The integration targets KoSIT **1.6.3** and the XRechnung configuration **2026-08-31**. Sources and security rationale are in the research register. `KositWorker` uses KoSIT's `Configuration`, `InputFactory`, `DefaultCheck`, and `Result` API in a child JVM. Only successful processing with `ACCEPTABLE` becomes PASS. Diagnostics are extracted from SVRL for display, never used to override the Result decision.

```cmd
python scripts\setup_kosit.py
set "RF_KOSIT_DIR=C:\Projects\rechnungflow\tools\kosit"
python scripts\validate_kosit.py --kosit tools\kosit --input examples\invoices\01-ubl-office.xml --expect PASS
```

The installer downloads explicit official release assets, checks published SHA-256 when supplied, rejects unsafe ZIP entries, and records all local configuration hashes. Missing published digests require explicit operator review and the separately named opt-in flag. It never guesses an asset URL. Installation is not invoice acceptance. The sample's PASS is an expectation to test, not an assertion that this environment obtained an official PASS.

## Evidence and upgrade.

The application verifies installed file hashes, bounds child memory/time/concurrency, strips application secrets from its environment, and preserves report bytes encrypted. Revalidation appends evidence rather than replacing previous runs. An evidence ZIP includes available historical reports. Hash pinning detects changes to reviewed local inputs; it is not a vendor signature or supply-chain guarantee.

Actual KoSIT binaries could not be downloaded in the build environment. Official live acceptance and installer network execution remain unverified. Test doubles are explicitly labeled and never bundled as a real validator. Before production, run positive/negative official corpora, additional tax regimes, currency/rounding cases, Windows path cases, and archive each report with version/hash metadata. New releases require new acceptance, not an automatic unreviewed upgrade. Hybrid PDF/ZUGFeRD extraction, signatures, Peppol transport, and all invoice profiles are outside this release.
