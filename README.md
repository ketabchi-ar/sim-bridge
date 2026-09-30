# SIM Bridge 📱🔁📱

[![DevSponsors](https://devsponsors.github.io/assets/badges/sponsor.svg)](https://devsponsors.github.io)
[![DevSponsors](https://img.shields.io/badge/DevSponsors-Verified_OSS-6366f1?style=for-the-badge&logo=github)](https://devsponsors.github.io)
[![Sponsor](https://img.shields.io/badge/Sponsor-DevSponsors_Hub-emerald?style=for-the-badge&logo=github-sponsors)](https://devsponsors.github.io)
[![Cloud](https://img.shields.io/badge/Infrastructure-DevSponsors_Cloud-ec4899?style=for-the-badge&logo=server)](https://devsponsors.github.io/mediakit.html)
پل ارتباطی هوشمند بین دو گوشی اندرویدی از طریق بلوتوث:
1. **گوشی اول (Samsung S21 Ultra - سیم‌کارت‌دار)**
2. **گوشی دوم (Samsung S26 Ultra - بدون سیم‌کارت / رجیستر نشده)**

---

## قابلیت‌های کلیدی

* **انتقال زنده تماس (Live Call Relay):**
  * پخش صدای زنگ روی گوشی دوم هنگام زنگ خوردن سیم‌کارت گوشی اول.
  * امکان پاسخ دادن (Answer) یا رد تماس (Reject) مستقیم از روی گوشی دوم.
  * مکالمه دوطرفه زنده (Live Audio Relay) از طریق کانال صوتی بلوتوث (میکروفون و اسپیکر).
* **انتقال دوطرفه پیامک (Two-Way SMS Relay):**
  * دریافت فوری نوتیفیکیشن پیامک‌های دریافتی گوشی اول روی گوشی دوم.
  * ارسال پیامک از طریق گوشی دوم با استفاده از سیم‌کارت گوشی اول.
* **اینترنت کم‌مصرف (Bluetooth Tethering):**
  * انتقال اینترنت از طریق Bluetooth PAN بدون داغ شدن گوشی و مصرف باتری بالای هات‌اسپات Wi-Fi.

---

## نحوه استفاده و راه‌اندازی

### مرحله ۱: جفت‌سازی بلوتوث (Bluetooth Pair)
1. بلوتوث هر دو گوشی را روشن کنید.
2. از بخش تنظیمات بلوتوث، دو گوشی را به یکدیگر Pair کنید.

### مرحله ۲: فعال‌سازی اینترنت بلوتوثی (اختیاری اما توصیه شده)
1. در گوشی اول: `تنظیمات -> اتصالات -> نقطه اتصال همراه و اتصال اینترنت -> اتصال به اینترنت با بلوتوث (Bluetooth tethering)` را روشن کنید.
2. در گوشی دوم: در بخش بلوتوث، روی نام گوشی اول و آیکون چرخ‌دنده بزنید و تیک **Internet access** را فعال کنید.

### مرحله ۳: اجرای برنامه در دو گوشی
1. اپلیکیشن **SIM Bridge** را روی هر دو گوشی نصب کنید.
2. دسترسی‌های درخواستی (تماس، پیامک، میکروفون و بلوتوث) را تایید کنید.
3. در گوشی اول: تب **گوشی اول (میزبان)** را انتخاب کرده و دکمه **فعال‌سازی سرویس میزبان** را بزنید.
4. در گوشی دوم: تب **گوشی دوم (کلاینت)** را انتخاب کرده، دکمه **انتخاب و اتصال به گوشی اول** را بزنید و گوشی اول را انتخاب کنید.

ارتباط زنده برقرار است! 

---

## معماری و کامپوننت‌های فنی

* **لایه تبادل دیتا:** سرور و کلاینت RFCOMM SPP بر پایه استاندارد پروتکل بلوتوث.
* **لایه تماس:** ادغام با `TelecomManager` و `TelephonyCallback` برای کنترل تماس‌های مخابراتی.
* **لایه صوتی:** پایپ‌لاین دوطرفه PCM 16kHz با لغو اکو و نویزگیر صوتی (`VOICE_COMMUNICATION`).
* **لایه پیامک:** `SmsBroadcastReceiver` به همراه `SmsManager`.

---

## بیلد و دریافت APK
این پروژه دارای پایپ‌لاین خودکار GitHub Actions است که به ازای هر Push یا تغییر نسخه، خروجی‌های Debug و Release APK را در بخش **Actions -> Artifacts** و همچنین **Releases** قرار می‌دهد.
