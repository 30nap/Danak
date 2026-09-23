package ir.danak.app

import ir.danak.app.ui.util.splitIntoParagraphs
import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphsTest {

    @Test
    fun `short text stays one paragraph`() {
        assertEquals(listOf("یک جمله. جملهٔ دوم."), splitIntoParagraphs("یک جمله. جملهٔ دوم."))
    }

    @Test
    fun `long text is split every two sentences`() {
        val text = "جملهٔ اول نسبتاً بلند است و ادامه دارد. جملهٔ دوم هم همین‌طور است و ادامه دارد. " +
            "جملهٔ سوم هم به اندازهٔ کافی بلند است که تنها بماند. جملهٔ چهارم هم بلند است و تمام می‌شود."
        val paragraphs = splitIntoParagraphs(text)
        assertEquals(2, paragraphs.size)
        assertEquals(true, paragraphs[0].endsWith("ادامه دارد."))
    }

    @Test
    fun `persian question marks end sentences too`() {
        val text = "چرا این‌طور است؟ چون ساده‌ترین توضیح است و بیشتر شواهد آن را تأیید می‌کنند. " +
            "ولی همیشه استثنا وجود دارد و باید با دقت نگاه کرد. این آخرین جملهٔ بلند متن است."
        assertEquals(2, splitIntoParagraphs(text).size)
    }

    @Test
    fun `a short trailing sentence joins the previous paragraph`() {
        val text = "جملهٔ اول به اندازهٔ کافی طولانی است که معنا داشته باشد. جملهٔ دوم هم همین‌طور است. کوتاه."
        assertEquals(1, splitIntoParagraphs(text).size)
    }

    @Test
    fun `decimal points inside numbers do not split`() {
        assertEquals(1, splitIntoParagraphs("حدود ۱۳٫۸ میلیارد سال. و ۰.۳ هم عدد است.").size)
    }
}
