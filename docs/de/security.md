# Sicherheitsmodell und Grenzen.

Rechnungen, Dateinamen, Lieferantendaten und Prüfberichte sind nicht vertrauenswürdig. DTDs, externe Entitäten, externe Schemas und XInclude werden deaktiviert. Tiefe, Knoten, Attribute und Dateigröße sind begrenzt. Namensraumgenaue Extraktion akzeptiert keine gleichnamigen Elemente aus gefälschten Namensräumen. Die Oberfläche maskiert Inhalte, statt Rechnungsmarkup auszuführen.

Sitzungsgeheimnisse sind zufällig und nur als Hash gespeichert. Cookies verwenden HttpOnly/SameSite; Sitzungen laufen nach acht Stunden beziehungsweise dreißig Minuten Inaktivität ab. Passwörter nutzen PBKDF2-HMAC-SHA256 mit 600.000 Iterationen und individuellen Salts. MFA, OIDC/SSO, E-Mail-Passwortrücksetzung und zentraler Identitätsanbieter fehlen. Die eigene Anmeldung benötigt unabhängige Sicherheitsprüfung. Anmeldelimits gelten lokal pro Prozess; öffentlich erreichbare Installationen benötigen zusätzlich geeignete Begrenzung am Netzwerkrand.

Rollen und Mandantenzugehörigkeit werden serverseitig geprüft, unabhängig von sichtbaren Schaltflächen. CSRF- und Ursprungsprüfungen schützen Änderungen. Eingangsschlüssel sind begrenzt, widerrufbar und nicht in Listen im Klartext enthalten. Passwort- und Berechtigungsänderungen entwerten Sitzungen. Der letzte Administrator kann über die API nicht entfernt werden.

Originale, Berichte und Zustand sind mit AES-256-GCM verschlüsselt; Kontextinformationen werden authentifiziert. Produktionsschlüssel getrennt von Daten und Sicherungen verwahren. Verschlüsselung verhindert nicht den Zugriff eines kompromittierten autorisierten Prozesses. Ein Online-Schlüsselrotationsverfahren ist nicht implementiert.

Eine strikte CSP wird per HTTP-Header ausgeliefert, ohne `unsafe-inline` oder `unsafe-eval`. Lokale Browser-Harnesstests beweisen ihre native Durchsetzung nicht. Native CI-Prüfungen sind vorbereitet. XML wird als Anhang heruntergeladen, nicht als ausführbarer Seiteninhalt eingesetzt.

Buchhaltungsziele sind feste Betreiberkonfiguration und benötigen HTTPS. Netzwerkausgangskontrolle, TLS-Terminierung, Secret-Verwaltung, Schadsoftwarekonzept, Systempatches, Prüfung der KoSIT-Abhängigkeiten und Penetrationstest bleiben erforderlich. Die lokale Auditkette ist weder WORM noch extern verankert und schützt nicht gegen Austausch des gesamten Zustands samt Schlüssel. Automatische DSGVO- oder GoBD-Konformität wird nicht zugesichert.
