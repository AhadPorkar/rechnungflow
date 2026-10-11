# Forschungsregister.

Recherchedatum: **2026-10-10**. Primärquellen von Gesetzgebern, Standardverantwortlichen, Plattformen und Sicherheitsorganisationen. Ein Eintrag begründet eine Entscheidung oder bezeichnet eine nicht implementierte Funktion, keine Zertifizierung. Öffentliche Dokumentation war zugänglich; Binärdownloads aus dem Build-Container waren blockiert.

Empfangspflicht und Übergangsregeln für die Ausstellung sind zu unterscheiden. Das BMF beschreibt Empfangsfähigkeit ab 2025 und stufenweise Ausnahmen für die Ausstellung in 2026/2027. Konkreter Anwendungsbereich und Ausnahmen müssen aktuell geprüft werden. Die Anwendung trifft keine Rechtsentscheidung. KoSIT 1.6.3 behebt eine URI-Auflösungsschwachstelle; Zielkonfiguration ist 2026-08-31. Technische Gültigkeit beweist weder Geschäftsvorfall noch Zahlungsberechtigung.

Dynamische Quellen können sich ändern. Vor Upgrades Releasehinweise erneut prüfen. Weder offizielle Empfehlung noch Zertifizierung oder Verbandszugehörigkeit wird behauptet.

1. [BMF e-invoice FAQ](https://www.bundesfinanzministerium.de/Content/DE/FAQ/e-rechnung.html). Deutscher Kontext zu Empfang, Ausstellung und Übergängen.

2. [UStG section 14](https://www.gesetze-im-internet.de/ustg_1980/__14.html). Rechnungsrecht; Einzelfallprüfung bleibt erforderlich.

3. [UStG section 14b](https://www.gesetze-im-internet.de/ustg_1980/__14b.html). Aufbewahrungsgrundlage, keine Archivzertifizierung.

4. [KoSIT validator 1.6.3](https://github.com/itplr-kosit/validator/releases/tag/v1.6.3). Gepinnter Sicherheitsrelease; hier nicht heruntergeladen.

5. [XRechnung configuration 2026-08-31](https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/tag/v2026-08-31). Gepinnte Konfiguration; echte Abnahme offen.

6. [KoSIT public API](https://github.com/itplr-kosit/validator/blob/main/docs/api.md). Annahmeentscheidung ist getrennt vom Verarbeitungserfolg.

7. [KoSIT CLI](https://github.com/itplr-kosit/validator/blob/main/docs/cli.md). Bewertet; isolierter API-Prozess gewählt.

8. [KoSIT Result API](https://github.com/itplr-kosit/validator/blob/main/src/main/java/de/kosit/validationtool/api/Result.java). Annahmeempfehlung und Berichtsgrenze.

9. [KoSIT XRechnung project](https://github.com/itplr-kosit/validator-configuration-xrechnung). Konfigurationsrepository und Lizenzprüfung.

10. [FeRD ZUGFeRD](https://www.ferd-net.de/standards/zugferd). Bewertet; hybride PDF-Extraktion fehlt.

11. [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html). Maschinenlesbarer Vertrag, keine Zertifizierung.

12. [OWASP XML external entity prevention](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html). DTD/externe Auflösung deaktivieren, Eingaben begrenzen.

13. [OWASP file upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html). Grenzen für Größe, Name, Typ, Speicher und Anzeige.

14. [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). PBKDF2-HMAC-SHA256-Arbeitsfaktor und Salts.

15. [OWASP CSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html). Token- und Ursprungsprüfung für Änderungen.

16. [OWASP session management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html). Sitzungsablauf, Cookieattribute und Widerruf.

17. [Java 21 HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html). Begrenzter Transport und ausdrückliche Unsicherheit.

18. [Java 21 virtual threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html). Virtuelle Threads mit Anwendungsgrenzen.

19. [Playwright assertions](https://playwright.dev/python/docs/test-assertions). Wiederholende Locatorprüfungen ohne unsichere Auswertung.

20. [GitHub Actions matrix](https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs). Unabhängige Java-/OS-Ergebnisse, kein Fail-Fast.

21. [GitHub Actions security](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions). Minimale Rechte und Releaseprüfung.

22. [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html). Alternative bewertet; Spring Boot wird NICHT verwendet.

23. [GitHub checkout](https://github.com/actions/checkout). Aktuelle dokumentierte Hauptversion; gehostete Ausführung unbestätigt.

24. [GitHub setup-java](https://github.com/actions/setup-java). Aktuelle dokumentierte Hauptversion; gehostete Ausführung unbestätigt.

25. [GitHub setup-python](https://github.com/actions/setup-python). Aktuelle dokumentierte Hauptversion; gehostete Ausführung unbestätigt.

26. [GitHub upload-artifact](https://github.com/actions/upload-artifact). Aktuelle dokumentierte Hauptversion; gehostete Ausführung unbestätigt.

