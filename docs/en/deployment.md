# Deployment configuration.

The default JAR binds `127.0.0.1:8090`. `--demo` is loopback-only and cannot send to external accounting. Create a fresh data directory for STRICT mode. There is no supported conversion of the demo workspace into production.

```cmd
set "RF_WORKSPACE=acme"
set "RF_DATA=C:\RechnungFlowData"
set "RF_PUBLIC_ORIGIN=https://invoices.example.com"
set "RF_MASTER_KEY=<base64-of-32-random-bytes>"
set "RF_ACK_SINGLE_NODE=true"
set "RF_KOSIT_DIR=C:\RechnungFlowTools\kosit"
set "RF_ACCOUNTING_URL=https://accounting.example.com/api/invoices"
set "RF_ACCOUNTING_TOKEN=<deployment-secret>"
set "RF_ACCOUNTING_IDEMPOTENT=true"
java -jar rechnungflow-0.1.0.jar --host 0.0.0.0 --port 8090
```

The example values are placeholders, not credentials. Generate a 32-byte random key using an approved secret-management process. Do not paste secrets into shell history in a real deployment. Keep backend port 8090 accessible only to a trusted TLS reverse proxy. The public origin must exactly match the browser-facing origin; the application does not trust arbitrary forwarded-host headers. TLS terminates at the operator's proxy, not the built-in Java listener.

| Setting | Purpose. |
|---|---|
| `RF_DATA`, `--data` | Persistent encrypted state and document directory. |
| `RF_WORKSPACE` | Workspace identifier created on first strict startup. |
| `RF_MASTER_KEY` | External base64 AES-256 key; same key required on restart. |
| `RF_PUBLIC_ORIGIN` | Exact externally visible origin for mutation checks and secure cookies. |
| `RF_KOSIT_DIR` | Reviewed pinned validator installation. |
| `RF_VALIDATION_TIMEOUT` | Child-process deadline, 2–120 seconds. |
| `RF_ACCOUNTING_IDEMPOTENT` | Explicit attestation of the destination's idempotency contract. |
| `RF_DISABLE_WORKER` | Operator/test switch; queued jobs will not advance while true. |
| `RF_BOOTSTRAP_PASSWORD` | Optional first-start secret; not a password reset mechanism. |

Docker and Compose templates are supplied for a strict single-node deployment behind a proxy. They are not locally executed or penetration-tested evidence. Containers must run non-root with a writable restricted data volume and separately mounted read-only validator inputs. Do not publish the backend directly to the Internet. Review base-image digests and licenses at release time.

There is no HA mode, rolling schema migration, automatic key rotation, SMTP service, cloud provisioning, SSO, malware scanner, or certified external archive. Backups require a stopped instance in this release. Linux local execution was verified; Windows, Docker, Java 25 and customer networking require their own acceptance.
