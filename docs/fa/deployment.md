# تنظیم استقرار.

پیش‌فرض `127.0.0.1:8090` است. حالت `--demo` فقط loopback را می‌پذیرد و خروجی خارجی ندارد. برای STRICT مسیر دادهٔ تازه بسازید. تبدیل مستقیم فضای نمایشی به تولید پشتیبانی نشده است.

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

مقادیر بالا جای‌نگهدار هستند. کلید تصادفی امن ۳۲بایتی را از فرایند مدیریت راز سازمان تولید کنید. راز واقعی را در تاریخچهٔ shell قرار ندهید. پورت 8090 فقط از reverse proxy مورد اعتماد قابل دسترسی باشد. مبدأ عمومی باید دقیقاً با نشانی مرورگر یکی باشد. هدر Forwarded-Host دلخواه قابل اعتماد نیست. TLS در پراکسی اپراتور پایان می‌یابد، نه شنوندهٔ داخلی Java.

`RF_DATA` یا `--data` مسیر ماندگار را انتخاب می‌کند. `RF_WORKSPACE` شناسهٔ اولیهٔ فضای سخت‌گیرانه است. `RF_MASTER_KEY` باید در هر اجرا همان کلید باشد. `RF_KOSIT_DIR` نصب بررسی‌شده است. `RF_VALIDATION_TIMEOUT` بین ۲ و ۱۲۰ ثانیه است. `RF_ACCOUNTING_IDEMPOTENT` تأیید صریح قرارداد مقصد است. با `RF_DISABLE_WORKER=true` کارها پیش نمی‌روند. `RF_BOOTSTRAP_PASSWORD` فقط برای اولین مقداردهی است، نه بازنشانی رمز.

قالب Docker و Compose برای نمونهٔ تک‌گرهی STRICT پشت پراکسی وجود دارد، اما اجرای محلی یا امنیت آن تأیید نشده است. کانتینر بدون root، حجم داده با مجوز محدود و ورودی اعتبارسنج فقط‌خواندنی لازم است. backend را مستقیم عمومی نکنید. هش تصویر پایه و مجوزها را پیش از انتشار بررسی کنید.

HA، مهاجرت گردشی، چرخش خودکار کلید، SMTP، آماده‌سازی ابری، SSO، پویش بدافزار و آرشیو دارای گواهی وجود ندارند. پشتیبان به توقف نمونه نیاز دارد. Linux محلی اجرا شده است؛ Windows، Docker، Java 25 و شبکهٔ مشتری به پذیرش مستقل نیاز دارند.
