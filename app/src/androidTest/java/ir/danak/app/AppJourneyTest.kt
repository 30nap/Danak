package ir.danak.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.danak.app.data.MockDanaks
import ir.danak.app.model.Category
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks the whole V0 journey from the Definition of Done on the real activity: pick
 * interests, read the feed, swipe, save, open the detail, browse saved, change theme.
 * Captures a screenshot and an accessibility audit of every screen along the way.
 */
@RunWith(AndroidJUnit4::class)
class AppJourneyTest {

    private val rule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val chain: RuleChain = RuleChain.outerRule(ClearAppStateRule()).around(rule)

    private val chosen = listOf(Category.Psychology, Category.Science, Category.History)
    private val expectedFeed = MockDanaks.all.filter { it.category in chosen }

    @Test
    fun fullJourney() {
        onboarding()
        feedAndSwipe()
        saveAndDetail()
        savedList()
        settingsAndTheme()
    }

    private fun onboarding() {
        // The splash holds until saved state is read; wait for the first real frame.
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("به چه موضوعاتی علاقه داری؟").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("به چه موضوعاتی علاقه داری؟").assertIsDisplayed()
        rule.onNodeWithText("ادامه").assertIsNotEnabled()
        UiAudit.screenshot(rule, "01_onboarding_empty")

        chosen.forEach { rule.onNodeWithText(it.label).performClick() }
        rule.onNodeWithText(chosen.first().label).assertIsSelected()
        rule.onNodeWithText("ادامه").assertIsEnabled()
        UiAudit.screenshot(rule, "02_onboarding_selected")
        UiAudit.auditScreen(rule, "onboarding")

        rule.onNodeWithText("ادامه").performClick()
    }

    private fun feedAndSwipe() {
        val first = expectedFeed[0]
        val second = expectedFeed[1]

        rule.onNodeWithText(first.title).assertIsDisplayed()
        assertRtlLayout()
        UiAudit.screenshot(rule, "03_feed_first")
        UiAudit.auditScreen(rule, "feed")

        rule.onRoot().performTouchInput { swipeUp() }
        rule.onNodeWithText(second.title).assertIsDisplayed()
        UiAudit.screenshot(rule, "04_feed_second")

        rule.onRoot().performTouchInput { swipeDown() }
        rule.onNodeWithText(first.title).assertIsDisplayed()
    }

    /** Persian reads right to left, so the start of every row must be on the right. */
    private fun assertRtlLayout() {
        val width = rule.onRoot().fetchSemanticsNode().size.width
        val chip = rule.visibleNodeWithText(expectedFeed[0].category.label)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("category chip should hug the right edge", chip.right > width * 0.8f)

        val save = rule.visibleNodeWithDescription("ذخیره کردن").fetchSemanticsNode().boundsInRoot
        val more = rule.visibleNodeWithText("بیشتر بدان").fetchSemanticsNode().boundsInRoot
        assertTrue("«بیشتر بدان» leads the row in RTL, i.e. sits right of save", more.left > save.left)
    }

    private fun saveAndDetail() {
        val first = expectedFeed[0]

        rule.visibleNodeWithDescription("ذخیره کردن").performClick()
        rule.visibleNodeWithDescription("حذف از ذخیره‌شده‌ها").assertIsDisplayed()
        UiAudit.screenshot(rule, "05_feed_saved")

        rule.visibleNodeWithText("بیشتر بدان").performClick()
        rule.onNodeWithText(first.sections.last().body).assertExists()
        rule.onNodeWithContentDescription("حذف از ذخیره‌شده‌ها").assertIsDisplayed()
        UiAudit.screenshot(rule, "06_detail_top")
        UiAudit.auditScreen(rule, "detail")

        rule.onRoot().performTouchInput { swipeUp() }
        rule.onRoot().performTouchInput { swipeUp() }
        UiAudit.screenshot(rule, "07_detail_scrolled")

        rule.onNodeWithContentDescription("بازگشت").performClick()
        rule.onNodeWithText(first.title).assertIsDisplayed()
    }

    private fun savedList() {
        val first = expectedFeed[0]

        rule.onNodeWithContentDescription("ذخیره‌شده‌ها").performClick()
        rule.onNodeWithText(first.title).assertIsDisplayed()
        UiAudit.screenshot(rule, "08_saved_list")
        UiAudit.auditScreen(rule, "saved")

        val remove = "حذف «${first.title}» از ذخیره‌شده‌ها"
        // With auto-advance the test clock would skip the snackbar's timeout and dismiss
        // it before it could be seen, so time is stepped by hand while it is up.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithContentDescription(remove).performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("از ذخیره‌شده‌ها حذف شد").assertIsDisplayed()
        UiAudit.screenshot(rule, "09_saved_undo")

        // Undo brings it back; removing again leaves the list empty.
        rule.onNodeWithText("بازگرداندن").performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.mainClock.autoAdvance = true
        rule.onNodeWithText(first.title).assertIsDisplayed()
        rule.onNodeWithContentDescription(remove).performClick()
        rule.onNodeWithText("هنوز چیزی ذخیره نکرده‌ای").assertIsDisplayed()
        UiAudit.screenshot(rule, "09_saved_empty")

        rule.onNodeWithContentDescription("بازگشت").performClick()
        // Removed from saved in the list, so the feed must show it unsaved again.
        rule.visibleNodeWithDescription("ذخیره کردن").assertIsDisplayed()
    }

    private fun settingsAndTheme() {
        // Move two pages in; changing interests below must bring the feed back to the start.
        rule.onRoot().performTouchInput { swipeUp() }
        rule.onRoot().performTouchInput { swipeUp() }
        rule.onNodeWithText(expectedFeed[2].title).assertIsDisplayed()

        rule.onNodeWithContentDescription("تنظیمات").performClick()
        rule.onNodeWithText("تاریک").assertIsSelected()
        UiAudit.screenshot(rule, "10_settings_dark")
        UiAudit.auditScreen(rule, "settings")

        rule.onNodeWithText("روشن").performClick()
        rule.onNodeWithText("روشن").assertIsSelected()
        UiAudit.screenshot(rule, "11_settings_light")

        rule.onNodeWithText("موضوعات مورد علاقه").performClick()
        rule.onNodeWithText(chosen.first().label).assertIsSelected()
        // Clearing every topic is allowed here and means "show everything".
        chosen.forEach { rule.onNodeWithText(it.label).performClick() }
        rule.onNodeWithText("موضوعی انتخاب نشده؛ دانک‌های همهٔ موضوعات نمایش داده می‌شوند.")
            .assertIsDisplayed()
        rule.onNodeWithText("ذخیره").assertIsEnabled()
        UiAudit.screenshot(rule, "12_edit_interests_light")
        rule.onNodeWithText("ذخیره").performClick()
        rule.onNodeWithText("همهٔ موضوعات").assertIsDisplayed()

        rule.onNodeWithContentDescription("بازگشت").performClick()
        // New feed, back at the top — not left on page 2 of a list that no longer exists.
        rule.onNodeWithText(MockDanaks.all[0].title).assertIsDisplayed()
        UiAudit.screenshot(rule, "13_feed_light")
        UiAudit.auditScreen(rule, "feed_light")

        rule.visibleNodeWithText("بیشتر بدان").performClick()
        UiAudit.screenshot(rule, "14_detail_light")
    }
}
