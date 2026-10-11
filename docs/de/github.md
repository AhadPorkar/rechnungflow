# Veröffentlichung mit Windows CMD.

Ein neues Repository im eigenen GitHub-Konto anlegen. `rechnungflow` ist ein Namensvorschlag; dieses Projekt wurde nicht in Ihr Konto hochgeladen. Quellarchiv entpacken und CMD im Verzeichnis mit `README.md` und `invoice-core` öffnen.

```cmd
git init
git branch -M main
git config user.name "Ahad Porkar"
git config user.email "YOUR_VERIFIED_GITHUB_EMAIL"
scripts\test.cmd
git status --short
git add .
git diff --cached --stat
git diff --cached --name-only
git commit -m "feat: add RechnungFlow e-invoice workflow engineering preview"
git remote add origin https://github.com/AhadPorkar/rechnungflow.git
git push -u origin main
```

Die Befehle setzen ein leeres Remote voraus. Bei vorhandenen GitHub-Commits zuerst klonen und den Quellcode ohne fremdes `.git` hineinkopieren. Kein Force-Push zur Behebung unterschiedlicher Historien. E-Mail und Repositorynamen ersetzen. Browseranmeldung über Credential Manager ist eingebetteten Zugriffstokens vorzuziehen.

Lizenz und vorgemerkte Dateien prüfen. Keine echten Datenverzeichnisse, Schlüssel, Tokens, Lieferantenbelege oder Kundenscreenshots veröffentlichen. `.gitignore` schließt erzeugte Dateien aus, entfernt aber keine bereits eingecheckten Geheimnisse.

Nach dem Push Actions tatsächlich prüfen. Vorlagen decken Linux Java 21/25, Windows Java 21 und natives Chromium ab; erfolgreiche gehostete Läufe sind erst anschließend belegt. Offizielle Validatorabnahme ist separat und kann offene Probleme zeigen.

Erst nach Prüfung ein Release erstellen und JAR, Prüfsummen und Nachweise anhängen, statt Binärdateien in die Quellhistorie aufzunehmen. Als Engineering Preview kennzeichnen. Für das Portfolio Bankänderung, unabhängige Freigabe und Zustellungsklärung demonstrieren. Lebenslaufaussagen müssen beobachteten Ergebnissen entsprechen.
