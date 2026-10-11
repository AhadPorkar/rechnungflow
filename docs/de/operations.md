# Betrieb, Sicherung und Wiederherstellung.

Ein dediziertes Betriebssystemkonto und ein verschlüsseltes Datenvolume verwenden. Externe AES-Schlüssel gehören in eine Secret-Verwaltung, nicht ins Repository oder Supportticket. Die lokale Schlüsseldatei ist für kontrollierte lokale Installationen gedacht. Ein verlorener Schlüssel macht die verschlüsselten Daten unlesbar.

## Offline-Prüfung und Snapshot.

Vor Wartung die Anwendung beenden. Die Prozesssperre verhindert absichtlich inkonsistente Kopien im laufenden Betrieb.

```cmd
java -cp build\rechnungflow-0.1.0.jar io.ahad.rechnungflow.server.Maintenance verify var
java -cp build\rechnungflow-0.1.0.jar io.ahad.rechnungflow.server.Maintenance snapshot var C:\Backups\rechnungflow-2026-10-10
```

Das Ziel darf nicht existieren. Geprüft werden Zustand, referenzierte Originale und Berichte sowie Auditketten. Der Snapshot enthält verschlüsselte Daten und ein Hashmanifest, ausdrücklich keinen Hauptschlüssel. Den Schlüssel getrennt verwahren und dessen Wiederherstellung ebenfalls testen.

In ein neues leeres Verzeichnis zurückspielen. Denselben Schlüssel über `RF_MASTER_KEY` bereitstellen oder die getrennt verwahrte `master.key` mit geeigneten Dateirechten wiederherstellen. Vor dem Start `Maintenance verify` ausführen. Die ursprüngliche Sicherung behalten, bis ein dokumentierter Test Originale, Zugang, Freigaben und Exportwiederherstellung bestätigt. Fehlende Dateien oder falsche Schlüssel dürfen keinen scheinbar leeren neuen Arbeitsbereich erzeugen.

## Unklare Buchhaltungszustellung.

Nach einem Absturz während der Übergabe wird der Job `UNKNOWN`. Ein Timeout allein rechtfertigt keine Wiederholung. Im Ziel nach Fallkennung und Idempotenzschlüssel suchen. Finance bestätigt entweder Zustellung mit Empfangskennung oder Nichtzustellung mit Untersuchungsbegründung. `FAILED` erlaubt eine ausdrückliche geprüfte Wiederholung. Das Original bleibt unverändert.

## Aufbewahrung und Überwachung.

Standard sind acht Jahre ab Ende des Ausstellungsjahres; längere Fristen sind konfigurierbar. Dies ist ein von deutschen Aufbewahrungsregeln informierter Standard, keine individuelle Rechtsberatung. Sperren werden erfasst; automatisches Löschen fehlt. Neue Richtlinien ändern bestehende Snapshots nicht rückwirkend. Externes Archiv, Friständerungen, Löschung und Betroffenenrechte benötigen ein geprüftes Betriebskonzept.

Prozess, Speicherplatz, Zustandsgröße, Ausnahmen, unklare Jobs und Fehlanmeldungen überwachen. `/health/live` ist nur ein Lebenszeichen, keine Validatorbereitschaft. `/api/system` zeigt authentifizierte Fähigkeiten. Produktions-SLO, RPO, RTO, Durchsatz und Verfügbarkeit sind nicht zertifiziert oder nachgewiesen.
