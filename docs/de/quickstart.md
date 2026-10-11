# RechnungFlow starten.

RechnungFlow 0.1.0 ist eine lauffähige Java-21-Anwendung für den Eingang von XML-Rechnungen, kontrollierte Freigaben und die Übergabe an die Buchhaltung. Diese Engineering-/Pilotversion ist weder ein zertifiziertes Rechnungsarchiv noch ein Zahlungsdienst. Die lokale Demo benötigt keinen externen Dienst.

## Windows-Eingabeaufforderung.

JDK 21 oder neuer und Python ab 3.10 installieren. `java`, `javac`, `jar` und `python` müssen im PATH liegen. Im entpackten Projektverzeichnis ausführen.

```cmd
java -version
javac -version
python --version
scripts\build.cmd
scripts\run-demo.cmd
```

Für die bereitgestellte JAR-Datei genügt Java.

```cmd
java -jar rechnungflow-0.1.0.jar --demo
```

`http://127.0.0.1:8090` öffnen. Beim ersten Start wird das generierte Initialpasswort ausgegeben. Im Arbeitsbereich `demo` stehen die künstlichen Konten `admin`, `intake`, `reviewer`, `approver`, `approver2`, `finance` und `auditor` bereit. Anfangs gilt für sie dasselbe angezeigte Demopasswort. Diese Konfiguration darf nicht für echte Geschäftsvorgänge übernommen werden. Ein Neustart setzt gespeicherte Konten nicht zurück; Zugangsdaten sicher aufbewahren.

EN, DE oder فارسی in der Kopfzeile wählen. Persisch verwendet Rechts-nach-links-Darstellung; technische Kennungen und IBANs bleiben links nach rechts. Datumsangaben verwenden auch auf Persisch den gregorianischen Kalender.

## Erstes Szenario.

`NW-2026-1001` öffnen und Originalhash sowie ausdrücklich gekennzeichneten Demostatus prüfen. Als `reviewer` Kostenstelle, Sachkonto und Prüfnotiz erfassen. Als unabhängiger `approver` freigeben. Als `finance` exportieren; der Hintergrundprozess schreibt in das interne Sandbox-Journal. Das ist keine externe Buchung und keine Zahlung.

Für einen neuen Import eine neue Rechnungsnummer verwenden. Identische Dateien und fachliche Dubletten werden erkannt. Beispiele enthalten CII, eine Gutschrift, inkonsistente Summen, geänderte Bankdaten und unsicheres XML. Sämtliche Dokumente sind fiktive Kandidaten, kein offiziell validierter Referenzbestand.

## Beenden und fortsetzen.

Mit Strg+C beenden und dasselbe `--data`-Verzeichnis erneut verwenden. `master.key` nicht löschen. Demodaten nicht in einen strikten Arbeitsbereich umwandeln. Ein anderes Datenverzeichnis bezeichnet eine separate Instanz. Vor einer Netzwerkfreigabe Bereitstellungs- und Validierungsleitfaden lesen.
