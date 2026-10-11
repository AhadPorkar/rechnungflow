# API-Vertrag und Integration.

Die maschinenlesbare OpenAPI-3.1.1-Datei liegt unter `invoice-server/src/main/resources/openapi.json` und ist authentifiziert unter `/api/openapi.json` verfügbar. Sie beschreibt 38 Operationen. Geldbeträge werden als exakte Dezimalzeichenfolgen übertragen. Für Buchhaltungsentscheidungen nicht in binäre Gleitkommazahlen umwandeln. Fehler liefern einen stabilen `error.code` und eine Anfragekennung.

## Authentifizierung.

Browsersitzungen verwenden ein HttpOnly-Cookie. `POST /api/auth/login` erwartet `tenant`, `login` und `password`. Die Antwort enthält `csrf`; jede nachfolgende schreibende Sitzungsanfrage benötigt `X-CSRF-Token`. Fremdursprüngliche Änderungen werden abgewiesen. Neue Benutzer müssen ihr Initialpasswort ändern. Sitzungsgeheimnisse gehören nicht in localStorage.

Administratoren erzeugen zeitlich begrenzte Eingangsschlüssel unter Team & access. Übergabe als `Authorization: Bearer <token>`. Diese Schlüssel dürfen Dokumente einreichen und eigene Fälle abrufen, aber weder prüfen, freigeben, exportieren, Benutzer verwalten noch fremde Fälle lesen. Das Token wird nur einmal angezeigt und gehasht gespeichert. Für Importprozesse ist es einem eingebetteten Administratorpasswort vorzuziehen.

## Eingang.

```http
POST /api/invoices
Authorization: Bearer <intake-token>
Content-Type: application/xml
X-Filename: supplier-invoice.xml
Idempotency-Key: <submission-key>
```

Im Body unverändertes XML senden, weder Multipart noch JSON. Höchstens 2 MiB. Derselbe Idempotenzschlüssel mit anderen Bytes führt zum Konflikt. Eine Berichtigung verwendet `?supersedes=<case-id>` und ein neues unveränderliches Dokument. Für Wiederholungen derselben logischen Einreichung bleibt der Schlüssel gleich.

## Versionierte Aktionen.

`GET /api/invoices/{id}` liefert `version`. Diesen Wert bei `review`, `approve`, `reject`, `comment`, `hold`, `revalidate` oder `export` mitsenden. Eine Prüfung benötigt zusätzlich `costCenter`, `glAccount` und `note`; Export außerdem einen Idempotenzschlüssel. `GET .../report?attempt=0` lädt den ersten erhaltenen Prüflauf. Ohne `attempt` wird der aktuelle Bericht angefordert. Fehlende Berichte erzeugen einen Fehler statt erfundener Nachweise.

## Buchhaltungsadapter.

Ein festes HTTPS-Ziel wird vom Betreiber konfiguriert. JSON enthält Fallkennung, Beträge, Kontierung, Originalhash und Validierungsnachweise. Der Idempotenzschlüssel bleibt stabil. Erfolg verlangt dieselbe `invoiceId` und eine nicht leere `receiptId` in der Antwort. Status 200 allein genügt nicht. Demojobs rufen niemals das externe Ziel auf. Zertifizierte DATEV-/SAP-Anbindung, automatische Zahlung, eingehende Webhooks und Peppol-Endpunkt sind nicht implementiert.
