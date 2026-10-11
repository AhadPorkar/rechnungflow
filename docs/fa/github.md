# انتشار با Windows CMD.

در حساب GitHub خود مخزن تازه بسازید. `rechnungflow` فقط نام پیشنهادی است و پروژه به حساب شما push نشده است. آرشیو منبع را باز و CMD را در پوشهٔ دارای `README.md` و `invoice-core` اجرا کنید.

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

این فرمان‌ها مخزن remote خالی را فرض می‌کنند. اگر GitHub قبلاً commit دارد، ابتدا clone کنید و منبع را بدون `.git` قبلی داخل آن کپی کنید. برای اختلاف تاریخچه force-push نکنید. ایمیل و نام مخزن را با مقدار واقعی عوض کنید. ورود مرورگری با Credential Manager از قرار دادن توکن در URL بهتر است.

مجوز و فایل‌های stageشده را بازبینی کنید. مسیر دادهٔ واقعی، کلید اصلی، توکن، سند تأمین‌کننده یا تصویر مشتری را منتشر نکنید. `.gitignore` خروجی ساخته‌شده را کنار می‌گذارد، ولی راز commitشده را پاک نمی‌کند.

پس از push نتیجهٔ Actions را واقعاً ببینید. قالب‌ها Linux با Java 21 و 25، Windows با Java 21 و Chromium بومی را دارند؛ تا اجرا نشده‌اند موفق محسوب نمی‌شوند. پذیرش رسمی اعتبارسنج جداست و ممکن است نقصی نشان دهد.

بعد از بررسی Release بسازید و JAR، checksum و شواهد را ضمیمه کنید، نه در تاریخچهٔ منبع. عنوان پیش‌نمایش مهندسی را حفظ کنید. برای رزومه، تغییر بانک مسدودشده، تأیید مستقل و رسیدگی به تحویل نامعلوم را نمایش دهید. ادعا باید مطابق نتیجهٔ مشاهده‌شده باشد، نه برنامهٔ آینده.
