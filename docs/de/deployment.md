# Bereitstellung.

Standard ist `127.0.0.1:8090`. `--demo` ist auf Loopback begrenzt und liefert nicht an externe Buchhaltung. Für STRICT ein neues Datenverzeichnis verwenden. Eine produktive Umwandlung des Demo-Arbeitsbereichs ist nicht vorgesehen.

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

Alle Werte sind Platzhalter. Einen kryptografisch zufälligen 32-Byte-Schlüssel über die freigegebene Secret-Verwaltung erzeugen. Produktive Geheimnisse nicht in Shellhistorien ablegen. Port 8090 nur für den vertrauenswürdigen TLS-Reverse-Proxy erreichbar machen. `RF_PUBLIC_ORIGIN` muss exakt dem Browserursprung entsprechen. Beliebige Forwarded-Host-Header werden nicht vertraut. TLS endet am Betreiberproxy, nicht am Java-Listener.

`RF_DATA` beziehungsweise `--data` wählt den persistenten Speicher. `RF_WORKSPACE` legt beim ersten strikten Start die Arbeitsbereichskennung fest. `RF_MASTER_KEY` muss bei Neustarts unverändert verfügbar sein. `RF_KOSIT_DIR` bezeichnet die geprüfte Installation. `RF_VALIDATION_TIMEOUT` liegt zwischen 2 und 120 Sekunden. `RF_ACCOUNTING_IDEMPOTENT` bestätigt ausdrücklich den Vertrag des Zielsystems. Bei `RF_DISABLE_WORKER=true` bleiben Jobs in der Warteschlange. `RF_BOOTSTRAP_PASSWORD` dient nur der ersten Initialisierung, nicht der Rücksetzung.

Docker-/Compose-Vorlagen sind für eine strikte Einzelinstanz hinter einem Proxy enthalten, aber nicht als lokal ausgeführte oder sicherheitsgeprüfte Bereitstellung belegt. Container ohne Root-Rechte betreiben, Datenvolume begrenzen und Validator schreibgeschützt einbinden. Backend nicht direkt im Internet veröffentlichen. Image-Digests und Lizenzen vor Freigabe prüfen.

HA, rollierende Migration, automatische Schlüsselrotation, SMTP, Cloud-Provisionierung, SSO, Malwareprüfung und zertifiziertes Archiv fehlen. Sicherungen erfordern eine angehaltene Instanz. Linux wurde lokal ausgeführt; Windows, Docker, Java 25 und Kundennetzwerk benötigen eigene Abnahme.
