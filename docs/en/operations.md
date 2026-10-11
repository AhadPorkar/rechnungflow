# Operations, backup, and recovery.

Use a dedicated OS account and an encrypted data volume. Store the external AES key in a managed secret store, not in the repository or a support ticket. The default local key file is for a controlled local installation. A lost key makes encrypted state and documents unrecoverable.

## Offline verification and snapshot.

Stop the application before maintenance. The data lock intentionally prevents inconsistent live copying.

```cmd
java -cp build\rechnungflow-0.1.0.jar io.ahad.rechnungflow.server.Maintenance verify var
java -cp build\rechnungflow-0.1.0.jar io.ahad.rechnungflow.server.Maintenance snapshot var C:\Backups\rechnungflow-2026-10-10
```

The target must not exist. Verification authenticates the state, every referenced original/report, and audit chains. Snapshot copies encrypted state and referenced blobs plus a hash manifest. The master key is deliberately excluded. Escrow it separately and verify both recovery paths. Keep the snapshot inaccessible to unrelated users.

Restore into a new empty directory. Supply the same key through `RF_MASTER_KEY`, or restore the separately escrowed `master.key` under appropriate permissions. Run `Maintenance verify` on the restored directory before starting the application. Retain the original backup until a documented rehearsal proves originals, user access, approval state, and export recovery. Missing blob files and wrong keys must fail, not produce an empty new workspace.

## Unknown accounting delivery.

If a worker crashes after dispatch, the restarted application marks the in-flight job `UNKNOWN`. Do not press retry based solely on a timeout. Search the destination using the stable invoice identity and idempotency key. Finance records either confirmed delivery with receipt, or confirmed non-delivery with an investigation reason. `FAILED` permits an explicit investigated retry. The original invoice is not altered during reconciliation.

## Retention and audit.

The policy defaults to eight years from the end of the issue year and allows a longer configured period. This is an implementation default informed by German invoice-retention rules, not individualized legal advice. Holds are recorded, there is no automatic purge, and extending a policy does not silently rewrite existing invoice snapshots. External archive design, retention changes, legal holds, deletion, and data-subject requests need a reviewed operating procedure.

Monitor process health, disk capacity, encrypted-state size, exception backlog, pending/unknown jobs, and failed logins. `/health/live` is liveness, not proof of validator readiness. `/api/system` exposes authenticated capability information. No production SLO, RPO, RTO, throughput, or uptime has been certified or benchmarked here.
