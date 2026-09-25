<div align="center">

# Vulpine VPN for macOS & Windows 🦊

**Unofficial Firefox VPN client for macOS and Windows** — a desktop port of
[Vauth/FoxyVPN](https://github.com/Vauth/FoxyVPN) built with Kotlin and
Compose Multiplatform, keeping the original Material 3 UI pixel-for-pixel.

*کلاینت رسمی‌نبودِ Firefox VPN برای مک — پورت دسکتاپ پروژهٔ FoxyVPN با Kotlin و
Compose Multiplatform و همان رابط Material 3 نسخهٔ اندروید.*

![macOS](https://img.shields.io/badge/macOS-Apple%20Silicon-000000?style=for-the-badge&logo=apple&logoColor=white)
![Windows](https://img.shields.io/badge/Windows-x64-0078D6?style=for-the-badge&logo=windows&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Compose%20Multiplatform-1.9-4285F4?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-green?style=for-the-badge)

</div>

---

## English

### What is this?
Vulpine VPN signs in with a **Firefox Account**, obtains a proxy pass from
Mozilla's Guardian service, and carries your traffic through Firefox VPN's
Fastly edge over an HTTP/2 tunnel — the same engine as the Android app
[FoxyVPN](https://github.com/Vauth/FoxyVPN), ported to macOS.

> [!NOTE]
> **No subscription required.** It runs on the free **50 GB/month** of VPN
> traffic Mozilla includes with a Firefox account (the same allowance the
> Firefox browser's built-in VPN uses).

### Features
- Firefox Account sign-in (email + password, Hawk/OAuth — same as Android)
- Server/location picker with latency measurement
- Two traffic modes: **Proxy-only** (local SOCKS5) and **System proxy**
- Private DNS by default + DNS-over-HTTPS options
- Upstream proxy chaining (chain behind another SOCKS5/HTTP proxy)
- Exit verification, in-app logs, dark/light/system theme
- Split-tunneling list (macOS app picker)
- Native installers with bundled JRE — **no Java installation needed**
  (`.dmg` for macOS, `.exe`/`.msi` for Windows)
- One-time administrator approval for system proxy (survives app restarts
  and upgrades; never asks again)

### Requirements
- macOS 11 or newer, **Apple Silicon** (arm64), **or** Windows 10/11 x64
- A Firefox account — create one free at
  [accounts.firefox.com](https://accounts.firefox.com/signup)

### Installation
1. Download the latest installer from
   [Releases](https://github.com/HELBOYCODER/VulpineVPN-mac/releases):
   `VulpineVPN-x.y.z.dmg` (macOS) or `VulpineVPN-x.y.z.exe` (Windows).
2. **macOS:** open the DMG and drag **Vulpine VPN** to *Applications*.
   **Windows:** run the `.exe` installer (per-user, no admin needed).
3. The build is **not signed with an Apple Developer ID** (local build).
   On first launch, do one of:
   - Right-click the app → **Open** → **Open**, or
   - In Terminal:
     ```bash
     xattr -dr com.apple.quarantine /Applications/VulpineVPN.app
     ```
   - **Windows:** SmartScreen shows *"Windows protected your PC"* for the
     unsigned build — click **More info → Run anyway**.

### How to use
1. Launch **Vulpine VPN** and sign in with your Firefox account.
2. Pick a location (or keep *Recommended Location*).
3. Press the power button.
   - **System proxy** (default): Chrome, Edge, Safari and Firefox traffic
     is routed automatically.
     - **Windows:** applied through per-user registry settings —
       **no administrator prompt at all**.
     - **macOS:** the very first connection asks for administrator access
       once (a tiny LaunchDaemon helper toggles the proxy). It never asks
       again, even after app updates or reinstalls.
   - **Proxy-only mode** (Settings → Local proxy → Proxy-only mode): no
     system changes; point individual apps at `SOCKS5 127.0.0.1:1080`.
4. Verify: open [ipify.org](https://ipify.org) in the browser — you should
   see the VPN exit IP, not your own.

> [!TIP]
> **Telegram Desktop** ignores the macOS system proxy by design. In
> Telegram: *Settings → Connection → Proxy → Add proxy → SOCKS5,
> `127.0.0.1`, port `1080`* — it works in both modes.

### Data & logs
- Settings and tokens (values AES-256-GCM encrypted):
  `~/Library/Application Support/FoxyVPN/`
- Helper logs: `~/Library/Application Support/FoxyVPN/helper/launchd.*`
- In-app logs: **Settings → Logs** (copy or export to a file)

### Removing the privileged helper
Closing the app **does not** remove it (by design). To remove completely:
```bash
sudo launchctl bootout system/com.vauth.foxyvpn.helper
sudo rm /Library/LaunchDaemons/com.vauth.foxyvpn.helper.plist
```

### Troubleshooting
| Symptom | Fix |
|---|---|
| Browser traffic doesn't change | Make sure *Proxy-only mode* is **off** in Settings, then reconnect; check `scutil --proxy` shows `SOCKSEnable : 1` |
| "System proxy needs administrator access" | The admin prompt was declined — reconnect and approve it once |
| Old icon stuck in Dock | `killall Dock` |
| Quota exhausted | Mozilla's 50 GB resets monthly; try again after reset |
| Another VPN behaves oddly after disconnect | The helper restores the proxy on disconnect; fully quit and relaunch the app if needed |

### Building from source
```bash
git clone https://github.com/HELBOYCODER/VulpineVPN-mac.git
cd VulpineVPN-mac
# JDK 17+ and Gradle 8.10 required (no Android SDK needed)
gradle packageDistributionForCurrentOS
# macOS output: build/compose/binaries/main/dmg/*.dmg
# Windows output: build/compose/binaries/main/exe/*.exe (and msi/)
```
Pushing a `v*` tag triggers the **Build installers** GitHub Actions
workflow, which compiles both installers on native runners and attaches
them to the release automatically.
Headless verification harness: `gradle connectTest` (connects with the
stored session, checks the system proxy and exit IP, then restores).

### Architecture (how the port works)
- `src/main/kotlin/compat/` — a minimal Android-compatibility layer
  (`android.content.Context`, `SharedPreferences`, `Toast`, `Base64`,
  `EncryptedSharedPreferences`, `LocalContext`, `PackageManager`, …) so the
  Android UI sources compile **unchanged** on desktop.
- The VPN engine (local SOCKS5 server, Netty HTTP/2 tunnel to the Fastly
  edge, Guardian proxy-pass renewal, edge failover, watchdog) is the
  original JVM code, reused as-is.
- `vpn/tun/MacHelper.kt` — the privileged LaunchDaemon helper that toggles
  the macOS system proxy (admin approval once, then silent).
- The engine itself always bypasses the system proxy it configures
  (`ProxySelector` pinned to DIRECT), so control-plane traffic never loops.
- Full-tunnel (TUN) mode was **removed** in v1.0.4-mac3 at user request;
  the traffic model is proxy-only / system-proxy.

---

## فارسی

### این چیست؟
والپاین وی‌پی‌ان با **اکانت فایرفاکس** وارد می‌شود، از سرویس Guardian موزیلا
«proxy pass» می‌گیرد و ترافیک شما را از طریق لبهٔ Fastly و تونل HTTP/2 عبور
می‌دهد — همان موتور نسخهٔ اندروید (FoxyVPN) که برای مک پورت شده است.

> **اشتراک لازم نیست.** روی همان سقف رایگان **۵۰ گیگ در ماه** موزیلا کار
> می‌کند که برای اکانت‌های فایرفاکس (VPN داخلی مرورگر فایرفاکس) در نظر گرفته
> شده و ماهانه ریست می‌شود.

### امکانات
- ورود با اکانت فایرفاکس (ایمیل + رمز)
- انتخاب سرور/کشور با سنجش تأخیر
- دو حالت ترافیک: **فقط پروکسی** (SOCKS5 لوکال) و **پروکسی سیستمی**
- DNS خصوصی به‌صورت پیش‌فرض + گزینه‌های DNS-over-HTTPS
- اتصال زنجیره‌ای به پروکسی بالادستی (SOCKS5/HTTP)
- بررسی IP خروجی، لاگ داخلی، تم تیره/روایت/سیستمی
- خروجی `.app` با JRE داخلی — **نیازی به نصب جاوا نیست**
- یک‌بار اجازهٔ ادمین برای پروکسی سیستمی — با نصب مجدد یا آپدیت هم تکرار
  نمی‌شود

### پیش‌نیازها
- macOS 11 یا جدیدتر (اپل سیلیکون) **یا** ویندوز 10/11 (64-bit)
- اکانت فایرفاکس — ساخت رایگان در
  [accounts.firefox.com](https://accounts.firefox.com/signup)

### نصب
1. از بخش [Releases](https://github.com/HELBOYCODER/VulpineVPN-mac/releases)
   آخرین نصبی را بگیرید: `VulpineVPN-x.y.z.dmg` (مک) یا `VulpineVPN-x.y.z.exe` (ویندوز).
2. **مک:** DMG را باز کنید و **Vulpine VPN** را به *Applications* بکشید.
   **ویندوز:** فایل `.exe` را اجرا کنید (نصب در سطح کاربر، بدون ادمین).
3. بیلد با Developer ID اپل **امضا نشده** (ساخت محلی). بار اول یکی از این دو:
   - راست‌کلیک روی اپ → **Open** → دوباره **Open**، یا
   - در ترمینال:
     ```bash
     xattr -dr com.apple.quarantine /Applications/VulpineVPN.app
     ```
   - **ویندوز:** چون بیلد امضا نشده، SmartScreen پیام *"Windows protected
     your PC"* می‌دهد — روی **More info → Run anyway** کلیک کنید.

### طرز استفاده
1. اپ را باز کنید و با اکانت فایرفاکس وارد شوید.
2. لوکیشن را انتخاب کنید (یا همان *Recommended Location*).
3. دکمهٔ اتصال را بزنید:
   - **پروکسی سیستمی** (پیش‌فرض): ترافیک کروم، اج، سافاری و فایرفاکس
     خودکار از تونل رد می‌شود.
     - **ویندوز:** از طریق تنظیمات رجیستری سطح کاربر اعمال می‌شود —
       **بدون هیچ پرامپت ادمین**.
     - **مک:** در اولین اتصال یک بار رمز ادمین خواسته می‌شود (هلپر
       LaunchDaemon)؛ بعد از آن هرگز نمی‌پرسد.
   - **فقط پروکسی** (تنظیمات → Local proxy → Proxy-only mode): بدون هیچ
     تغییری در سیستم؛ اپ‌های موردنظر را به `SOCKS5 127.0.0.1:1080` وصل کنید.
4. تست: در مرورگر [ipify.org](https://ipify.org) را باز کنید — باید IP
   تونل را ببینید نه IP خودتان.

> **تلگرام دسکتاپ** عمداً پروکسی سیستمی مک را نمی‌خواند. در تلگرام:
> *Settings → Connection → Proxy → Add proxy → SOCKS5 با `127.0.0.1` و
> پورت `1080`* — در هر دو حالت کار می‌کند.

### داده‌ها و لاگ‌ها
- تنظیمات و توکن‌ها (مقادیر با AES-256-GCM رمزنگاری می‌شوند):
  `~/Library/Application Support/FoxyVPN/`
- لاگ هلپر: `~/Library/Application Support/FoxyVPN/helper/`
- لاگ داخل اپ: **تنظیمات → Logs** (کپی یا خروجی فایل)

### حذف کامل هلپر ادمین
بستن اپ، هلپر را حذف **نمی‌کند** (طراحی عمدی برای «یک‌بار برای همیشه»):
```bash
sudo launchctl bootout system/com.vauth.foxyvpn.helper
sudo rm /Library/LaunchDaemons/com.vauth.foxyvpn.helper.plist
```

### عیب‌یابی
| مشکل | راه‌حل |
|---|---|
| ترافیک مرورگر تغییر نمی‌کند | مطمئن شوید *Proxy-only mode* خاموش است، دوباره وصل شوید؛ `scutil --proxy` باید `SOCKSEnable : 1` نشان دهد |
| پیام «System proxy needs administrator access» | پرامپت ادمین رد شده — دوباره اتصال بزنید و تأیید کنید |
| آیکون قدیمی در Dock | `killall Dock` |
| سقف ۵۰ گیگ تمام شده | ماهانه ریست می‌شود؛ بعد از آن دوباره امتحان کنید |

### بیلد از سورس
```bash
git clone https://github.com/HELBOYCODER/VulpineVPN-mac.git
cd VulpineVPN-mac
# فقط JDK 17+ و Gradle 8.10 لازم است (بدون Android SDK)
gradle packageDistributionForCurrentOS
# خروجی: build/compose/binaries/main/dmg/VulpineVPN-<version>.dmg
```
تست بی‌سر‌رابطه: `gradle connectTest` (با سشن ذخیره‌شده وصل می‌شود،
پروکسی سیستمی و IP خروجی را بررسی و سپس وضعیت را برمی‌گرداند).

### ساختار پورت
- `src/main/kotlin/compat/` — لایهٔ سازگاری مینیمال اندروید تا کد UI نسخهٔ
  اندروید **بدون تغییر** روی دسکتاپ کامپایل شود.
- موتور VPN (سرویس SOCKS5 لوکال، تونل HTTP/2 نتّی به لبهٔ Fastly، تمدید
  proxy-pass، جابه‌جایی edge، watchdog) همان کد JVM اصلی است.
- `vpn/tun/MacHelper.kt` — هلپر LaunchDaemon فقط پروکسی سیستمی را روشن/خاموش
  می‌کند (یک‌بار اجازهٔ ادمین، بعد بی‌صدا).
- خودِ موتور هرگز پروکسی سیستمی‌ای که می‌سازد دنبال نمی‌کند (ProxySelector
  روی DIRECT قفل شده) تا ترافیک کنترلی حلقه نکند.
- حالت تونل کامل (TUN) در نسخهٔ 1.0.4-mac3 طبق درخواست حذف شد؛ مدل ترافیک
  فقط «پروکسی لوکال / پروکسی سیستمی» است.

## License
MIT — based on [Vauth/FoxyVPN](https://github.com/Vauth/FoxyVPN) (MIT).
