# Start RechnungFlow.

RechnungFlow 0.1.0 is a runnable Java 21 application for XML invoice intake, controlled approval, and accounting handoff. It is an engineering/pilot release, not a certified accounting archive or a payment service. No external service is needed for the local demo.

## Windows Command Prompt.

Install JDK 21 or newer and Python 3.10 or newer, make `java`, `javac`, `jar`, and `python` available on PATH, then run from the extracted project root.

```cmd
java -version
javac -version
python --version
scripts\build.cmd
scripts\run-demo.cmd
```

With the distributed application JAR, only Java is needed.

```cmd
java -jar rechnungflow-0.1.0.jar --demo
```

Open `http://127.0.0.1:8090`. The first startup prints the generated bootstrap password. In the `demo` workspace, available synthetic accounts are `admin`, `intake`, `reviewer`, `approver`, `approver2`, `finance`, and `auditor`. They initially share the displayed demo bootstrap password. Do not reuse this arrangement for real operations. Persisted accounts are not reset on a later launch; retain the original credentials securely.

Switch EN, DE, or فارسی in the header. The Persian UI is right-to-left; identifiers and IBANs remain left-to-right. The date calendar remains Gregorian, including Persian formatting.

## First scenario.

Open invoice `NW-2026-1001`. Inspect the original hash and the explicit demo validation status. As `reviewer`, enter a cost center, general-ledger account, and review note. Log in as `approver` and approve independently. As `finance`, request the export; the background worker writes to the internal sandbox ledger. This is not an external accounting posting or payment.

Use a fresh filename/number for a new upload; exact and business-identity duplicates are detected. Examples include CII, a credit note, inconsistent totals, changed bank details, and unsafe XML. All are fictional candidates, not an official acceptance corpus.

## Stop and restart.

Press Ctrl+C. Restart with the same `--data` directory to retain encrypted state. Do not delete `master.key` or reuse demo data for a strict workspace. A different data directory is a separate instance. Consult deployment and validation guides before leaving loopback.
