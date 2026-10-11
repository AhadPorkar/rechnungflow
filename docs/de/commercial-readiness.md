# Kommerzielle Reife: verbindliche Freigabekriterien.

Der Quellcode kann veröffentlicht und die Anwendung lokal ausgeführt werden. Daraus folgt weder rechtliche Zertifizierung noch Produktionsqualifikation, universelle Kompatibilität oder Eignung für Kundendaten ohne weitere Prüfung. Eine Lizenz erlaubt bestimmte Nutzung, ersetzt aber keine Konformitätsbestätigung oder Gewährleistung.

## Implementierte Grundlage.

UBL-/CII-XML-Eingang, verschlüsselte unveränderte Originale, begrenztes Parsing, ausgewählte Plausibilität, Schnittstelle zum offiziellen Validator, Prüfverlauf, Lieferanten-/IBAN-Bestätigung, unabhängige Prüfung und Freigaben, Richtliniensnapshots, Versionsschutz, Dubletten/Berichtigungen, persistente Outbox, Sandbox und HTTPS-Buchhaltung, Rollen, CSRF, Eingangsschlüssel, Nachweispakete, Aufbewahrungskontrollen, drei Sprachen und Offline-Sicherungsprüfung sind implementiert.

## Erforderliche Abnahme.

1. Echte gepinnte KoSIT-Eingaben installieren, offizielle positive und negative Fälle ausführen, API/Berichte prüfen und Steuer-/Profilabdeckung dokumentieren. Diese Umgebung führte das nicht aus.
2. Speichergrenzen gegen Volumen und Wiederherstellungsziele bewerten. Bei Bedarf qualifizierte Transaktionsdatenbank und Archiv einsetzen. Last, Parallelität, Wiederherstellung und Upgrade messen.
3. Unabhängigen Sicherheitstest, native CSP-Prüfung, Schlüsselkonzept, erforderliche MFA/SSO-Anbindung, Betriebsrechte und Netzwerkschutz abschließen.
4. Deutsche Buchhaltungs- und Rechtsfachleute Verfahren, Aufbewahrung, Steuerfälle, Löschung, Datenschutz und konkrete Installation prüfen lassen. GoBD-, DSGVO-, EN-16931-, XRechnung-, ISO- oder BSI-Zertifizierung wird nicht behauptet.
5. Jedes Buchhaltungsziel mit Quittungs- und Idempotenzvertrag qualifizieren. Zertifizierte DATEV-/SAP-, Peppol-, Zahlungs-, Hybrid-PDF-, OCR- und Signaturfunktionen fehlen.
6. Übersetzungen fachlich muttersprachlich prüfen; Barrierefreiheit, Windows, Container und Supportverfahren abnehmen.

## Roadmap, keine vorhandenen Funktionen.

Nächste sinnvolle Schritte sind PostgreSQL, Unternehmensidentität/MFA, externe Archivverankerung, echte automatisierte Validatorabnahme, geprüftes Hybrid-PDF, ERP-Adapter, Benachrichtigungen und messbare Betriebsziele. KI-Extraktion oder autonome Finanzentscheidungen sind für den Kern nicht erforderlich.

Geeignete Veröffentlichung: „Engineering Preview eines Java-E-Rechnungsworkflows mit klar getrennten Validierungs- und Freigabenachweisen.“ „Vollständig konformes Rechnungssystem“ wäre ohne zusätzliche Nachweise nicht gerechtfertigt.
