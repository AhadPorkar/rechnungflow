# Benutzerleitfaden und Abnahmeszenario.

## Rollen und Verantwortung.

`INTAKE` übernimmt Originale. `REVIEWER` prüft Geschäftsvorfall und Kontierung. `APPROVER` genehmigt unabhängig. `FINANCE` steuert Buchhaltungsübergaben und unklare Zustellungen. `AUDITOR` liest Nachweise ohne Änderung des Geschäftsprozesses. `ADMIN` verwaltet Zugänge und Einstellungen, umgeht aber nicht allein durch seine Rolle die Funktionstrennung.

Eine normale Rechnung erfordert drei unterschiedliche Identitäten: Erfassung, Prüfung und Freigabe. Ab einschließlich des gespeicherten zweiten Freigabeschwellwerts werden zwei unabhängige Freigebende benötigt, also insgesamt vier Personen. Standard sind EUR 5.000; bei Gutschriften gilt der absolute Betrag. Das ist strenger als ein bloßes Zwei-Personen-Prinzip. Kleine Organisationen sollten die praktische Eignung prüfen.

## Eingang und Berichtigungen.

Eine oder mehrere XML-Dateien mit jeweils höchstens 2 MiB hochladen. Originalbytes werden verschlüsselt und inhaltsadressiert erhalten. Fehlerhafte oder unsichere Eingaben werden als Ausnahme erfasst und niemals künstlich als gültig markiert. Dublettenprüfung erfolgt sowohl über Dateihash als auch über Lieferant, Nummer und Dokumentart. Eine Berichtigung ist eine neue verknüpfte Einreichung. Ein zulässiger Vorgänger erhält `SUPERSEDED`; Original und Verlauf bleiben erhalten. Freigegebene oder exportierte Rechnungen lassen sich nicht stillschweigend austauschen.

## Lieferantenprüfung.

Lieferantenidentität anhand von Steuerkennung, Registerkennung, Endpunkt oder Ersatzidentität anlegen. Die IBAN über einen unabhängigen vertrauenswürdigen Kanal prüfen, nicht ausschließlich anhand der Rechnungs-E-Mail. Die eigene Dateneingabe darf nicht selbst bestätigt werden. Abweichende Bankdaten blockieren Prüfung und Export. Änderungen entwerten zugehörige Freigaben und blockieren ausstehende Übergaben. Während einer laufenden oder unklaren Zustellung sind solche Änderungen gesperrt.

## Prüfung und Freigabe.

Validierung, Käuferreferenz, Bestellpflicht, Summen, Lieferant und Bankdaten prüfen. Kostenstelle, Sachkonto und aussagekräftige Notiz erfassen. Technische Validität beweist weder Leistungserbringung noch Zahlungsberechtigung. Freigaben beziehen sich auf gespeicherte Richtlinien- und Lieferantenversionen. Paralleländerungen erzeugen Versionskonflikte. Mit Begründung ablehnen oder Berichtigung anfordern, statt das Original-XML zu bearbeiten.

## Buchhaltung und Nachweise.

Finance stellt eine idempotente Übergabe ein. `DELIVERED` setzt eine bestätigte Empfangskennung voraus. Bei `UNKNOWN` zunächst den tatsächlichen Zielzustand untersuchen. Es handelt sich nicht um eine Banküberweisung. Nachweispakete enthalten Original, Fall-JSON und verfügbare aktuelle sowie historische Prüfberichte. Aufbewahrungssperren werden erfasst; automatische Löschung und rechtlich zertifizierte Archivierung sind nicht Bestandteil dieser Version.
