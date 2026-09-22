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
| زبان | Kotlin 2.1.21 |
| UI | Jetpack Compose + Material 3 |
| ناوبری | Navigation Compose |
| حالت | ViewModel + StateFlow + Coroutines |
| تصاویر | Coil |
| Build | Gradle Kotlin DSL + version catalog |
| فونت | Vazirmatn (SIL OFL 1.1) |

`compileSdk`/`targetSdk` = 36، `minSdk` = 26 (Android 8.0).

بدون backend، بدون احراز هویت، بدون Room/Retrofit/Firebase، بدون analytics و بدون
هیچ API هوش مصنوعی. تمام محتوا محلی است.

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

پیش‌نیازها: JDK 17 یا بالاتر و Android SDK با API 36.
اگر از Android Studio استفاده می‌کنی، پروژه را باز کن و SDK لازم خودکار دانلود می‌شود.

> **توجه دربارهٔ build**
> این پروژه در محیطی نوشته شده که دسترسی به `dl.google.com` (میزبان Google Maven و
> Android SDK) توسط سیاست شبکه مسدود بود؛ بنابراین APK در آن محیط ساخته نشده است.
> کد با کامپایلر Kotlin از نظر ساختاری بررسی شده، اما اولین `./gradlew assembleDebug`
> روی ماشین تو، اولین build واقعی پروژه خواهد بود.

---

## دامنهٔ V0

### هست

- **فید** — `VerticalPager` تمام‌صفحه؛ هر swipe دقیقاً یک دانک، با snap، پارالاکس ملایم
  روی تصویر و هپتیک ظریف هنگام تثبیت صفحه.
- **جزئیات** — «بیشتر بدان» لایهٔ عمیق‌تر همان دانک را باز می‌کند: همان تصویر، همان
  تیتر، متن کامل با زیرعنوان‌ها، منبع و ذخیره.
- **علاقه‌مندی‌ها** — انتخاب چندتایی از هشت موضوع در اولین اجرا، و قابل ویرایش از تنظیمات.
- **ذخیره‌شده‌ها** — فهرست تصویری با باز کردن و حذف.
- **تنظیمات** — تم (روشن/تاریک/سیستم)، ویرایش موضوعات، دربارهٔ دانک.
- **۲۴ دانک فارسی** در هشت دسته، با متن کامل و منبع.
- RTL کامل، تایپوگرافی فارسی، اعداد فارسی، edge-to-edge.

### نیست (عمداً)

- ماندگاری بین اجراها — علاقه‌مندی‌ها و ذخیره‌شده‌ها فقط در همان session زنده‌اند.
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

هیچ منبع تصویری در محیط توسعه در دسترس نبود، پس به‌جای استاک‌فوتوی ژنریک، ۲۴ هیرو به
صورت **مولد** ساخته شده‌اند — هر کدام با یک motif متناسب با موضوع خودش: شبکهٔ گره برای
حافظه، مدار و ذره برای قفل‌شدگی کشندی، ستون‌های فروریخته برای سقوط روم، شانهٔ عسل برای
ماندگاری عسل. پالت مشترک و تیره است تا فید یک محصول واحد به نظر برسد.

اسکریپت تولید در `tools/generate_hero_art.py` است:

```bash
pip install Pillow numpy
python3 tools/generate_hero_art.py app/src/main/res/drawable tools/heroes.json
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

هر دانک به مقالهٔ ویکی‌پدیای همان مفهوم لینک می‌دهد. این یک تصمیم آگاهانهٔ V0 است:
لینک‌های واقعی، پایدار و قابل بررسی، به‌جای ساختن URL های جعلی روی سایت ناشران.
جایگزین کردن آن‌ها با منابع اختصاصی، یک کار محتوایی است نه کد.

---

## کیفیت

- تست واحد: `PersianTextTest`، `DanakViewModelTest`، `MockDanaksTest`
  (یکتایی id، پوشش دسته‌ها، کامل بودن هر دانک، پخش‌شدن دسته‌ها در فید).
- تست Compose: `FeedScreenTest`، `InterestsScreenTest`.
- بدون dependency بلااستفاده، بدون import مرده و بدون TODO برای قابلیت‌های V0.

---

## مجوز فونت

Vazirmatn تحت SIL Open Font License 1.1 منتشر شده؛ متن کامل مجوز در
`third_party/Vazirmatn-OFL.txt`.
