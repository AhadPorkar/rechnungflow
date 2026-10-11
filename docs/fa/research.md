# دفتر پژوهش.

تاریخ پژوهش **2026-10-10** است. منابع اولیهٔ قانون، مالک استاندارد، پلتفرم و امنیت آمده‌اند. هر مورد مبنای تصمیم یا مرز قابلیت پیاده‌نشده است، نه گواهی. مستند عمومی در دسترس بود، اما دریافت باینری از محیط ساخت مسدود بود؛ این دو سطح را یکی ندانید.

الزام دریافت و دورهٔ گذار صدور صورتحساب آلمان متفاوت‌اند. BMF قابلیت دریافت از ۲۰۲۵ و استثناهای مرحله‌ای صدور در ۲۰۲۶ و ۲۰۲۷ را شرح می‌دهد. دامنه و استثنای مشتری باید به‌روز بررسی شود. برنامه تصمیم حقوقی نمی‌گیرد. KoSIT 1.6.3 اصلاح امنیتی بازیابی URI دارد و تنظیم هدف تاریخ 2026-08-31 دارد. اعتبار فنی سند، معامله یا مجوز پرداخت را اثبات نمی‌کند.

منابع پویا ممکن است عوض شوند. پیش از ارتقا یادداشت انتشار را دوباره بررسی کنید. تأیید رسمی، گواهی یا وابستگی به نهاد استاندارد ادعا نشده است.

1. [BMF e-invoice FAQ](https://www.bundesfinanzministerium.de/Content/DE/FAQ/e-rechnung.html). زمینهٔ دریافت، صدور و تفاوت مهلت‌های آلمان.

2. [UStG section 14](https://www.gesetze-im-internet.de/ustg_1980/__14.html). قانون صورتحساب؛ بررسی حقوقی موردی لازم است.

3. [UStG section 14b](https://www.gesetze-im-internet.de/ustg_1980/__14b.html). مبنای نگه‌داری، نه گواهی آرشیو.

4. [KoSIT validator 1.6.3](https://github.com/itplr-kosit/validator/releases/tag/v1.6.3). نسخهٔ اصلاح امنیتی؛ باینری اینجا دریافت نشد.

5. [XRechnung configuration 2026-08-31](https://github.com/itplr-kosit/validator-configuration-xrechnung/releases/tag/v2026-08-31). تنظیم ثابت؛ پذیرش واقعی انجام نشده است.

6. [KoSIT public API](https://github.com/itplr-kosit/validator/blob/main/docs/api.md). پذیرش سند از موفقیت اجرای ابزار جداست.

7. [KoSIT CLI](https://github.com/itplr-kosit/validator/blob/main/docs/cli.md). بررسی شد؛ فرایند جدا با API انتخاب شد.

8. [KoSIT Result API](https://github.com/itplr-kosit/validator/blob/main/src/main/java/de/kosit/validationtool/api/Result.java). مرز پیشنهاد پذیرش و گزارش.

9. [KoSIT XRechnung project](https://github.com/itplr-kosit/validator-configuration-xrechnung). مخزن تنظیم و بررسی مجوز.

10. [FeRD ZUGFeRD](https://www.ferd-net.de/standards/zugferd). بررسی شد؛ استخراج PDF ترکیبی پیاده نیست.

11. [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html). قرارداد ماشینی HTTP، نه گواهی.

12. [OWASP XML external entity prevention](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html). غیرفعال‌سازی DTD و منبع خارجی و محدودیت ورودی.

13. [OWASP file upload](https://cheatsheetseries.owasp.org/cheatsheets/File_Upload_Cheat_Sheet.html). مرز حجم، نام، نوع، ذخیره و نمایش.

14. [OWASP password storage](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). تکرار PBKDF2-HMAC-SHA256 و salt.

15. [OWASP CSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html). توکن تغییر نشست و کنترل مبدأ.

16. [OWASP session management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html). انقضای نشست، ویژگی کوکی و لغو.

17. [Java 21 HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html). انتقال محدود و ابهام صریح.

18. [Java 21 virtual threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html). ترد مجازی با سقف هم‌زمانی برنامه.

19. [Playwright assertions](https://playwright.dev/python/docs/test-assertions). assertion روی locator بدون ارزیابی ناامن.

20. [GitHub Actions matrix](https://docs.github.com/en/actions/using-jobs/using-a-matrix-for-your-jobs). نتیجهٔ مستقل Java و سیستم‌عامل بدون لغو زودهنگام.

21. [GitHub Actions security](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions). حداقل دسترسی و بررسی انتشار.

22. [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html). گزینهٔ جایگزین بررسی شد؛ Spring Boot استفاده نشده است.

23. [GitHub checkout](https://github.com/actions/checkout). نسخهٔ اصلی مستند در CI انتخاب شده؛ اجرای میزبان تأیید نشده است.

24. [GitHub setup-java](https://github.com/actions/setup-java). نسخهٔ اصلی مستند در CI انتخاب شده؛ اجرای میزبان تأیید نشده است.

25. [GitHub setup-python](https://github.com/actions/setup-python). نسخهٔ اصلی مستند در CI انتخاب شده؛ اجرای میزبان تأیید نشده است.

26. [GitHub upload-artifact](https://github.com/actions/upload-artifact). نسخهٔ اصلی مستند در CI انتخاب شده؛ اجرای میزبان تأیید نشده است.

