# Teststrategie und Nachweise.

Die primären Offline-Tests im Projektverzeichnis starten.

```cmd
scripts\test.cmd
```

Das Skript kompiliert Produktions- und Testklassen neu und führt Java-Tests für Kern, Eingang, Workflow, Anmeldung, Outbox und Validierung sowie echte HTTP-, Wartungs-, Installer- und Repository-Prüfungen aus. JUnit-XML liegt unter `build/test-results`. Maven oder Netzwerkzugriff sind nicht erforderlich. Ein Fehler beendet den Lauf.

Kernprüfungen umfassen Dezimalwerte, doppelte JSON-Schlüssel, Verschlüsselung, Kontextbindung, Rollback, Persistenz, Sperren, CSV-Formelinjektion und Auditänderungen. Eingangstests prüfen Namensräume, DTD/XXE/XInclude, Grenzen, Summen, CII und Gutschriften. Workflowtests behandeln Zustandswechsel, unzulässige Rollen, Mandanten, Dubletten, Bankänderungen, unabhängige Prüfung, Schwellwerte, alte Versionen, Berichtigungen und Nachweise. Outboxtests verwenden einen echten lokalen HTTP-Dienst für Quittungen, Umleitungen, Zeitüberschreitungen und Idempotenz.

## Browser.

```cmd
python -m pip install -r tests\browser\requirements.txt
python -m playwright install chromium
python tests\browser\ui_test.py
```

Standard ist native Navigation. Nur die ausdrückliche Option `RF_UI_TRANSPORT=harness` rendert die echten Anwendungsdateien und übermittelt Anfragen über eine Testbrücke an den echten Java-Server. Sie wird nie still gewählt und beweist weder native Navigation noch Cookieübertragung, CSP oder Bereitstellungsheader. Entsprechende Sicherheitsprüfungen werden dort explizit übersprungen und müssen nativ in CI bestehen. Jeder Test hat einen eigenen Browserkontext und Fehlerpuffer.

`docs/evidence` dokumentiert tatsächlich lokal ausgeführte Prüfungen. Vorhandene Tests beweisen keine erfolgreichen Läufe auf GitHub, Windows, Java 25, Docker oder Kundensystemen. Echte KoSIT-Abnahme ist ein eigener Befehl und wird bei fehlendem Validator nicht als bestanden gezählt. Abdeckungsquote, Lastwerte, Verfügbarkeit und offizielle Konformität werden nicht erfunden.

## Vor Freigabe.

Sauberen Checkout, alle Tests, echte KoSIT-Fälle, native Zielbrowser, Windows-Pfade, Wiederherstellung, Upgrade und Buchhaltungsziel prüfen. Commit, Umgebung, XML-Berichte und Validatorhashes archivieren. Für Fehler zuerst einen Regressionstest ergänzen. Erwartete Fehlercodes nicht aufweichen, um Zeitrennen zu verstecken.
