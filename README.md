# دانَک (Danak)

**هر اسکرول، یک دانستنی.**

دانَک یعنی یک تکهٔ کوچک از دانستن. یک اپلیکیشن Android برای یادگیری خُرد (microlearning)
به زبان فارسی: هر اسکرول عمودی، یک مفهوم کوتاه و واقعاً مفید — بدون حساب کاربری،
بدون فید اجتماعی و بدون شلوغی.

This is **V0**: a complete, polished front end running entirely on local mock content.

---

## Stack

| | |
|---|---|
| زبان | Kotlin 2.4.20 (Kotlin داخلی AGP 9) |
| UI | Jetpack Compose + Material 3 |
| ناوبری | Navigation Compose |
| حالت | ViewModel + StateFlow + Coroutines |
| تصاویر | Coil |
| ماندگاری | DataStore Preferences |
| Build | AGP 9.4.1 · Gradle 9.7.1 · Kotlin DSL + version catalog |
| فونت | Vazirmatn (SIL OFL 1.1) |

`compileSdk` = 37، `targetSdk` = 36، `minSdk` = 26 (Android 8.0).

بدون backend، بدون احراز هویت، بدون Room/Retrofit/Firebase، بدون analytics و بدون
هیچ API هوش مصنوعی. تمام محتوا محلی است؛ فقط تنظیمات کاربر با DataStore روی دستگاه ذخیره می‌شود.

---

## ساخت و اجرا

```bash
git clone <repo> && cd Danak

# دیباگ
./gradlew assembleDebug        # خروجی: app/build/outputs/apk/debug/app-debug.apk

# نصب روی گوشی متصل
./gradlew installDebug

# تست‌ها
./gradlew test                 # تست‌های واحد (JVM)
./gradlew connectedAndroidTest # تست‌های Compose (نیازمند دستگاه/شبیه‌ساز)

# نسخهٔ release (امضا نشده)
./gradlew assembleRelease
```

پیش‌نیازها: JDK 17 یا بالاتر و Android SDK با API 37.
اگر از Android Studio استفاده می‌کنی، پروژه را باز کن و SDK لازم خودکار دانلود می‌شود.

### CI (GitHub Actions)

`.github/workflows/android.yml` روی هر push اجرا می‌شود:

| Job | کار |
|---|---|
| Build, unit tests, lint | `assembleDebug` · `testDebugUnitTest` · `lintDebug` · `assembleRelease` (با R8) |
| Source links resolve | باز کردن همهٔ لینک‌های منبع و پیشنهاد مقالهٔ فارسی |
| UI tests on emulator | تست‌های Compose روی امولاتور API 34، یک بار با فونت عادی و یک بار با فونت ۱٫۳× |

خروجی‌ها به‌صورت artifact آپلود می‌شوند: APK دیباگ، گزارش‌ها، اسکرین‌شات همهٔ صفحه‌ها و
گزارش دسترس‌پذیری. برای اینکه اسکرین‌شات‌ها داخل لاگ هم چاپ شوند، از Actions یک اجرای
دستی با `dump_screenshots` بزن یا `[screenshots]` را در پیام کامیت بگذار.

---

## دامنهٔ V0

### هست

- **فید** — `VerticalPager` تمام‌صفحه؛ هر swipe دقیقاً یک دانک، با snap، پارالاکس ملایم
  روی تصویر و هپتیک ظریف هنگام تثبیت صفحه. در انتها یک صفحهٔ پایان با «افزودن موضوعات»
  و «از اول»؛ با عوض شدن علاقه‌مندی‌ها فید به ابتدا برمی‌گردد.
- **جزئیات** — «بیشتر بدان» لایهٔ عمیق‌تر همان دانک را باز می‌کند: همان تصویر، همان
  تیتر، متن کامل با زیرعنوان‌ها، منبع و ذخیره.
- **علاقه‌مندی‌ها** — انتخاب چندتایی از هشت موضوع در اولین اجرا، و قابل ویرایش از تنظیمات
  (خالی گذاشتن در ویرایش یعنی «همهٔ موضوعات»).
- **ماندگاری** — علاقه‌مندی‌ها، ذخیره‌ها و تم بعد از بستن اپ می‌مانند؛ splash تا خوانده
  شدن آن‌ها صبر می‌کند تا کاربر برگشتی هیچ‌وقت onboarding را نبیند.
- **ذخیره‌شده‌ها** — فهرست تصویری با باز کردن، حذف و «بازگرداندن» (undo) در همان جای قبلی.
- **تنظیمات** — تم (روشن/تاریک/سیستم)، ویرایش موضوعات، دربارهٔ دانک. فید و صفحهٔ جزئیات
  همیشه تیره‌اند (هنر هیروها برای زمینهٔ تیره ساخته شده)؛ تم انتخابی روی بقیهٔ صفحه‌ها اعمال می‌شود.
- **۳۲ دانک فارسی** در هشت دسته (چهار دانک در هر دسته)، با متن کامل و منبع.
- RTL کامل، تایپوگرافی فارسی، اعداد فارسی، edge-to-edge.

