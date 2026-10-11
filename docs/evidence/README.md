# Verification evidence / Pruefnachweise / مدارک آزمون.

## English.

RechnungFlow 0.1.0 is an engineering preview, not verified sell-ready financial software. This directory contains actual local JUnit XML, execution logs, and screenshots from real application assets against the real Java server. All invoices, credentials, and suppliers used for tests are synthetic.

**564 checks passed, 2 explicitly skipped, 0 failed.** The browser harness accounts for 79 passing checks and two skipped native security checks. No test-coverage percentage is asserted. The Java validation suite includes clearly labeled synthetic engine doubles; those are not official KoSIT acceptance.

| Suite | Passed | Skipped | Failed / errors |
|---|---:|---:|---:|
| auth | 34 | 0 | 0 |
| browser-harness | 79 | 2 | 0 |
| core | 75 | 0 | 0 |
| http | 100 | 0 | 0 |
| ingest | 69 | 0 | 0 |
| installer | 22 | 0 | 0 |
| operations | 15 | 0 | 0 |
| outbox | 45 | 0 | 0 |
| repository | 24 | 0 | 0 |
| validation | 29 | 0 | 0 |
| workflow | 72 | 0 | 0 |

The complete primary command passed. A clean extraction independently rebuilt the application and passed all 485 primary checks again; repeated executions are not added to the distinct total. Compiled JAR entry payloads matched. The detailed record is in verification.json. The two native-only tests concern browser-enforced CSP and cookie transport. The local environment refuses native navigation with ERR_BLOCKED_BY_ADMINISTRATOR; the explicitly selected harness renders the actual interface and sends actions to the real Java HTTP service. It does not emulate successful vendor validation. Hosted Actions is configured for native navigation and does not silently fall back to the harness.

Originals, strict-mode blocking, authentication, role checks, duplicates, approval transitions, version conflicts, supplier changes, export uncertainty, restart recovery, backup/restore, and API errors are tested. Repository/static checks are counted separately from runtime suites. Screenshots are captures, not design mockups. Persian viewport captures use Chromium layout coordinates to account for RTL origin offsets; no application pixels are retouched.

Actual official KoSIT binaries, Windows, macOS, Java 25, Docker, hosted CI and external accounting systems were not exercised here. Review the commercial-readiness guide before any customer deployment. Evidence is historical, not a guarantee about changes made after this release.

[Machine-readable verification](verification.json). [JUnit XML](reports). [Offline log](offline-tests.txt). [Browser log](browser-harness-tests.txt).

## Deutsch.

Diese Ausgabe ist eine Engineering-Vorschau und keine als verkaufsfertig oder rechtskonform bestaetigte Finanzsoftware. Lokal wurden **564 Pruefungen bestanden, 2 ausdruecklich uebersprungen und 0 fehlgeschlagen**. Die Browser-Pruefungen verwenden einen ausdruecklich gewaehlten Harness mit der echten Oberflaeche und dem echten Java-Server. Native Navigation, Cookie-Transport und CSP sind damit nicht bestaetigt.

Die echte KoSIT-Abnahme konnte wegen der Netzbeschraenkung nicht ausgefuehrt werden. Windows, Java 25, Docker und gehostete GitHub Actions sind ebenfalls unbestaetigt. Synthetische Validator-Doubles zaehlen nicht als amtliche Validierung. Die XML-Berichte und Protokolle dokumentieren konkrete Ausfuehrungen; eine Code-Coverage-Prozentzahl wird nicht behauptet. Der Stand des sauberen Neuaufbaus steht in verification.json.

## فارسی.

# راهبرد آزمون و شواهد.

از ریشهٔ پروژه مجموعهٔ آفلاین را اجرا کنید.

```cmd
scripts\test.cmd
```

اسکریپت کلاس تولید و آزمون را دوباره می‌سازد، سپس هسته، ورود، گردش کار، احراز هویت، صف خروجی، اعتبارسنجی و آزمون HTTP واقعی، نگه‌داری، نصب‌کننده و مخزن را اجرا می‌کند. گزارش JUnit XML در `build/test-results` است. Maven و شبکه لازم نیست. شکست یک مجموعه فرمان را متوقف می‌کند.

هسته اعشار دقیق، کلید تکراری JSON، رمزنگاری، زمینهٔ احراز اصالت، rollback، ماندگاری، قفل، تزریق فرمول CSV و تغییر ممیزی را می‌آزماید. ورودی namespace، DTD/XXE/XInclude، سقف منابع، مقادیر خراب، جمع، CII و سند بستانکار را بررسی می‌کند. گردش کار انتقال وضعیت، نقش ممنوع، جداسازی مستأجر، تکرار، تغییر بانک، تأیید مستقل، آستانه، نسخهٔ قدیمی، اصلاحیه و شواهد را پوشش می‌دهد. آزمون خروجی برای رسید، redirect، حجم، timeout و عدم تکرار از سرویس HTTP محلی واقعی استفاده می‌کند.

## مرورگر.

```cmd
python -m pip install -r tests\browser\requirements.txt
python -m playwright install chromium
python tests\browser\ui_test.py
```

پیش‌فرض پیمایش بومی مرورگر است. فقط گزینهٔ صریح `RF_UI_TRANSPORT=harness` فایل واقعی رابط را رندر و درخواست را از پل آزمون به Java واقعی می‌فرستد. انتخاب پنهان انجام نمی‌شود. این روش پیمایش بومی، انتقال کوکی مرورگر، اجرای CSP و هدر استقرار را ثابت نمی‌کند. آزمون امنیت مرتبط صریحاً رد می‌شود و باید در CI بومی اجرا شود. هر آزمون زمینهٔ مرورگر و فهرست خطای جدا دارد.

`docs/evidence` اجرای محلی واقعی را ثبت می‌کند. وجود کد آزمون به معنی موفقیت GitHub، Windows، Java 25، Docker یا محیط مشتری نیست. پذیرش واقعی KoSIT فرمان جداست و نبود موتور موفق حساب نمی‌شود. درصد پوشش، بار، دسترس‌پذیری و انطباق رسمی ساختگی اعلام نمی‌شود.

## پیش از انتشار.

نسخهٔ تمیز، همهٔ آزمون‌ها، نمونه‌های واقعی KoSIT، مرورگرهای مقصد، مسیر Windows، بازیابی، ارتقا و مقصد حسابداری را بررسی کنید. commit، محیط، XML نتیجه و هش موتور را آرشیو کنید. برای خطای گزارش‌شده ابتدا آزمون بازگشتی بسازید. انتظار کد خطا را برای پنهان کردن اشکال زمانی ضعیف نکنید.


```text
Passed: 564
Skipped: 2
Failed: 0
Official KoSIT acceptance: NOT RUN
Native browser acceptance: NOT RUN
```
