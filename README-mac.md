# FoxyVPN for macOS — راهنمای نصب و استفاده

نسخهٔ دسکتاپ (مک) FoxyVPN، پورت‌شده از اپ اندرویدی [Vauth/FoxyVPN](https://github.com/Vauth/FoxyVPN) با
همان پوستهٔ Jetpack Compose / Material 3 — بدون تغییر در ساختار UI. موتور VPN (پراکسی SOCKS5 لوکال +
تونل HTTP/2 به لبهٔ Fastly) عیناً منتقل شده و فقط لایهٔ TUN اندروید با معادل مک (sing-box + سیستم‌پروکسی)
جایگزین شده است.

## فایل خروجی
`dist/FoxyVPN-1.0.4.dmg` — اپلیکیشن Apple Silicon (arm64)، با JRE داخلی (نیازی به نصب Java ندارید).

## نصب
1. DMG را باز کنید و FoxyVPN را به Applications بکشید.
2. چون اپ با Developer ID اپل **امضا نشده** (ساخت محلی)، بار اول یکی از این دو کار را بکنید:
   - راست‌کلیک روی اپ → **Open** → دوباره **Open**
   - یا در ترمینال: `xattr -dr com.apple.quarantine /Applications/FoxyVPN.app`

## حالت‌های ترافیک
- **Proxy-only** (سوئیچ در Settings): فقط SOCKS5 لوکال روی `127.0.0.1:1080`؛ اپ‌هایی مثل Telegram/Firefox را دستی به آن وصل کنید. بدون هیچ دسترسی.
- **System proxy** (پیش‌فرض): تنظیم سیستم‌پروکسی مک با هلپر root. یک بار پرامپت admin کافی است؛ هلپر به‌صورت LaunchDaemon نصب می‌ماند و با بستن/بازکردن یا نصب مجدد اپ دیگر نمی‌پرسد.

> حالت Full Tunnel (TUN/sing-box) در این نسخه حذف شده است؛ مدل ترافیک همان نسخهٔ اول است.

## داده‌ها و لاگ‌ها
- تنظیمات/توکن‌ها (مقدارها AES-GCM رمزنگاری می‌شوند): `~/Library/Application Support/FoxyVPN/`
- لاگ هلپر و sing-box (حالت TUN): `~/Library/Application Support/FoxyVPN/helper/singbox.log`
- لاگ داخل اپ: Settings → Logs

## نکات
- ورود با همان Firefox Account نسخهٔ اندروید کار می‌کند (سقف ۵۰ گیگ ماهانه).
- Split tunneling در حالت TUN روی مک فعلاً فقط لیست اپ‌های `/Applications` را نگه می‌دارد و
  در حالت پروکسی اثری ندارد (مثل اندروید که فقط روی VPN اعمال می‌شود).
- اگر حالت TUN وسط اتصال Edge را عوض کند، مسیرهای bypass از قبل برای همهٔ edgeهای آن شهر
  ثبت شده‌اند.

## ساخت دوباره
```bash
cd foxyvpn-mac
JAVA_HOME=<jdk-17> gradle packageDistributionForCurrentOS
# خروجی: build/compose/binaries/main/dmg/FoxyVPN-1.0.4.dmg
```
پیش‌نیازها: JDK 17 (Temurin) و Gradle 8.10.

## معماری پورت
- `src/main/kotlin/compat/` — لایهٔ سازگاری مینیمال با نام پکیج‌های اندروید
  (`android.content.Context`, `SharedPreferences`, `Toast`, `Base64`, `EncryptedSharedPreferences`,
  `LocalContext`, `PackageManager` و…) تا سورس UI/اسکرین‌ها بدون تغییر باینری بماند.
- `vpn/FoxyVpnService.kt` — همان ماشین‌حالت اتصال/watchdog/proxy-pass renewal، بدون Notification/WakeLock.
- `vpn/tun/MacHelper.kt` — هلپر root (LaunchDaemon، یک‌بار approval) فقط برای toggle سیستم‌پروکسی.