### نیست (عمداً)

- هرگونه شبکه — اپ حتی مجوز `INTERNET` هم نمی‌گیرد. پیوند منبع با Intent به مرورگر
  سپرده می‌شود.
- کامنت، فالوور، پروفایل، اشتراک‌گذاری، شمارندهٔ بازدید یا واکنش اجتماعی.

---

## ساختار

```
app/src/main/java/ir/danak/app/
├── MainActivity.kt
├── model/      Danak, DanakSection, Category, DanakImage, ThemeMode
├── data/       MockDanaks — تنها منبع محتوا (static)
└── ui/
    ├── DanakApp.kt        NavHost و مسیرها
    ├── DanakViewModel.kt  تنها ViewModel اپ
    ├── theme/             رنگ، تایپوگرافی، شکل، accent دسته‌ها
    ├── components/        HeroImage، SaveChipButton، CategoryChip، SourceLink، …
    ├── screens/           feed · detail · interests · saved · settings
    └── util/              PersianText — اعداد و زمان مطالعه
```

معماری عمداً ساده است: بدون repository روی دادهٔ استاتیک، بدون use-case، بدون لایهٔ
domain و بدون DI framework. یک ViewModel در سطح Activity، چون حجم state دقیقاً همین
اندازه است.

### ناوبری

سه مقصد اصلی داریم اما **bottom bar نداریم**: یک نوار شفاف در بالای فید با نشان «دانَک»
و دو آیکون آرام (ذخیره‌شده‌ها، تنظیمات). دلیلش در خود صورت مسئله است — هیچ عنصر ناوبری
نباید با فید رقابت کند، و یک نوار پایینی دائمی ۸۰dp از ارتفاع هر دانک را می‌گرفت.

---

## دو تصمیم که ارزش توضیح دارند

### تصاویر

هیچ منبع تصویری در محیط توسعه در دسترس نبود، پس به‌جای استاک‌فوتوی ژنریک، ۳۲ هیرو به
صورت **مولد** ساخته شده‌اند — هر کدام با یک motif متناسب با موضوع خودش: شبکهٔ گره برای
حافظه، مدار و ذره برای قفل‌شدگی کشندی، ستون‌های فروریخته برای سقوط روم، شانهٔ عسل برای
ماندگاری عسل. پالت مشترک و تیره است تا فید یک محصول واحد به نظر برسد.

تصاویر با نسبت ۹:۱۶ (۱۰۸۰×۱۹۲۰) تولید می‌شوند تا در فید تمام‌صفحه، `ContentScale.Crop`
کمترین برش را بزند؛ خود motif روی بوم ۲:۳ کشیده و بعد روی زمینهٔ بلندتر ترکیب می‌شود تا
با تغییر نسبت خروجی کشیده نشود.

اسکریپت تولید در `tools/generate_hero_art.py` است:

```bash
pip install Pillow numpy
python3 tools/generate_hero_art.py app/src/main/res/drawable-nodpi tools/heroes.json
```

**لایهٔ تصویر آمادهٔ ریموت است.** هر هیرو از طریق `DanakImage` رندر می‌شود:

```kotlin
sealed interface DanakImage {
    val model: Any                 // مستقیم به Coil می‌رود
    value class Local(@DrawableRes val resId: Int) : DanakImage
    value class Remote(val url: String) : DanakImage
}
```

برای رفتن به تصاویر ریموت کافی است مقدار `image` در `MockDanaks` عوض شود و مجوز
`INTERNET` به manifest اضافه شود — هیچ composable ای تغییر نمی‌کند.

### منابع

هر دانک به مقالهٔ ویکی‌پدیای همان مفهوم لینک می‌دهد — نسخهٔ فارسی هرجا وجود دارد، و
انگلیسی در غیر این صورت. `tools/check_links.py` در CI همهٔ لینک‌ها را باز می‌کند و اگر
حتی یکی خراب باشد، job مربوطه قرمز می‌شود؛ با `--suggest-fa` هم مقالهٔ فارسی معادل هر
مقالهٔ انگلیسی را از API ویکی‌پدیا پیدا می‌کند.

---

## کیفیت

- تست واحد: `PersianTextTest`، `DanakViewModelTest` (شامل بقای state بعد از راه‌اندازی
  دوباره و undo)، `DanakStoreTest`، `MockDanaksTest` (یکتایی id، پوشش دسته‌ها، کامل بودن
  هر دانک، پخش‌شدن دسته‌ها در فید).
- تست Compose: `FeedScreenTest`، `InterestsScreenTest`، و `AppJourneyTest` که کل سفر
  کاربر را روی اکتیویتی واقعی طی می‌کند و از هر صفحه اسکرین‌شات و audit دسترس‌پذیری می‌گیرد.
- بدون dependency بلااستفاده، بدون import مرده و بدون TODO برای قابلیت‌های V0.

---

## مجوز فونت

Vazirmatn تحت SIL Open Font License 1.1 منتشر شده؛ متن کامل مجوز در
`third_party/Vazirmatn-OFL.txt`.
