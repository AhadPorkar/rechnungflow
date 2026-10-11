# Security model and threat boundaries.

Treat every invoice, filename, supplier value, and validator report as untrusted. The XML layer disables DTDs, external entities, external schema resolution, and XInclude, and enforces depth, node, attribute, and upload-size limits. Namespace-aware extraction does not trust a matching local element name in a forged namespace. The browser renders user content with escaping rather than executing invoice markup.

Sessions use random secrets stored as hashes, HttpOnly/SameSite cookies, eight-hour absolute and thirty-minute idle expiry. Passwords use PBKDF2-HMAC-SHA256 with 600,000 iterations and unique salts. There is no MFA, OIDC/SSO, password-reset email, or global identity provider. A custom identity subsystem deserves independent review; dependency-free does not mean vulnerability-free. Login limits are process-local and require a correctly configured edge rate limiter for exposed deployments.

The server checks role authorization and tenant ownership independently of hidden interface controls. CSRF and configured-origin checks protect session mutations. Intake keys are scoped, expiring, revocable, and never returned in list responses. Changing a user's password or access invalidates sessions. The last administrator cannot be removed through the API.

Originals, reports, and state use AES-256-GCM. Tenant/path context is authenticated. The key must be stored separately from production data and escrowed separately from snapshots. Encryption at rest does not prevent a compromised application administrator or process from reading authorized data. There is no implemented online key-rotation protocol.

Strict CSP is delivered as an HTTP header; no `unsafe-inline` or `unsafe-eval` is added. Local browser-harness tests cannot establish native CSP enforcement. Native browser checks are provided for CI. Uploaded XML is downloaded as an attachment, not injected as executable markup.

Accounting destinations are fixed by deployment configuration and require HTTPS. This is not a general user-selected URL fetcher. Network egress controls, TLS termination, secret management, malware policy, host patching, dependency review of downloaded KoSIT components, and an independent penetration test remain operator obligations. The local audit chain is tamper-evident against changes, not WORM, externally anchored, or resistant to an attacker replacing an entire state/key set. Never claim automatic GDPR or GoBD compliance.
