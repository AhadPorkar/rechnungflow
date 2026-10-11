# Test strategy and evidence.

Run the primary offline suites from the repository root.

```cmd
scripts\test.cmd
```

The script rebuilds production and test classes, then runs Java core, ingest, workflow, authentication, outbox, and validation suites, real HTTP integration, maintenance/recovery, installer safety, and repository checks. Standard JUnit XML is written under `build/test-results`. No Maven test provider or network dependency is required. A failing suite stops the command; it is not treated as a warning.

Core coverage includes exact decimals, duplicate JSON keys, encryption, authenticated context, transaction rollback, persistence, locks, CSV formula injection, and audit mutation. Ingest covers namespaces, DTD/XXE/XInclude, resource bounds, malformed values, tax/line reconciliation, CII, and credit notes. Workflow tests cover each transition, forbidden roles, tenant isolation, duplicate detection, supplier changes, independent review, threshold approvals, stale versions, immutable corrections, retention, and evidence. Export tests use a real loopback HTTP fixture for receipts, redirects, size limits, timeouts, and idempotency contracts.

## Browser suites.

```cmd
python -m pip install -r tests\browser\requirements.txt
python -m playwright install chromium
python tests\browser\ui_test.py
```

Native browser navigation is the default. The optional `RF_UI_TRANSPORT=harness` explicitly renders actual application assets and bridges requests to the real Java server for restricted environments. It is never selected silently. Harness results do not verify native navigation, cookies as transported by the browser, CSP enforcement, or deployment headers. Native security checks are skipped explicitly in harness mode and must pass in GitHub Actions before those claims are made. Browser contexts and page-error collections are isolated per test.

The supplied `docs/evidence` reports record what was actually run locally. Source tests are not proof that hosted CI, Windows, Java 25, Docker, or a customer's infrastructure passed. Optional real KoSIT validation is a separate fail-closed command, not counted as successful when unavailable. No coverage percentage, load result, uptime, or official conformance result is fabricated.

## Before a release.

Run a clean checkout, all suites, genuine KoSIT acceptance, native Chromium and target browsers, Windows paths, backup/restore, upgrade rehearsal, and destination-accounting acceptance. Archive the commit, JDK, OS, test reports, installed validator/configuration hashes, and observed limitations. Add tests for a reported failure before modifying the code. Do not weaken expected status codes to conceal timing defects.
