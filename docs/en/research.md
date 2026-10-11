# Research register.

Research date: **2026-10-10**. These are primary legal, standards-owner, platform, and security sources. Each entry records a design use or an explicit non-implementation boundary, not a certification. Public documentation could be read; downloading validator release binaries from this build container was blocked. Do not confuse those two access levels.

Germany's e-invoice receipt requirements and issuer transition periods must be distinguished. The BMF describes receipt capability from 2025 and phased issuance exceptions into 2026/2027; customer-specific scope and exceptions require current review. The application does not decide legal eligibility. KoSIT 1.6.3 includes a URI-resolution security fix; the targeted XRechnung configuration is dated 2026-08-31. A vendor report of validity does not prove the commercial transaction or authorize payment.

All sources were selected or consulted for this release; dynamic pages can change after this date. Review upstream release notes before upgrading. No certification logo, association, or official endorsement is claimed.

1. [BMF e-invoice FAQ](https://www.bundesfinanzministerium.de/Content/DE/FAQ/e-rechnung.html). German receipt/issuance context and transition distinctions.

2. [UStG section 14](https://www.gesetze-im-internet.de/ustg_1980/__14.html). Invoice law; customer-specific legal review remains necessary.

3. [UStG section 14b](https://www.gesetze-im-internet.de/ustg_1980/__14b.html). Retention baseline, not an archive certification.

4. [KoSIT validator 1.6.3](https://github.com/itplr-kosit/validator/releases/tag/v1.6.3). Pinned security-fix release; binaries not downloaded here.

5. [XRechnung configuration 2026-08-31](https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/tag/v2026-08-31). Pinned configuration; real acceptance outstanding.

6. [KoSIT public API](https://github.com/itplr-kosit/validator/blob/main/docs/api.md). Result acceptance is separate from processing success.

7. [KoSIT CLI](https://github.com/itplr-kosit/validator/blob/main/docs/cli.md). Evaluated; isolated public API worker selected.

8. [KoSIT Result API](https://github.com/itplr-kosit/validator/blob/main/src/main/java/de/kosit/validationtool/api/Result.java). Acceptance recommendation and report boundary.

9. [KoSIT XRechnung project](https://github.com/itplr-kosit/validator-configuration-xrechnung). Configuration repository and licensing review.

10. [FeRD ZUGFeRD](https://www.ferd-net.de/standards/zugferd). Evaluated; hybrid-PDF extraction is not implemented.

11. [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html). Machine-readable HTTP contract, not certification.

12. [OWASP XML external entity prevention](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html). Disable DTD/external resolution and bound input.

13. [OWASP file upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html). Size, name, type, storage and display boundaries.

14. [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). PBKDF2-HMAC-SHA256 work factor and salts.

15. [OWASP CSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html). Session mutation token and origin controls.

16. [OWASP session management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html). Session expiry, cookie flags and revocation.

17. [Java 21 HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html). Bounded outbound transport and explicit uncertainty.

18. [Java 21 virtual threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html). Virtual threads with application concurrency limits.

19. [Playwright assertions](https://playwright.dev/python/docs/test-assertions). Retrying locator assertions without unsafe evaluation.

20. [GitHub Actions matrix](https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs). Independent Java/OS results, fail-fast disabled.

21. [GitHub Actions security](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions). Least permissions and release review.

22. [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html). Evaluated alternative; Spring Boot is NOT used.

23. [GitHub checkout](https://github.com/actions/checkout). Current documented major used in CI; hosted execution remains unverified.

24. [GitHub setup-java](https://github.com/actions/setup-java). Current documented major used in CI; hosted execution remains unverified.

25. [GitHub setup-python](https://github.com/actions/setup-python). Current documented major used in CI; hosted execution remains unverified.

26. [GitHub upload-artifact](https://github.com/actions/upload-artifact). Current documented major used in CI; hosted execution remains unverified.

