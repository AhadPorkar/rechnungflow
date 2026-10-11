# Validierung: echte Nachweise statt simulierter Konformität.

## Getrennte Entscheidungen.

Parsing erkennt unterstützte XML-Syntax. `BasicRules` prüft ausgewählte Pflichtwerte, Daten, Dezimaldarstellung, Gesamt-, Zeilen- und Steuerbeträge, Dokumentarten sowie IBAN-Prüfsummen. Das ersetzt ausdrücklich nicht die vollständige EN-16931-/XRechnung-Validierung. Sachliche Prüfung, Lieferantenfreigabe, Berechtigung und Annahme durch die Buchhaltung sind eigenständige Entscheidungen.

STRICT verlangt einen tatsächlichen offiziellen PASS. Fehlende Konfiguration, belegte Prozesse, Ausnahmen, Zeitüberschreitung und unbestimmte Annahme blockieren. DEMO erlaubt den synthetischen Ablauf, kennzeichnet fehlende offizielle Prüfung und liefert ausschließlich in die Sandbox. Erfolgreicher Programmlauf bedeutet nicht gültige Rechnung.

## Gepinnter externer Validator.

Zielversionen sind KoSIT **1.6.3** und XRechnung-Konfiguration **2026-08-31**. Quellen stehen im Forschungsregister. `KositWorker` verwendet `Configuration`, `InputFactory`, `DefaultCheck` und `Result` in einer separaten JVM. Nur erfolgreiche Verarbeitung mit `ACCEPTABLE` ergibt PASS. SVRL-Hinweise dienen der Anzeige, nicht der Übersteuerung dieser Entscheidung.

```cmd
python scripts\setup_kosit.py
set "RF_KOSIT_DIR=C:\Projects\rechnungflow\tools\kosit"
python scripts\validate_kosit.py --kosit tools\kosit --input examples\invoices\01-ubl-office.xml --expect PASS
```

Der Installer lädt konkrete offizielle Release-Artefakte, prüft veröffentlichte SHA-256-Werte, weist unsichere ZIP-Einträge ab und protokolliert Konfigurationshashes. Fehlt ein veröffentlichter Hash, ist eine ausdrückliche Betreiberentscheidung mit gesonderter Option nötig. Artefaktadressen werden nicht geraten. Installation ist keine Rechnungsabnahme. PASS beim Beispiel ist eine zu prüfende Erwartung, kein hier bereits erzieltes offizielles Ergebnis.

## Nachweise und Aktualisierung.

Dateihashes werden geprüft, Kindprozesse zeitlich und im Speicher begrenzt, Anwendungsgeheimnisse aus deren Umgebung entfernt und Berichte verschlüsselt erhalten. Neue Prüfungen ergänzen die Historie. Nachweispakete enthalten verfügbare ältere Berichte. Hash-Pinning ist weder Herstellersignatur noch vollständige Lieferkettengarantie.

Die Build-Umgebung konnte echte KoSIT-Binärdateien nicht herunterladen. Offizielle Live-Abnahme und Netzwerkpfad des Installers sind unbestätigt. Testdoubles sind gekennzeichnet und ersetzen keinen Validator. Vor Produktion offizielle positive und negative Testbestände, Steuerfälle, Rundung, Währungen und Windows-Pfade prüfen. Upgrades benötigen erneute Abnahme. Hybride PDF-/ZUGFeRD-Extraktion, Signaturen, Peppol-Transport und sämtliche Rechnungsprofile sind nicht abgedeckt.
