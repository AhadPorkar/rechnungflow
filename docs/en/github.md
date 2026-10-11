# Publish from Windows CMD.

Create a new repository under your own GitHub account. The example name `rechnungflow` is a proposal; this project has not been pushed to your account. Extract the source ZIP and open CMD in the directory containing `README.md` and `invoice-core`.

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

These commands assume an empty remote. When GitHub already contains a commit, clone that repository first and copy this source into the clone, excluding any old `.git` directory. Do not force-push to solve a history mismatch. Replace the email and repository name with your actual values. Browser-based credential-manager authentication is preferable to placing a token in a remote URL.

Review the license, source, synthetic fixtures and staged files. Never add real runtime directories, master keys, tokens, supplier documents or screenshots containing customer data. `.gitignore` excludes generated build output, local data and validator binaries; ignore rules do not erase secrets already committed.

After pushing, inspect Actions rather than assuming green badges. The supplied workflows cover Linux Java 21/25, Windows Java 21, and native Chromium. They are templates until their hosted runs complete. The optional official-validator workflow is separate and may expose acceptance gaps; it must not be marketed as passed before execution.

Create a GitHub Release only after verification. Attach the runnable JAR, checksums and verification record rather than committing compiled binaries into source history. Use the engineering-preview designation. Pin the repository on your profile and show a short demo of a blocked bank change, independent approval and unknown-delivery reconciliation. Claims in the CV must reflect observed results, not feature plans.
