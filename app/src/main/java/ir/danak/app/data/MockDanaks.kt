package ir.danak.app.data

import ir.danak.app.R
import ir.danak.app.model.Category
import ir.danak.app.model.Danak
import ir.danak.app.model.DanakImage
import ir.danak.app.model.DanakSection
import ir.danak.app.model.PhotoCredit

/**
 * V0 ships its content as a static list — there is no backend yet, and wrapping twenty-four
 * constants in a repository would add a layer without adding a capability.
 *
 * Every entry links to the Wikipedia article for the concept it explains — the Persian
 * edition wherever one exists, English otherwise. Real, stable, checkable links beat
 * invented article URLs on a publisher's site, and tools/check_links.py verifies all of
 * them in CI.
 */
object MockDanaks {

    private val psychologyAndScience: List<Danak> = listOf(

        // ---------------------------------------------------------- روان‌شناسی
        Danak(
            id = "zeigarnik",
            category = Category.Psychology,
            title = "چرا مغز کارهای ناتمام را بهتر به خاطر می‌سپارد؟",
            summary = "مغز کار نیمه‌تمام را مثل پرونده‌ای باز نگه می‌دارد؛ برای همین کارهای ناتمام مدام " +
                "به ذهنمان برمی‌گردند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "بلوما زایگارنیک، روان‌شناس اهل لیتوانی، در دههٔ ۱۹۲۰ متوجه چیز " +
                        "عجیبی در یک کافه شد: پیشخدمت‌ها سفارش‌های پرداخت‌نشده را با جزئیات " +
                        "کامل به یاد داشتند، اما همین که حساب تسویه می‌شد، سفارش را تقریباً " +
                        "فراموش می‌کردند. او این مشاهده را به آزمایشگاه برد و دید افرادی که " +
                        "وسط انجام یک تکلیف متوقف می‌شوند، آن تکلیف را حدود دو برابر بهتر از " +
                        "تکالیف تمام‌شده به یاد می‌آورند.",
                ),
                DanakSection(
                    heading = "چرا این اتفاق می‌افتد؟",
                    body = "مغز برای حل مسئله ساخته شده است. کار ناتمام مانند یک پرسش بی‌پاسخ " +
                        "در حافظهٔ فعال باقی می‌ماند و منابع توجه را به خود اختصاص می‌دهد. " +
                        "این تنش شناختی تا زمانی که کار بسته شود ادامه دارد — و دقیقاً به " +
                        "همین دلیل است که یادآوری‌اش آسان‌تر می‌شود. البته تکرار این آزمایش " +
                        "همیشه همان نتیجه را نداده است؛ آنچه پایدارتر دیده شده، میل ذهن به " +
                        "برگشتن و تمام کردن کار نیمه‌تمام است.",
                ),
                DanakSection(
                    heading = "چطور از آن استفاده کنیم؟",
                    body = "اگر هنگام مطالعه یا نوشتن گیر کرده‌ای، به‌جای تمام کردن یک بخش، " +
                        "عمداً وسط آن توقف کن؛ بازگشت به کار آسان‌تر می‌شود چون ذهنت هنوز " +
                        "پرونده را باز نگه داشته. برعکس، اگر فکرهای ناتمام نمی‌گذارند بخوابی، " +
                        "نوشتن یک برنامهٔ مشخص برای فردا همان حس «بسته شدن» را می‌سازد و " +
                        "تنش را کم می‌کند.",
                ),
            ),
            readingSeconds = 45,
            image = DanakImage.Local(R.drawable.hero_zeigarnik),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/اثر_زیگارنیک",
            keyTakeaway = "برای ادامه دادن آسان‌تر، وسط کار دست بکش؛ برای آرام شدن، قدم بعدی را روی کاغذ " +
                "بنویس.",
            photoCredit = PhotoCredit(
                author = "Shixart1985",
                license = "CC BY 2.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Person_writing_in_notebook_while_using_laptop_at_a_modern_workspace.jpg",
            ),
        ),

        Danak(
            id = "dunning_kruger",
            category = Category.Psychology,
            title = "چرا کم‌مهارت‌ها توانایی خود را دست بالا می‌گیرند؟",
            summary = "برای فهمیدن اینکه در کاری ضعیفی، همان مهارتی لازم است که نداری؛ برای همین " +
                "تازه‌کارها خود را دست بالا می‌گیرند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "دیوید دانینگ و جاستین کروگر در سال ۱۹۹۹ از دانشجویان خواستند پس " +
                        "از یک آزمون، حدس بزنند نمره‌شان نسبت به بقیه چطور بوده است. کسانی " +
                        "که در پایین‌ترین بخش قرار داشتند، به‌طور میانگین خود را بالاتر از " +
                        "متوسط ارزیابی کردند. آن‌ها مطمئن‌ترینِ جمع نبودند — ماهرترها هنوز " +
                        "خود را بالاتر می‌دیدند — اما فاصلهٔ حدسشان با واقعیت از همه بیشتر بود.",
                ),
                DanakSection(
                    heading = "ریشهٔ مسئله",
                    body = "ارزیابی کیفیت کار، خودش یک مهارت است و معمولاً همان مهارتی است که " +
                        "برای انجام درست کار لازم داری. کسی که قواعد را نمی‌شناسد، خطاهای " +
                        "خودش را هم نمی‌بیند؛ پس معیاری برای سنجش ندارد جز حس کلی‌اش.",
                ),
                DanakSection(
                    heading = "روی دیگر سکه",
                    body = "متخصص‌ها معمولاً مسیر مخالف را می‌روند: چون می‌دانند موضوع چقدر " +
                        "گسترده است، توانایی خود را کمتر از واقع تخمین می‌زنند و فرض " +
                        "می‌کنند کارهایی که برایشان آسان است، برای بقیه هم آسان است. " +
                        "در عمل، تنها راه بیرون آمدن از این حلقه بازخورد بیرونی است: " +
                        "آزمون، بررسی کد، یا کسی که کارت را نقد کند. برخی پژوهشگران بخشی از " +
                        "این الگو را اثری آماری می‌دانند، اما خودِ دست‌بالاگرفتن در " +
                        "کم‌مهارت‌ها بارها دیده شده است.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_dunning_kruger),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/اثر_دانینگ–کروگر",
            keyTakeaway = "حس اطمینان معیار مهارت نیست؛ فقط بازخورد بیرونی حلقه را می‌شکند.",
            photoCredit = PhotoCredit(
                author = "Mænsard vokser",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Hiker_on_path_501_from_Monte_Alben_to_Monte_della_Croce_-_Bergamo,_Lombardy,_Italy_-_2020-09-13.jpg",
            ),
        ),

        Danak(
            id = "confirmation_bias",
            category = Category.Psychology,
            title = "چرا فقط چیزی را می‌بینیم که از قبل باور داریم؟",
            summary = "ذهن ما شواهد را بی‌طرفانه جمع نمی‌کند؛ سراغ چیزی می‌رود که باور فعلی‌اش را تأیید " +
                "کند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "پیتر واسون در دههٔ ۱۹۶۰ آزمایشی طراحی کرد که در آن افراد باید " +
                        "قاعده‌ای پنهان پشت یک دنبالهٔ عددی را کشف می‌کردند. تقریباً همه " +
                        "تنها نمونه‌هایی را آزمودند که فرضیهٔ خودشان را تأیید می‌کرد، " +
                        "نه نمونه‌هایی که می‌توانست آن را رد کند.",
                ),
                DanakSection(
                    heading = "چرا این خطا اینقدر سرسخت است؟",
                    body = "جست‌وجوی تأیید از نظر شناختی ارزان است و حس خوبی دارد؛ جست‌وجوی " +
                        "رد کردن پرهزینه است و ناخوشایند. این سوگیری در هر سه مرحله عمل " +
                        "می‌کند: در انتخاب اینکه چه چیزی را بخوانیم، در تفسیر آنچه " +
                        "می‌خوانیم، و در اینکه بعداً چه چیزی را به یاد بیاوریم.",
                ),
                DanakSection(
                    heading = "پادزهر عملی",
                    body = "به‌جای پرسیدن «چه شواهدی نظر من را تأیید می‌کند؟» بپرس «چه چیزی " +
                        "باید درست باشد تا نظر من غلط از آب دربیاید؟». در تصمیم‌های مهم، " +
                        "نوشتن صریح فرض‌ها و شرط ابطال‌شان پیش از جمع‌آوری داده، بیشترین " +
                        "اثر را دارد.",
                ),
            ),
            readingSeconds = 55,
            image = DanakImage.Local(R.drawable.hero_confirmation),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/سوگیری_تأییدی",
            keyTakeaway = "بپرس «چه چیزی باید درست باشد تا من اشتباه کرده باشم؟» — نه «چه چیزی حرفم را تأیید " +
                "می‌کند؟»",
            photoCredit = PhotoCredit(
                author = "Flazingo Photos",
                license = "CC BY-SA 2.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Job_Listings.jpg",
            ),
        ),

        // ---------------------------------------------------------- علم
        Danak(
            id = "blue_sky",
            category = Category.Science,
            title = "چرا آسمان آبی است اما غروب قرمز؟",
            summary = "هوا نور آبی را حدود شش برابر بیشتر از قرمز پخش می‌کند؛ همین آبی آسمان و قرمزی غروب " +
                "را توضیح می‌دهد.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "نور خورشید سفید است، یعنی همهٔ رنگ‌ها را با هم دارد. وقتی این نور " +
                        "به مولکول‌های بسیار کوچک هوا برخورد می‌کند، پدیده‌ای به نام " +
                        "«پراکندگی ریلی» رخ می‌دهد: شدت پراکندگی با توان چهارم معکوس " +
                        "طول موج متناسب است.",
                ),
                DanakSection(
                    heading = "عدد ماجرا",
                    body = "طول موج نور آبی حدود ۴۵۰ نانومتر و قرمز حدود ۷۰۰ نانومتر است. " +
                        "نسبت توان چهارم این دو تقریباً شش می‌شود؛ یعنی نور آبی حدود شش " +
                        "برابر بیشتر از قرمز در آسمان پخش می‌شود و از هر جهت به چشم ما " +
                        "می‌رسد.",
                ),
                DanakSection(
                    heading = "پس چرا غروب قرمز است؟",
                    body = "هنگام غروب، نور خورشید باید مسیر بسیار بلندتری را در جو طی کند. " +
                        "در این مسیر طولانی، آبی آنقدر پراکنده می‌شود که از خط دید ما خارج " +
                        "می‌شود و آنچه مستقیم به چشم می‌رسد، عمدتاً نارنجی و قرمز است.",
                ),
            ),
            readingSeconds = 45,
            image = DanakImage.Local(R.drawable.hero_blue_sky),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/پراکنش_ریلی",
            keyTakeaway = "آبی و قرمز آسمان هر دو از یک پدیده‌اند: پراکندگی ریلی، فقط با طول مسیر متفاوت نور.",
            photoCredit = PhotoCredit(
                author = "Basile Morin",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Red_clouds_over_Mekong_banks_with_dwellings_and_pirogues_at_sunrise_in_Don_Det_Laos.jpg",
            ),
        ),

        Danak(
            id = "entropy",
            category = Category.Science,
            title = "چرا زمان فقط به یک سمت می‌رود؟",
            summary = "قوانین فیزیک تقریباً همه به جهت زمان بی‌اعتنا هستند؛ فقط آنتروپی به زمان جهت " +
                "می‌دهد — و دلیلش آمار است.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "فیلم برخورد دو توپ بیلیارد را برعکس پخش کن؛ هیچ قانون فیزیکی " +
                        "نقض نمی‌شود. اما فیلم شکستن یک لیوان را برعکس پخش کن؛ بلافاصله " +
                        "می‌فهمی چیزی غلط است. تفاوت در تعداد حالت‌هاست.",
                ),
                DanakSection(
                    heading = "آنتروپی یعنی شمارش",
                    body = "آنتروپی معیاری است از تعداد آرایش‌های میکروسکوپی که یک وضعیت " +
                        "ماکروسکوپی را می‌سازند. برای «لیوان سالم» تعداد این آرایش‌ها " +
                        "بسیار کم است؛ برای «لیوان شکسته» نجومی زیاد. سیستم به سمت " +
                        "حالت‌هایی می‌رود که راه‌های بیشتری برای تحقق دارند — نه چون " +
                        "نیرویی آن را هل می‌دهد، بلکه چون آن حالت‌ها بی‌نهایت محتمل‌ترند.",
                ),
                DanakSection(
                    heading = "پیامد",
                    body = "حس ما از گذشت زمان، در واقع حس افزایش آنتروپی است. به همین دلیل " +
                        "گذشته را به یاد می‌آوریم اما آینده را نه: ثبت خاطره خودش فرایندی " +
                        "است که آنتروپی را افزایش می‌دهد.",
                ),
            ),
            readingSeconds = 60,
            image = DanakImage.Local(R.drawable.hero_entropy),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/قانون_دوم_ترمودینامیک",
            keyTakeaway = "بی‌نظمی بیشتر می‌شود چون حالت‌های بی‌نظم بسیار بیشترند، نه چون نیرویی سیستم را هل " +
                "می‌دهد.",
            photoCredit = PhotoCredit(
                author = "Dietmar Rabich",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:D%C3%BClmen,_R%C3%B6dder,_G%C3%A4rtnerei_Wessinghage_--_2021_--_0364.jpg",
            ),
        ),

        Danak(
            id = "tidal_locking",
            category = Category.Science,
            title = "چرا همیشه یک روی ماه را می‌بینیم؟",
            summary = "ماه با همان سرعتی دور خودش می‌چرخد که دور زمین؛ نتیجهٔ ترمزی که گرانش زمین روی آن گذاشت.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "ماه در حدود ۲۷٫۳ روز یک بار دور زمین می‌گردد و در همان ۲۷٫۳ روز " +
                        "یک بار هم دور محور خودش می‌چرخد. نتیجه این است که همواره یک نیم‌کرهٔ " +
                        "آن رو به ما است. به این وضعیت «قفل‌شدگی کشندی» می‌گویند.",
                ),
                DanakSection(
                    heading = "چطور به اینجا رسید؟",
                    body = "گرانش زمین روی نزدیک‌ترین بخش ماه قوی‌تر از دورترین بخش آن اثر " +
                        "می‌گذارد و ماه را کمی کشیده می‌کند. وقتی ماه سریع‌تر می‌چرخید، این " +
                        "برآمدگی دائماً جابه‌جا می‌شد و اصطکاک داخلی ایجاد می‌کرد. این " +
                        "اصطکاک مثل ترمز عمل کرد تا چرخش ماه با گردشش هم‌زمان شد.",
                ),
                DanakSection(
                    heading = "یک نکتهٔ مهم",
                    body = "«سمت تاریک ماه» اصطلاح غلطی است. نیم‌کرهٔ پنهان ماه هم دقیقاً به " +
                        "اندازهٔ نیم‌کرهٔ پیدا نور خورشید می‌گیرد؛ فقط از زمین دیده نمی‌شود. " +
                        "ضمناً همین فرایند روی زمین هم کار می‌کند و شبانه‌روز ما را هر قرن " +
                        "حدود ۱٫۸ میلی‌ثانیه طولانی‌تر می‌کند.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_tidal_lock),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/قفل_کشندی",
            keyTakeaway = "«سمت تاریک ماه» وجود ندارد؛ فقط سمتی هست که از زمین دیده نمی‌شود.",
            photoCredit = PhotoCredit(
                author = "W.carter",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Full_moon_over_Gullmarn_fjord_at_Holma_Marina_2.jpg",
            ),
        ),
    )

    private val technologyAndProgramming: List<Danak> = listOf(

        Danak(
            id = "public_key",
            category = Category.Technology,
            title = "چطور بدون ردوبدل کردن رمز، رمز می‌سازیم؟",
            summary = "رمزنگاری کلید عمومی اجازه می‌دهد بدون ردوبدل کردن هیچ رمزی، امن با هم حرف بزنیم.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "در رمزنگاری کلید عمومی، هر نفر دو کلید دارد: یک کلید عمومی که " +
                        "آزادانه منتشر می‌شود و یک کلید خصوصی که هرگز جایی نمی‌رود. هر " +
                        "چیزی که با کلید عمومی قفل شود، تنها با کلید خصوصی متناظرش باز " +
                        "می‌شود.",
                ),
                DanakSection(
                    heading = "ایدهٔ اصلی: عملیات یک‌طرفه",
                    body = "این روش بر کارهایی تکیه دارد که در یک جهت آسان و در جهت معکوس " +
                        "به‌شدت پرهزینه‌اند. ضرب دو عدد اول بزرگ در کسری از ثانیه انجام " +
                        "می‌شود، اما تجزیهٔ حاصل‌ضرب به همان دو عدد، با سخت‌افزار امروزی " +
                        "عملاً شدنی نیست. امنیت از همین عدم تقارن می‌آید.",
                ),
                DanakSection(
                    heading = "کجا از آن استفاده می‌کنی؟",
                    body = "هر بار که آدرسی با HTTPS باز می‌کنی، همین سازوکار پشت پرده اجرا " +
                        "می‌شود: مرورگر با کلید عمومی سرور یک کلید موقت رد و بدل می‌کند و " +
                        "بقیهٔ ارتباط با آن کلید سریع‌تر ادامه پیدا می‌کند. امضای دیجیتال، " +
                        "SSH و کیف‌پول‌های رمزارز هم بر همین پایه‌اند.",
                ),
            ),
            readingSeconds = 55,
            image = DanakImage.Local(R.drawable.hero_public_key),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/رمزنگاری_کلید_عمومی",
            keyTakeaway = "امنیت اینترنت روی عملیاتی بنا شده که انجامش آسان و برگرداندنش عملاً ناممکن است.",
            photoCredit = PhotoCredit(
                author = "Trougnouf",
                license = "CC BY 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Solex_99_30_padlock_with_keys_(DSCF2659).jpg",
            ),
        ),

        Danak(
            id = "battery_aging",
            category = Category.Technology,
            title = "چرا باتری گوشی با گذشت زمان ضعیف می‌شود؟",
            summary = "باتری لیتیوم-یونی با هر شارژ کمی فرسوده می‌شود؛ گرما و شارژ کامل این روند را تند " +
                "می‌کنند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "در باتری لیتیوم-یونی، یون‌های لیتیوم میان دو الکترود رفت و برگشت " +
                        "می‌کنند. اما در هر چرخه، بخش کوچکی از این یون‌ها در لایه‌ای به نام " +
                        "SEI روی سطح آند به دام می‌افتند و دیگر در کار شرکت نمی‌کنند. " +
                        "این لایه به‌تدریج ضخیم‌تر می‌شود.",
                ),
                DanakSection(
                    heading = "دو عامل تسریع‌کننده",
                    body = "گرما سرعت این واکنش‌ها را چند برابر می‌کند؛ باتری در ۴۰ درجه " +
                        "بسیار سریع‌تر از ۲۰ درجه فرسوده می‌شود. عامل دوم، ماندن طولانی در " +
                        "شارژ کامل است: ولتاژ بالا فشار شیمیایی بیشتری به الکترودها وارد " +
                        "می‌کند.",
                ),
                DanakSection(
                    heading = "در عمل یعنی چه؟",
                    body = "نگه داشتن شارژ در محدودهٔ حدود ۲۰ تا ۸۰ درصد و پرهیز از گرم شدن " +
                        "گوشی هنگام شارژ، عمر مفید باتری را محسوس‌تر از هر ترفند دیگری " +
                        "بالا می‌برد. قابلیت «شارژ بهینه» در گوشی‌های امروزی دقیقاً همین " +
                        "کار را خودکار می‌کند.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_battery),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/باتری_یون‌لیتیم",
            keyTakeaway = "شارژ بین ۲۰ تا ۸۰ درصد و دور از گرما، عمر باتری را بیشتر از هر ترفندی بالا می‌برد.",
            photoCredit = PhotoCredit(
                author = "LG전자",
                license = "CC BY 2.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Inductive_charging_of_LG_smartphone_(2).jpg",
            ),
        ),

        Danak(
            id = "cdn",
            category = Category.Technology,
            title = "چرا سایت‌ها از نزدیک‌ترین نقطه به تو بارگذاری می‌شوند؟",
            summary = "سرعت نور یک سقف است؛ شبکهٔ توزیع محتوا به‌جای جنگیدن با آن، داده را نزدیک تو " +
                "می‌آورد.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "نور در فیبر نوری حدود ۲۰۰ هزار کیلومتر بر ثانیه پیش می‌رود. " +
                        "یک رفت و برگشت میان تهران و یک سرور در کالیفرنیا، حتی در حالت " +
                        "ایده‌آل و بدون هیچ تأخیر پردازشی، حدود ۱۲۰ میلی‌ثانیه طول " +
                        "می‌کشد — و هیچ بهینه‌سازی نرم‌افزاری این عدد را کم نمی‌کند.",
                ),
                DanakSection(
                    heading = "راه‌حل: نزدیک‌تر کردن داده",
                    body = "شبکهٔ توزیع محتوا (CDN) نسخه‌هایی از فایل‌های ثابت — تصویر، " +
                        "جاوااسکریپت، ویدیو — را روی صدها سرور در سراسر دنیا نگه می‌دارد. " +
                        "وقتی درخواستی می‌فرستی، سامانهٔ مسیریابی تو را به نزدیک‌ترین " +
                        "نسخه هدایت می‌کند.",
                ),
                DanakSection(
                    heading = "سود دوم",
                    body = "کاهش تأخیر فقط نیمی از ماجراست. چون بیشتر درخواست‌ها هرگز به " +
                        "سرور اصلی نمی‌رسند، بار آن سرور به‌شدت کم می‌شود و در برابر " +
                        "هجوم ناگهانی ترافیک مقاوم‌تر می‌ماند.",
                ),
            ),
            readingSeconds = 45,
            image = DanakImage.Local(R.drawable.hero_cdn),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/شبکه_تحویل_محتوا",
            keyTakeaway = "کوتاه‌ترین مسیر شبکه، مسیری است که هرگز طی نشود.",
            photoCredit = PhotoCredit(
                author = "PiDatacenters",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Racks_Amravati_Data_Center.jpg",
            ),
        ),

        Danak(
            id = "big_o",
            category = Category.Programming,
            title = "چرا یک کد سریع روی داده‌های بزرگ کند می‌شود؟",
            summary = "سرعت الگوریتم را با ثانیه نمی‌سنجند، با آهنگ رشدش همراه بزرگ شدن ورودی.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "الگوریتمی که برای هزار رکورد یک میلی‌ثانیه وقت می‌گیرد، ممکن است " +
                        "برای یک میلیون رکورد یک ثانیه بگیرد یا پانزده دقیقه — بسته به " +
                        "اینکه زمانش چطور با اندازهٔ ورودی رشد می‌کند.",
                ),
                DanakSection(
                    heading = "چند مرتبهٔ رایج",
                    body = "در مرتبهٔ O(۱) زمان اجرا مستقل از اندازهٔ ورودی است، مثل خواندن " +
                        "یک کلید از جدول درهم‌سازی. در O(log n) هر گام مسئله را نصف " +
                        "می‌کند، مثل جست‌وجوی دودویی. در O(n) کل ورودی یک بار پیمایش " +
                        "می‌شود و در O(n²) برای هر عنصر، دوباره کل ورودی پیمایش می‌شود — " +
                        "همان حلقهٔ تو در تو که روی داده‌های بزرگ فاجعه می‌سازد.",
                ),
                DanakSection(
                    heading = "نکتهٔ مهمی که اغلب فراموش می‌شود",
                    body = "این نماد ضرایب ثابت را نادیده می‌گیرد. برای ورودی‌های کوچک، یک " +
                        "الگوریتم O(n²) ساده می‌تواند از یک O(n log n) پیچیده سریع‌تر " +
                        "باشد. به همین دلیل بسیاری از پیاده‌سازی‌های مرتب‌سازی استاندارد " +
                        "برای آرایه‌های کوتاه به روش ساده‌تر برمی‌گردند.",
                ),
            ),
            readingSeconds = 60,
            image = DanakImage.Local(R.drawable.hero_big_o),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/نماد_O_بزرگ",
            keyTakeaway = "حلقهٔ تو در تو روی دادهٔ کم بی‌خطر است و روی دادهٔ زیاد فاجعه.",
            photoCredit = PhotoCredit(
                author = "Markus Spiske",
                license = "CC0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Code_on_computer_monitor_(Unsplash).jpg",
            ),
        ),

        Danak(
            id = "floating_point",
            category = Category.Programming,
            title = "چرا ۰٫۱ + ۰٫۲ در کامپیوتر برابر ۰٫۳ نیست؟",
            summary = "کامپیوتر اعشار را در مبنای دو ذخیره می‌کند و ۰٫۱ در مبنای دو هیچ‌وقت تمام نمی‌شود.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "در اکثر زبان‌های برنامه‌نویسی، حاصل ۰٫۱ + ۰٫۲ برابر با " +
                        "۰٫۳۰۰۰۰۰۰۰۰۰۰۰۰۰۰۰۴ است. این باگ زبان نیست؛ نتیجهٔ مستقیم " +
                        "استاندارد IEEE 754 است که تقریباً همهٔ پردازنده‌ها از آن " +
                        "پیروی می‌کنند.",
                ),
                DanakSection(
                    heading = "چرا؟",
                    body = "همان‌طور که یک‌سوم در مبنای ده می‌شود ۰٫۳۳۳... و هرگز تمام " +
                        "نمی‌شود، عدد ۰٫۱ در مبنای دو یک کسر تناوبی بی‌پایان است. چون " +
                        "حافظه محدود است، این عدد در نزدیک‌ترین مقدار قابل نمایش گرد " +
                        "می‌شود و خطای کوچکی باقی می‌ماند که در محاسبات انباشته می‌شود.",
                ),
                DanakSection(
                    heading = "قاعدهٔ عملی",
                    body = "هرگز پول را در نوع اعشاری ذخیره نکن. مبالغ را به کوچک‌ترین واحد " +
                        "(ریال) به‌صورت عدد صحیح نگه دار، یا از نوع دهدهی دقیق مثل " +
                        "BigDecimal استفاده کن. برای مقایسهٔ اعشارها هم به‌جای تساوی " +
                        "دقیق، اختلاف را با یک آستانهٔ کوچک بسنج.",
                ),
            ),
            readingSeconds = 55,
            image = DanakImage.Local(R.drawable.hero_float_point),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/IEEE_۷۵۴",
            keyTakeaway = "پول را در نوع اعشاری ذخیره نکن؛ عدد صحیح یا نوع دهدهی دقیق به کار ببر.",
            photoCredit = PhotoCredit(
                author = "Coyau",
                license = "CC BY-SA 3.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Aurora_electronic_calculator_DT210_05.jpg",
            ),
        ),

        Danak(
            id = "db_index",
            category = Category.Programming,
            title = "ایندکس پایگاه داده دقیقاً چه کار می‌کند؟",
            summary = "ایندکس کار فهرست انتهای کتاب را می‌کند: به‌جای خواندن کل جدول، مستقیم به ردیف " +
                "می‌رسد.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "بیشتر ایندکس‌ها ساختاری به نام B-tree دارند: درختی متوازن که " +
                        "کلیدها را مرتب نگه می‌دارد. جست‌وجو در جدولی با ده میلیون ردیف " +
                        "به‌جای ده میلیون مقایسه، تنها به حدود بیست و چند گام نیاز دارد.",
                ),
                DanakSection(
                    heading = "هزینه‌ای که نادیده گرفته می‌شود",
                    body = "ایندکس رایگان نیست. هر درج، حذف یا به‌روزرسانی باید ایندکس را هم " +
                        "به‌روز کند، پس نوشتن کندتر می‌شود و فضای دیسک بیشتری مصرف " +
                        "می‌شود. جدولی با ده ایندکس ممکن است در خواندن سریع اما در نوشتن " +
                        "به‌طور محسوسی کند باشد.",
                ),
                DanakSection(
                    heading = "نکتهٔ ترتیب ستون‌ها",
                    body = "در ایندکس چندستونی، ترتیب اهمیت حیاتی دارد. ایندکسی روی " +
                        "(کاربر، تاریخ) برای فیلتر کردن بر اساس کاربر عالی است، اما برای " +
                        "فیلتر کردن فقط بر اساس تاریخ تقریباً بی‌فایده — درست مثل دفترچهٔ " +
                        "تلفنی که بر اساس نام خانوادگی مرتب شده و تو فقط نام کوچک را " +
                        "می‌دانی.",
                ),
            ),
            readingSeconds = 60,
            image = DanakImage.Local(R.drawable.hero_db_index),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/فهرست_(پایگاه_داده)",
            keyTakeaway = "هر ایندکس خواندن را سریع و نوشتن را کند می‌کند؛ فقط برای پرسش‌های واقعی بساز.",
            photoCredit = PhotoCredit(
                author = "יעל י",
                license = "CC BY-SA 3.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Drawers_NLI.jpg",
            ),
        ),
    )

    private val economyAndHistory: List<Danak> = listOf(

        Danak(
            id = "opportunity_cost",
            category = Category.Economy,
            title = "هزینهٔ واقعی هر انتخاب، چیزی است که انتخاب نکردی",
            summary = "هزینهٔ واقعی هر انتخاب، بهترین گزینه‌ای است که به خاطرش از دست می‌دهی.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "فرض کن صد میلیون تومان داری. اگر با آن یک خودرو بخری، هزینه‌ات " +
                        "صد میلیون نیست؛ سودی است که همان مبلغ در بهترین گزینهٔ جایگزین " +
                        "برایت می‌ساخت. اگر آن جایگزین سالانه سی درصد بازده داشت، هزینهٔ " +
                        "واقعی خرید خودرو سی میلیون در سال است.",
                ),
                DanakSection(
                    heading = "زمان هم همین‌طور است",
                    body = "رایج‌ترین جایی که این مفهوم نادیده گرفته می‌شود، زمان است. " +
                        "پذیرفتن یک پروژهٔ جانبی که ماهی ده ساعت می‌گیرد، «رایگان» نیست؛ " +
                        "هزینه‌اش همان ده ساعتی است که می‌توانست صرف یادگیری مهارتی شود " +
                        "که درآمدت را چند برابر کند.",
                ),
                DanakSection(
                    heading = "چطور از آن استفاده کنیم؟",
                    body = "هنگام تصمیم‌گیری، همیشه گزینه را در برابر «بهترین جایگزین» " +
                        "بسنج، نه در برابر «هیچ کاری نکردن». اگر نتوانی بهترین جایگزین را " +
                        "نام ببری، هنوز اطلاعات کافی برای تصمیم نداری.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_opportunity),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/هزینه_فرصت",
            keyTakeaway = "هر گزینه را با بهترین جایگزینش بسنج، نه با «هیچ کاری نکردن».",
            photoCredit = PhotoCredit(
                author = "Alan Hughes",
                license = "CC BY-SA 2.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Crossroads_Signpost_-_geograph.org.uk_-_5217734.jpg",
            ),
        ),

        Danak(
            id = "rule_of_72",
            category = Category.Economy,
            title = "با یک تقسیم ساده بفهم پولت کی دو برابر می‌شود",
            summary = "عدد ۷۲ را بر نرخ سالانه تقسیم کن تا بفهمی چند سال طول می‌کشد مقدار دو برابر شود.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "با بازده سالانهٔ ۸ درصد، سرمایه در حدود ۷۲ تقسیم بر ۸ یعنی ۹ سال " +
                        "دو برابر می‌شود. با ۲۴ درصد، در حدود ۳ سال. این تقریب برای " +
                        "نرخ‌های حدود ۶ تا ۱۵ درصد خطای کمتر از یک درصد دارد.",
                ),
                DanakSection(
                    heading = "روی دیگر: تورم",
                    body = "همین قاعده نشان می‌دهد قدرت خرید با چه سرعتی نصف می‌شود. با تورم " +
                        "سالانهٔ ۳۶ درصد، ارزش پول نقد در حدود دو سال نصف می‌شود. " +
                        "نگه داشتن پول راکد در چنین شرایطی یک تصمیم است، نه بی‌تصمیمی.",
                ),
                DanakSection(
                    heading = "چرا کار می‌کند؟",
                    body = "زمان دو برابر شدن دقیقاً برابر است با لگاریتم طبیعی ۲ تقسیم بر " +
                        "لگاریتم طبیعی (۱ + نرخ). لگاریتم طبیعی ۲ تقریباً ۰٫۶۹۳ است و " +
                        "عدد ۷۲ به این خاطر انتخاب شده که بر ۲، ۳، ۴، ۶، ۸، ۹ و ۱۲ " +
                        "بخش‌پذیر است و محاسبهٔ ذهنی را آسان می‌کند.",
                ),
            ),
            readingSeconds = 45,
            image = DanakImage.Local(R.drawable.hero_rule_72),
            sourceName = "ویکی‌پدیا (انگلیسی)",
            sourceUrl = "https://en.wikipedia.org/wiki/Rule_of_72",
            keyTakeaway = "همین تقسیم ساده نشان می‌دهد تورم با چه سرعتی ارزش پول نقد را نصف می‌کند.",
            photoCredit = PhotoCredit(
                author = "Dori",
                license = "Public domain",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Stack_of_coins_0214.jpg",
            ),
        ),

        Danak(
            id = "sunk_cost",
            category = Category.Economy,
            title = "چرا ادامه دادن به کار اشتباه، سخت‌تر از شروع آن است؟",
            summary = "پولی که خرج شده برنمی‌گردد، اما ذهن ما هرچه بیشتر خرج کرده باشد، سخت‌تر رها " +
                "می‌کند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "اگر بلیت یک فیلم را خریده‌ای و نیم‌ساعت اول نشان می‌دهد فیلم بدی " +
                        "است، پول بلیت در هر دو حالت از دست رفته است. تنها پرسش منطقی " +
                        "این است: یک‌ساعت‌ونیم آینده‌ات را کجا بهتر می‌گذرانی؟",
                ),
                DanakSection(
                    heading = "چرا رهایش سخت است؟",
                    body = "رها کردن یعنی پذیرفتن اینکه سرمایه‌گذاری قبلی اشتباه بود، و ذهن " +
                        "ما از این اعتراف فرار می‌کند. هرچه سرمایه‌گذاری بزرگ‌تر باشد، " +
                        "این مقاومت شدیدتر است — پدیده‌ای که در پروژه‌های نرم‌افزاری " +
                        "شکست‌خورده و کسب‌وکارهای زیان‌ده مدام تکرار می‌شود.",
                ),
                DanakSection(
                    heading = "پرسش درست",
                    body = "به‌جای «چقدر تا اینجا خرج کرده‌ام؟» بپرس «اگر امروز از صفر " +
                        "تصمیم می‌گرفتم، باز هم این را انتخاب می‌کردم؟». اگر پاسخ منفی " +
                        "است، هزینهٔ گذشته دلیلی برای ادامه نیست.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_sunk_cost),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/هزینه_ازدست‌رفته",
            keyTakeaway = "بپرس «اگر امروز از صفر تصمیم می‌گرفتم، باز همین را انتخاب می‌کردم؟»",
            photoCredit = PhotoCredit(
                author = "טל שמע",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Habonim-Dor_Beach.jpg",
            ),
        ),

        Danak(
            id = "fall_of_rome",
            category = Category.History,
            title = "روم یک‌شبه سقوط نکرد؛ قرن‌ها طول کشید",
            summary = "روم یک‌شبه فرو نریخت؛ اقتصاد، ارتش و مرزهایش قرن‌ها هم‌زمان فرسوده شدند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "سال ۴۷۶ میلادی، که معمولاً تاریخ سقوط شمرده می‌شود، صرفاً سالی " +
                        "است که آخرین امپراتور غربی از قدرت کنار گذاشته شد. تا آن زمان، " +
                        "امپراتوری غربی مدت‌ها بود که عملاً کنترل مالیات، ارتش و " +
                        "ولایاتش را از دست داده بود. بخش شرقی هم هزار سال دیگر ادامه " +
                        "یافت.",
                ),
                DanakSection(
                    heading = "عوامل درهم‌تنیده",
                    body = "مورخان امروز به‌جای یک علت، مجموعه‌ای از فشارها را برمی‌شمارند: " +
                        "تورم شدید و کاهش عیار سکه، وابستگی فزاینده به نیروهای مزدور در " +
                        "مرزها، جنگ‌های داخلی پی‌درپی بر سر جانشینی، فشار مهاجرت اقوام " +
                        "از شمال، و ناتوانی دستگاه اداری در تأمین هزینهٔ ارتشی که به " +
                        "آن وابسته بود.",
                ),
                DanakSection(
                    heading = "درس ماندگار",
                    body = "فروپاشی‌های بزرگ معمولاً با یک ضربه رخ نمی‌دهند، بلکه با از دست " +
                        "رفتن تدریجی ظرفیت جذب ضربه. سامانه‌ای که حاشیهٔ اطمینانش را " +
                        "خرج کرده، با همان بحرانی از پا درمی‌آید که ده سال قبل از آن " +
                        "جان سالم به در می‌برد.",
                ),
            ),
            readingSeconds = 65,
            image = DanakImage.Local(R.drawable.hero_rome),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/سقوط_امپراتوری_روم_غربی",
            keyTakeaway = "سامانه‌ها با یک ضربه نمی‌افتند؛ وقتی می‌افتند که دیگر ظرفیت جذب ضربه ندارند.",
            photoCredit = PhotoCredit(
                author = "Claude Lorrain",
                license = "Public domain",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Claude_Lorrain_-_Capriccio_with_ruins_of_the_Roman_Forum_-_Google_Art_Project.jpg",
            ),
        ),

        Danak(
            id = "printing_press",
            category = Category.History,
            title = "چاپ، دانش را از انحصار بیرون آورد",
            summary = "پیش از گوتنبرگ، کتاب در اروپا دست‌نویس بود؛ پس از او ایده‌ها سریع‌تر از قدرت‌ها " +
                "جابه‌جا شدند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "یوهانس گوتنبرگ در حدود سال ۱۴۵۰ در ماینتس، حروف فلزی متحرک را با " +
                        "دستگاه پرس و جوهر روغنی ترکیب کرد. حروف متحرک قرن‌ها پیش‌تر در چین و " +
                        "کره اختراع شده بود؛ نوآوری او هیچ‌کدام از این سه " +
                        "به‌تنهایی نبود، بلکه ساختن سامانه‌ای بود که تولید انبوه متن را " +
                        "اقتصادی می‌کرد.",
                ),
                DanakSection(
                    heading = "مقیاس تغییر",
                    body = "یک کاتب ماهر برای رونویسی یک کتاب ماه‌ها وقت می‌گذاشت. تا سال " +
                        "۱۵۰۰، یعنی کمتر از پنجاه سال بعد، چاپخانه‌های اروپا میلیون‌ها " +
                        "نسخه تولید کرده بودند. هزینهٔ دسترسی به متن چند مرتبهٔ بزرگی " +
                        "کاهش یافت.",
                ),
                DanakSection(
                    heading = "پیامدهای پیش‌بینی‌نشده",
                    body = "چاپ فقط کتاب ارزان نکرد؛ زنجیرهٔ خطای رونویسی را شکست و نسخه‌های " +
                        "یکسان و قابل ارجاع ساخت — پیش‌شرطی که روش علمی بدون آن ممکن " +
                        "نبود. هم‌زمان، امکان انتشار سریع جزوه‌های جدلی، موازنهٔ قدرت " +
                        "میان نهادهای مستقر و منتقدانشان را برهم زد.",
                ),
            ),
            readingSeconds = 60,
            image = DanakImage.Local(R.drawable.hero_printing),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/چاپ_فشاری",
            keyTakeaway = "چاپ فقط کتاب را ارزان نکرد؛ متن یکسان و قابل ارجاع ساخت که علم بدون آن ممکن نبود.",
            photoCredit = PhotoCredit(
                author = "International Printing Museum",
                license = "CC BY 2.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:PrintMus_038.jpg",
            ),
        ),

        Danak(
            id = "silk_road",
            category = Category.History,
            title = "جادهٔ ابریشم یک جاده نبود",
            summary = "جادهٔ ابریشم یک جاده نبود، شبکه‌ای از راه‌ها بود — و مهم‌ترین بارش ایده بود، نه " +
                "ابریشم.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "اصطلاح «جادهٔ ابریشم» را یک جغرافی‌دان آلمانی در قرن نوزدهم " +
                        "ساخت؛ هیچ بازرگانی در دوران باستان خود را مسافر چنین جاده‌ای " +
                        "نمی‌دانست. این شبکه از چین تا مدیترانه امتداد داشت و ایران در " +
                        "قلب آن قرار گرفته بود.",
                ),
                DanakSection(
                    heading = "تجارت مرحله‌ای",
                    body = "کالاها معمولاً دست‌به‌دست میان بازرگانان منطقه‌ای می‌گشتند. هر " +
                        "واسطه مسیر کوتاهی را می‌پیمود و سود خود را برمی‌داشت؛ به همین " +
                        "دلیل قیمت ابریشم در رم می‌توانست ده‌ها برابر قیمتش در چین باشد.",
                ),
                DanakSection(
                    heading = "بار واقعی",
                    body = "مهم‌ترین چیزی که در این شبکه جابه‌جا شد ایده بود، نه ابریشم: " +
                        "کاغذ، ریاضیات، نجوم، ادیان و فناوری‌های کشاورزی. متأسفانه " +
                        "بیماری‌ها هم از همین مسیرها سفر کردند؛ گسترش طاعون در قرن " +
                        "چهاردهم تا حد زیادی از همین شبکهٔ بازرگانی بهره برد.",
                ),
            ),
            readingSeconds = 55,
            image = DanakImage.Local(R.drawable.hero_silk_road),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/جاده_ابریشم",
            keyTakeaway = "کاغذ، ریاضیات و ادیان از همان مسیرهایی گذشتند که کاروان‌ها.",
            photoCredit = PhotoCredit(
                author = "Asfour hamza",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Camel_caravan_going_through_sand_in_the_Sahara_Desert.jpg",
            ),
        ),
    )

    private val productivityAndCuriosities: List<Danak> = listOf(

        Danak(
            id = "parkinson_law",
            category = Category.Productivity,
            title = "کار دقیقاً به اندازهٔ زمانی که داری طول می‌کشد",
            summary = "کار آن‌قدر کش می‌آید که تمام زمان در دسترس را پر کند؛ مهلت کوتاه‌تر، کار متمرکزتر.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "سیریل نورثکوت پارکینسون، تاریخ‌نگار بریتانیایی، در سال ۱۹۵۵ این " +
                        "جمله را در مقاله‌ای طنزآمیز نوشت: «کار گسترش می‌یابد تا زمان " +
                        "موجود برای تکمیلش را پر کند». مشاهدهٔ او از رشد بوروکراسی " +
                        "اداری می‌آمد، اما در کار فردی هم به‌روشنی دیده می‌شود.",
                ),
                DanakSection(
                    heading = "سازوکار",
                    body = "وقتی زمان فراوان است، معیار «به‌اندازهٔ کافی خوب» مدام بالا " +
                        "می‌رود، کمال‌گرایی فضا پیدا می‌کند و تصمیم‌های کوچک به تعویق " +
                        "می‌افتند. محدودیت زمانی این فضا را حذف می‌کند و مجبورت می‌کند " +
                        "زودتر تشخیص بدهی چه چیزی واقعاً ضروری است.",
                ),
                DanakSection(
                    heading = "استفادهٔ درست",
                    body = "برای هر کار یک مهلت کوتاه‌تر از آنچه طبیعی به نظر می‌رسد تعیین " +
                        "کن و آن را مثل یک قرار بیرونی جدی بگیر. اما این ابزار مرز " +
                        "دارد: فشرده کردن بیش از حد زمان روی کارهایی که ذاتاً نیاز به " +
                        "تفکر عمیق دارند، فقط کیفیت را قربانی می‌کند.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_parkinson),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/قانون_پارکینسون",
            keyTakeaway = "برای هر کار مهلتی کوتاه‌تر از حد طبیعی بگذار — اما نه برای کارهایی که تفکر عمیق " +
                "می‌خواهند.",
            photoCredit = PhotoCredit(
                author = "John Morgan",
                license = "CC BY 2.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Hourglass_with_sand.jpg",
            ),
        ),

        Danak(
            id = "spaced_repetition",
            category = Category.Productivity,
            title = "مرور درست‌زمان، از مرور زیاد مؤثرتر است",
            summary = "اگر درست پیش از فراموش کردن مرور کنی، هر بار مطلب مدت بیشتری در ذهن می‌ماند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "هرمان ابینگهاوس در دههٔ ۱۸۸۰ روی حافظهٔ خودش آزمایش کرد و نشان " +
                        "داد بیشترین افت یادآوری در ساعت‌ها و روزهای نخست رخ می‌دهد و " +
                        "سپس شیب منحنی ملایم می‌شود. او این را «منحنی فراموشی» نامید.",
                ),
                DanakSection(
                    heading = "چرا فاصله‌گذاری کار می‌کند؟",
                    body = "هر بار که چیزی را با تلاش از حافظه بیرون می‌کشی، مسیر بازیابی " +
                        "آن تقویت می‌شود. اگر خیلی زود مرور کنی، تلاشی در کار نیست و " +
                        "سودی هم ندارد؛ اگر خیلی دیر مرور کنی، باید از نو یاد بگیری. " +
                        "بهترین لحظه، درست پیش از فراموشی است.",
                ),
                DanakSection(
                    heading = "در عمل",
                    body = "فاصله‌های تقریبی یک روز، سه روز، یک هفته، دو هفته و یک ماه نقطهٔ " +
                        "شروع خوبی هستند. نکتهٔ کلیدی این است که مرور باید فعال باشد — " +
                        "یعنی پاسخ را از ذهنت بیرون بکشی، نه اینکه دوباره متن را بخوانی. " +
                        "خواندن دوباره حس آشنایی می‌سازد و آن را با دانستن اشتباه می‌گیریم.",
                ),
            ),
            readingSeconds = 55,
            image = DanakImage.Local(R.drawable.hero_spaced_rep),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/تکرار_فاصله‌دار",
            keyTakeaway = "مرور یعنی بیرون کشیدن از ذهن، نه دوباره خواندن؛ آشنایی را با دانستن اشتباه نگیر.",
            photoCredit = PhotoCredit(
                author = "Shixart1985",
                license = "CC BY 2.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Woman_reading_a_book._Legs_up_the_wall_pose.jpg",
            ),
        ),

        Danak(
            id = "context_switching",
            category = Category.Productivity,
            title = "هر بار جابه‌جایی بین کارها، هزینه‌ای پنهان دارد",
            summary = "ذهن نمی‌تواند آنی بین کارها جابه‌جا شود؛ بخشی از توجه روی کار قبلی جا می‌ماند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "در آزمایش‌های روان‌شناسی شناختی، وقتی افراد باید میان دو نوع " +
                        "تکلیف جابه‌جا شوند، زمان پاسخ و نرخ خطایشان به‌طور محسوسی بالا " +
                        "می‌رود. این هزینه حتی وقتی هر دو تکلیف ساده‌اند هم وجود دارد.",
                ),
                DanakSection(
                    heading = "باقیماندهٔ توجه",
                    body = "وقتی کاری را ناتمام رها می‌کنی و به کار دیگری می‌روی، بخشی از " +
                        "ظرفیت توجهت همچنان درگیر کار قبلی می‌ماند. برای کارهای شناختی " +
                        "سنگین — نوشتن، طراحی، اشکال‌زدایی — بازگشت به عمق کامل " +
                        "می‌تواند ده‌ها دقیقه طول بکشد.",
                ),
                DanakSection(
                    heading = "کاهش هزینه",
                    body = "کارهای هم‌جنس را کنار هم دسته‌بندی کن و برایشان بلوک زمانی " +
                        "بگذار. مهم‌تر از آن، پیش از رها کردن یک کار، در یک خط بنویس که " +
                        "قدم بعدی چیست؛ این یادداشت کوتاه، هزینهٔ بازگشت را به‌شکل " +
                        "چشمگیری کم می‌کند.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_context_switch),
            sourceName = "ویکی‌پدیا (انگلیسی)",
            sourceUrl = "https://en.wikipedia.org/wiki/Task_switching_(psychology)",
            keyTakeaway = "پیش از رها کردن هر کار، قدم بعدی‌اش را در یک خط بنویس.",
            photoCredit = PhotoCredit(
                author = "Daniel Schwen",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Tolono_Xing_1.jpg",
            ),
        ),

        Danak(
            id = "honey",
            category = Category.Curiosities,
            title = "چرا عسل تقریباً هیچ‌وقت فاسد نمی‌شود؟",
            summary = "آب کم، اسیدیته و کمی آب اکسیژنه عسل را سال‌ها سالم نگه می‌دارند؛ به شرط آنکه درش " +
                "بسته بماند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "باکتری و کپک برای رشد به آب نیاز دارند. عسل حدود ۸۰ درصد قند و " +
                        "کمتر از ۱۸ درصد آب دارد، و این آب به‌شدت به مولکول‌های قند " +
                        "مقید است. در چنین محیطی، میکروب‌ها آب خود را از دست می‌دهند و " +
                        "از بین می‌روند.",
                ),
                DanakSection(
                    heading = "دو سد دیگر",
                    body = "عسل اسیدی است و pH آن حدود ۳٫۹ است که برای بیشتر باکتری‌ها " +
                        "نامناسب است. افزون بر این، زنبورها آنزیمی به نام گلوکز " +
                        "اکسیداز به شهد اضافه می‌کنند که مقدار بسیار کمی آب اکسیژنه " +
                        "تولید می‌کند — یک ضدعفونی‌کنندهٔ ملایم و دائمی.",
                ),
                DanakSection(
                    heading = "یک هشدار مهم",
                    body = "ماندگاری به معنای استریل بودن نیست. عسل می‌تواند هاگ بوتولیسم " +
                        "داشته باشد که برای بزرگسالان بی‌خطر است اما برای نوزادان زیر " +
                        "یک سال خطرناک. همچنین اگر رطوبت جذب کند، امکان تخمیر پیدا " +
                        "می‌کند؛ پس ظرف باید بسته بماند.",
                ),
            ),
            readingSeconds = 45,
            image = DanakImage.Local(R.drawable.hero_honey),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/عسل",
            keyTakeaway = "آب کم، اسیدیته و کمی آب اکسیژنه جلوی رشد میکروب را می‌گیرند؛ اما عسل استریل " +
                "نیست.",
            photoCredit = PhotoCredit(
                author = "Thomas Bresson",
                license = "CC BY 2.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:ComputerHotline_-_Apis_mellifera_(by)_(1).jpg",
            ),
        ),

        Danak(
            id = "thermal_feel",
            category = Category.Curiosities,
            title = "چرا فلز سردتر از چوب حس می‌شود، با اینکه نیست؟",
            summary = "فلز و چوب کنار هم هم‌دما هستند، اما فلز سردتر حس می‌شود؛ پوست سرعت از دست دادن " +
                "گرما را می‌سنجد.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "اگر یک قاشق فلزی و یک قاشق چوبی ساعت‌ها در یک اتاق بمانند، دمای " +
                        "هر دو با دمای اتاق برابر می‌شود. با این حال لمس فلز حس سرمای " +
                        "بیشتری می‌دهد. علت در خود اجسام نیست، در گیرنده‌های ماست.",
                ),
                DanakSection(
                    heading = "رسانندگی گرمایی",
                    body = "گیرنده‌های پوست، تغییر دمای خود پوست را گزارش می‌کنند، نه دمای " +
                        "جسم را. فلز گرما را ده‌ها تا صدها برابر سریع‌تر از چوب هدایت " +
                        "می‌کند، پس گرمای دستت را با سرعت بسیار بیشتری می‌کشد و پوست " +
                        "سریع‌تر سرد می‌شود. مغز این افت سریع را «سرد» تفسیر می‌کند.",
                ),
                DanakSection(
                    heading = "همین اصل، برعکس",
                    body = "به همین دلیل فلز داغ بسیار خطرناک‌تر از چوب داغ است: گرما را با " +
                        "همان سرعت به پوست منتقل می‌کند. و به همین دلیل دمای ۲۰ درجه در " +
                        "آب بسیار سردتر از ۲۰ درجه در هوا حس می‌شود — رسانندگی گرمایی " +
                        "آب حدود بیست و پنج برابر هواست.",
                ),
            ),
            readingSeconds = 45,
            image = DanakImage.Local(R.drawable.hero_thermal),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/رسانندگی_گرمایی",
            keyTakeaway = "حس سرما دربارهٔ دمای جسم نیست، دربارهٔ سرعتی است که گرما را از تو می‌گیرد.",
            photoCredit = PhotoCredit(
                author = "Dietmar Rabich",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:D%C3%BClmen,_Wiese_am_Strandbadweg_--_2016_--_5670-6.jpg",
            ),
        ),

        Danak(
            id = "deep_sea",
            category = Category.Curiosities,
            title = "نقشهٔ سطح مریخ را بهتر از کف اقیانوس می‌شناسیم",
            summary = "نقشهٔ سطح مریخ را بهتر از کف اقیانوس‌های زمین داریم؛ دلیلش فیزیک آب است.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "برای نقشه‌برداری از سطح ماه یا مریخ کافی است ماهواره‌ای در مدار " +
                        "قرار بگیرد و تصویر بگیرد. اما آب دریا در برابر امواج رادیویی " +
                        "تقریباً کدر است؛ هیچ ماهواره‌ای نمی‌تواند مستقیم کف اقیانوس را " +
                        "با جزئیات ببیند.",
                ),
                DanakSection(
                    heading = "تنها راه: صوت، از نزدیک",
                    body = "نقشه‌برداری دقیق بستر دریا با سونار انجام می‌شود و سونار باید " +
                        "نزدیک به عمق باشد. یعنی کشتی‌ها باید عملاً خط‌به‌خط روی تمام " +
                        "اقیانوس‌ها حرکت کنند — کاری بسیار کند و پرهزینه برای مساحتی " +
                        "بیش از ۳۶۰ میلیون کیلومتر مربع.",
                ),
                DanakSection(
                    heading = "و فشار",
                    body = "در میانگین عمق اقیانوس، فشار حدود چهارصد برابر فشار سطح دریاست. " +
                        "هر دستگاهی که به آنجا فرستاده شود باید این فشار را تحمل کند، " +
                        "که هزینهٔ اکتشاف مستقیم را باز هم بالاتر می‌برد.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_deep_sea),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/ژرف‌دریا",
            keyTakeaway = "ماهواره‌ها از میان آب نمی‌بینند؛ کف اقیانوس را فقط باید با صوت و از نزدیک کاوید.",
            photoCredit = PhotoCredit(
                author = "Brocken Inaglory",
                license = "CC BY-SA 3.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Underwater_portrait_of_green_turtle.jpg",
            ),
        ),
    )

    private val secondBatch: List<Danak> = listOf(

        Danak(
            id = "anchoring",
            category = Category.Psychology,
            title = "اولین عددی که می‌بینی، حدس بعدی‌ات را می‌سازد",
            summary = "حتی یک عدد کاملاً تصادفی، تخمین بعدی ما را به سمت خودش می‌کشد.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "آموس تورسکی و دانیل کانمن در سال ۱۹۷۴ یک چرخ شانس را طوری دستکاری " +
                        "کردند که فقط روی ۱۰ یا ۶۵ بایستد. بعد از شرکت‌کنندگان پرسیدند چند " +
                        "درصد کشورهای عضو سازمان ملل آفریقایی‌اند. میانهٔ پاسخ کسانی که ۱۰ " +
                        "دیده بودند ۲۵ بود و میانهٔ کسانی که ۶۵ دیده بودند ۴۵ — با اینکه " +
                        "همه می‌دانستند عدد چرخ هیچ ربطی به سؤال ندارد.",
                ),
                DanakSection(
                    heading = "چرا این اتفاق می‌افتد؟",
                    body = "ذهن برای تخمین زدن از یک نقطهٔ شروع حرکت می‌کند و بعد آن را اصلاح " +
                        "می‌کند. مشکل این است که اصلاح تقریباً همیشه ناکافی است؛ ما زودتر " +
                        "از آنچه باید، از جابه‌جا کردن تخمین دست می‌کشیم و نزدیک لنگر " +
                        "می‌مانیم.",
                ),
                DanakSection(
                    heading = "در زندگی روزمره",
                    body = "قیمت «قبل از تخفیف»، اولین رقمی که در مذاکرهٔ حقوق گفته می‌شود و " +
                        "گران‌ترین گزینهٔ یک منو همه لنگرند. پیش از مذاکره، عدد خودت را از " +
                        "روی داده‌های بیرونی تعیین کن تا عدد طرف مقابل نقطهٔ شروعت نشود.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_anchoring),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/لنگر_انداختن",
            keyTakeaway = "پیش از مذاکره، عددت را از داده‌های بیرونی تعیین کن تا عدد طرف مقابل لنگرت نشود.",
            photoCredit = PhotoCredit(
                author = "JoachimKohler-HB",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Anker_am_Bug_der_DS_%22Storf_I%22_in_Bergen_NOR_(2015).jpg",
            ),
        ),

        Danak(
            id = "sound_in_space",
            category = Category.Science,
            title = "چرا در فضا هیچ صدایی شنیده نمی‌شود؟",
            summary = "صدا لرزش مولکول‌هاست و در خلأ فضا مولکولی نیست که بلرزد.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "صدا یک موج مکانیکی است: هر مولکول هوا به مولکول کناری ضربه می‌زند " +
                        "و این فشار زنجیروار جلو می‌رود. در فضای میان سیاره‌ها در هر سانتی‌متر " +
                        "مکعب فقط چند ذره وجود دارد، در حالی که هوای اطراف ما میلیاردها " +
                        "میلیارد مولکول در همان حجم دارد. زنجیره عملاً پاره است.",
                ),
                DanakSection(
                    heading = "پس فضانوردان چطور حرف می‌زنند؟",
                    body = "با رادیو. امواج رادیویی مثل نور، موج الکترومغناطیسی‌اند و برای " +
                        "حرکت به هیچ محیطی نیاز ندارند. داخل کپسول و لباس فضایی هوا هست، پس " +
                        "صدا در همان فضای کوچک به‌طور عادی شنیده می‌شود.",
                ),
                DanakSection(
                    heading = "یک استثنای جالب",
                    body = "در خوشه‌های کهکشانی، گاز داغ آن‌قدر زیاد است که امواج فشار واقعی در " +
                        "آن جریان دارد. ناسا در سال ۲۰۲۲ امواج فشار خوشهٔ پرساووش را با بالا " +
                        "بردن فرکانس‌شان ده‌ها اکتاو، به صدایی تبدیل کرد که گوش انسان " +
                        "می‌شنود.",
                ),
            ),
            readingSeconds = 45,
            image = DanakImage.Local(R.drawable.hero_sound_space),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/صدا",
            keyTakeaway = "فضانوردان با رادیو حرف می‌زنند، چون امواج الکترومغناطیسی به هوا نیازی ندارند.",
            photoCredit = PhotoCredit(
                author = "NASA",
                license = "Public domain",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Bruce_McCandless_II_during_EVA_in_1984.jpg",
            ),
        ),

        Danak(
            id = "gps",
            category = Category.Technology,
            title = "GPS بدون نظریهٔ نسبیت، روزی کیلومترها خطا داشت",
            summary = "ساعت ماهواره‌های GPS روزی ۳۸ میکروثانیه تند می‌رود؛ بدون اصلاح نسبیت، خطا روزی " +
                "کیلومترها می‌شد.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "هر ماهوارهٔ GPS دائماً زمان دقیق خودش را پخش می‌کند. گیرنده از روی " +
                        "تأخیر رسیدن سیگنال، فاصله‌اش تا هر ماهواره را به دست می‌آورد. با " +
                        "فاصله تا سه ماهواره موقعیت مشخص می‌شود و ماهوارهٔ چهارم خطای " +
                        "ساعت ارزان گیرنده را جبران می‌کند.",
                ),
                DanakSection(
                    heading = "نقش اینشتین",
                    body = "ماهواره‌ها با سرعت زیاد حرکت می‌کنند، پس طبق نسبیت خاص ساعتشان " +
                        "حدود ۷ میکروثانیه در روز کند می‌شود. اما چون از میدان گرانش زمین دورترند، " +
                        "طبق نسبیت عام حدود ۴۵ میکروثانیه در روز تند می‌شوند. برآیند، ۳۸ " +
                        "میکروثانیه تندتر است.",
                ),
                DanakSection(
                    heading = "چرا این عدد کوچک مهم است؟",
                    body = "سیگنال با سرعت نور می‌آید، پس هر میکروثانیه خطای زمانی یعنی حدود " +
                        "۳۰۰ متر خطای فاصله. به همین دلیل ساعت ماهواره‌ها پیش از پرتاب " +
                        "عمداً کمی کندتر تنظیم می‌شوند تا در مدار درست کار کنند.",
                ),
            ),
            readingSeconds = 55,
            image = DanakImage.Local(R.drawable.hero_gps),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/سامانه_موقعیت‌یاب_جهانی",
            keyTakeaway = "هر میکروثانیه خطای زمان، حدود ۳۰۰ متر خطای مکان است.",
            photoCredit = PhotoCredit(
                author = "European Space Agency",
                license = "CC BY-SA 3.0 igo",
                pageUrl = "https://commons.wikimedia.org/wiki/File:MetOp_Second_Generation_A-type_satellite_above_Earth_ESA511188.jpg",
            ),
        ),

        Danak(
            id = "git_branches",
            category = Category.Programming,
            title = "چرا ساختن شاخه در Git تقریباً هیچ هزینه‌ای ندارد؟",
            summary = "در Git، شاخه کپی کد نیست؛ فقط اشاره‌گری کوچک به یک کامیت است.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "در سیستم‌های قدیمی‌تر کنترل نسخه، ساختن شاخه اغلب یعنی کپی کردن " +
                        "کل پوشهٔ پروژه، و به همین دلیل تیم‌ها از شاخه زدن پرهیز می‌کردند. " +
                        "Git این رابطه را برعکس کرد.",
                ),
                DanakSection(
                    heading = "زیر کاپوت",
                    body = "هر کامیت در Git یک تصویر کامل از پروژه است، اما فایل‌هایی که " +
                        "تغییر نکرده‌اند دوباره ذخیره نمی‌شوند؛ هر محتوا با هش خودش شناخته " +
                        "می‌شود و بین کامیت‌ها مشترک است. شاخه فقط یک اشاره‌گر به آخرین " +
                        "کامیت است که با هر کامیت جدید جلو می‌رود.",
                ),
                DanakSection(
                    heading = "پیامد عملی",
                    body = "چون شاخه ارزان است، می‌توانی برای هر آزمایش کوچک یک شاخه بسازی و " +
                        "اگر به نتیجه نرسید، بی‌هزینه حذفش کنی. حذف شاخه هم فقط اشاره‌گر را " +
                        "پاک می‌کند؛ کامیت‌ها تا مدتی از طریق reflog قابل بازیابی می‌مانند.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_git),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/گیت_(نرم‌افزار)",
            keyTakeaway = "چون شاخه ارزان است، برای هر آزمایش کوچک یکی بساز و بی‌هزینه دورش بینداز.",
            photoCredit = PhotoCredit(
                author = "W.carter",
                license = "Public domain",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Waxing_moon_in_trees.jpg",
            ),
        ),

        Danak(
            id = "greshams_law",
            category = Category.Economy,
            title = "چرا «پول بد، پول خوب را از بازار بیرون می‌کند»؟",
            summary = "وقتی دو سکه ارزش اسمی یکسان دارند، مردم سکهٔ بهتر را نگه می‌دارند و بدتر را خرج " +
                "می‌کنند.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "این قانون به نام توماس گرشام، سرمایه‌دار انگلیسی قرن شانزدهم، " +
                        "شناخته می‌شود، هرچند پیش از او نیکول اورسم و کوپرنیک هم همین را " +
                        "توضیح داده بودند. منطقش ساده است: اگر قانون می‌گوید دو سکه هم‌ارزش‌اند، " +
                        "خرج کردن سکه‌ای که فلز بیشتری دارد ضرر است.",
                ),
                DanakSection(
                    heading = "یک نمونهٔ نزدیک",
                    body = "آمریکا در سال ۱۹۶۵ نقره را از سکه‌های ده و بیست‌وپنج سنتی حذف کرد. " +
                        "در چند سال، سکه‌های نقره‌ای قدیمی تقریباً از گردش ناپدید شدند؛ مردم " +
                        "آن‌ها را نگه داشتند یا ذوب کردند و سکه‌های جدید را خرج کردند.",
                ),
                DanakSection(
                    heading = "شرط مهم",
                    body = "قانون گرشام فقط وقتی کار می‌کند که نرخ برابری دو پول به زور قانون " +
                        "ثابت نگه داشته شود. اگر مردم آزاد باشند هر پول را به ارزش واقعی‌اش " +
                        "مبادله کنند، اتفاق برعکس می‌افتد و پول خوب، بد را کنار می‌زند.",
                ),
            ),
            readingSeconds = 55,
            image = DanakImage.Local(R.drawable.hero_gresham),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/قانون_گرشام",
            keyTakeaway = "قانون گرشام فقط وقتی کار می‌کند که قیمت برابر دو پول به زور قانون ثابت شده باشد.",
            photoCredit = PhotoCredit(
                author = "Hans Hillewaert",
                license = "CC BY-SA 3.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Silver_coin_hoard.jpg",
            ),
        ),

        Danak(
            id = "cuneiform",
            category = Category.History,
            title = "نخستین نوشتهٔ بشر، شعر نبود؛ حسابداری بود",
            summary = "قدیمی‌ترین لوح‌های خط میخی بیشتر فهرست جیره و دام‌اند؛ نوشتن برای حسابداری اختراع " +
                "شد.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "حدود پنج هزار و دویست سال پیش در شهر اوروک، کاتبان با قلمی از نی " +
                        "نشانه‌هایی گوه‌مانند روی گل نرم فشار می‌دادند. بخش بزرگی از لوح‌های " +
                        "این دوره اسناد اداری‌اند: چه کسی چقدر جو گرفت، چند گوسفند در کدام " +
                        "آغل است.",
                ),
                DanakSection(
                    heading = "سه هزار سال عمر",
                    body = "خط میخی برای نوشتن زبان‌های مختلفی به کار رفت، از سومری و اکدی تا " +
                        "فارسی باستان. کتیبه‌های هخامنشی، از جمله کتیبهٔ بیستون، به همین " +
                        "خط نوشته شده‌اند.",
                ),
                DanakSection(
                    heading = "چطور دوباره خوانده شد؟",
                    body = "کتیبهٔ بیستون یک متن را به سه زبان فارسی باستان، ایلامی و بابلی " +
                        "دارد. در قرن نوزدهم، هنری راولینسون و دیگران از بخش فارسی باستان " +
                        "شروع کردند و همین متن سه‌زبانه کلید رمزگشایی خط میخی شد.",
                ),
            ),
            readingSeconds = 55,
            image = DanakImage.Local(R.drawable.hero_cuneiform),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/خط_میخی",
            keyTakeaway = "کتیبهٔ سه‌زبانهٔ بیستون کلید خواندن دوبارهٔ خط میخی شد.",
            photoCredit = PhotoCredit(
                author = "ناشناس",
                license = "CC BY-SA 3.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Cuneiform_tablet_BM62788.jpg",
            ),
        ),

        Danak(
            id = "pomodoro",
            category = Category.Productivity,
            title = "۲۵ دقیقه کار، ۵ دقیقه استراحت: چرا جواب می‌دهد؟",
            summary = "۲۵ دقیقه کار و ۵ دقیقه استراحت؛ قدرت پومودورو در آسان کردن شروع است، نه در " +
                "زمان‌سنج.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "فرانچسکو چیریلو در اواخر دههٔ ۱۹۸۰، وقتی دانشجو بود، با یک " +
                        "زمان‌سنج آشپزخانه به شکل گوجه‌فرنگی این روش را ساخت. قاعده ساده است: " +
                        "۲۵ دقیقه روی یک کار، ۵ دقیقه استراحت، و بعد از چهار دور یک " +
                        "استراحت طولانی‌تر.",
                ),
                DanakSection(
                    heading = "چرا کار می‌کند؟",
                    body = "تعهد به «فقط ۲۵ دقیقه» اصطکاک شروع را کم می‌کند؛ کار بزرگ و " +
                        "مبهم به یک قدم کوچک و مشخص تبدیل می‌شود. استراحت‌های منظم هم " +
                        "خستگی را پیش از آنکه تمرکز را از بین ببرد، کنترل می‌کنند.",
                ),
                DanakSection(
                    heading = "انعطاف لازم",
                    body = "عدد ۲۵ مقدس نیست. برای کارهای عمیق مثل برنامه‌نویسی، بسیاری بازه‌های " +
                        "۵۰ دقیقه‌ای را بهتر می‌دانند. اصل مهم، مرز روشن میان تمرکز و " +
                        "استراحت است، نه طول دقیق آن.",
                ),
            ),
            readingSeconds = 45,
            image = DanakImage.Local(R.drawable.hero_pomodoro),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/فن_پومودورو",
            keyTakeaway = "مرز روشن میان تمرکز و استراحت مهم است، نه طول دقیق آن.",
            photoCredit = PhotoCredit(
                author = "Retro00064",
                license = "Public domain",
                pageUrl = "https://commons.wikimedia.org/wiki/File:Lux_Minute_Minder_timer.jpg",
            ),
        ),

        Danak(
            id = "olbers_paradox",
            category = Category.Curiosities,
            title = "اگر ستاره‌ها بی‌شمارند، چرا آسمان شب تاریک است؟",
            summary = "اگر ستاره‌ها بی‌شمار بودند، آسمان شب باید می‌درخشید؛ تاریکی شب سرنخی از آغاز کیهان " +
                "است.",
            sections = listOf(
                DanakSection(
                    heading = null,
                    body = "این پرسش را «پارادوکس اولبرس» می‌نامند، به نام اخترشناس آلمانی " +
                        "قرن نوزدهم. در کیهانی بی‌پایان و ایستا، ستاره‌های دورتر کم‌نورترند، " +
                        "اما تعدادشان به همان نسبت بیشتر است و این دو اثر یکدیگر را " +
                        "خنثی می‌کنند.",
                ),
                DanakSection(
                    heading = "پاسخ",
                    body = "کیهان حدود ۱۳٫۸ میلیارد سال عمر دارد، پس نور ستاره‌های بسیار دور " +
                        "هنوز فرصت نکرده به ما برسد. افزون بر این، انبساط کیهان نور " +
                        "کهکشان‌های دور را به طول موج‌های بلندتر و نامرئی می‌کشد.",
                ),
                DanakSection(
                    heading = "یک پیش‌بینی ادبی",
                    body = "ادگار آلن پو در سال ۱۸۴۸، در رسالهٔ «اورکا»، حدس زد که تاریکی " +
                        "آسمان به این دلیل است که نور دورترین ستاره‌ها هنوز به ما نرسیده — " +
                        "بسیار پیش از آنکه علم آغازی برای کیهان قائل شود.",
                ),
            ),
            readingSeconds = 50,
            image = DanakImage.Local(R.drawable.hero_olbers),
            sourceName = "ویکی‌پدیا",
            sourceUrl = "https://fa.wikipedia.org/wiki/پارادوکس_اولبرس",
            keyTakeaway = "آسمان شب تاریک است چون کیهان عمر محدودی دارد و نور دورترین ستاره‌ها هنوز نرسیده.",
            photoCredit = PhotoCredit(
                author = "Giles Laurent",
                license = "CC BY-SA 4.0",
                pageUrl = "https://commons.wikimedia.org/wiki/File:036_Milky_Way_during_Perseids_seen_from_Oeschinensee_with_water_reflections_Photo_by_Giles_Laurent.jpg",
            ),
        ),
    )

    /** Everything Danak knows, interleaved so consecutive items rarely share a category. */
    val all: List<Danak> = interleaveByCategory(
        psychologyAndScience + technologyAndProgramming +
            economyAndHistory + productivityAndCuriosities + secondBatch,
    )

    /**
     * Spreads categories out across the feed. Without this the first six swipes are all
     * psychology and the feed feels narrower than it is.
     */
    private fun interleaveByCategory(items: List<Danak>): List<Danak> {
        val buckets = items.groupBy { it.category }.values.map { it.toMutableList() }
        val result = ArrayList<Danak>(items.size)
        while (result.size < items.size) {
            for (bucket in buckets) {
                if (bucket.isNotEmpty()) result += bucket.removeAt(0)
            }
        }
        return result
    }
}
