package ir.danak.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.danak.app.data.BundledContent
import ir.danak.app.ui.screens.feed.FeedScreen
import ir.danak.app.ui.theme.DanakTheme
import ir.danak.app.ui.theme.ImmersiveSurface
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val danaks =
        BundledContent.read(InstrumentationRegistry.getInstrumentation().targetContext).take(3)

    @Test
    fun theFirstDanakIsShownWithItsTitleAndActions() {
        rule.setContent {
            DanakTheme {
                FeedScreen(
                    danaks = danaks,
                    isSaved = { false },
                    onToggleSave = {},
                    onOpenDetail = {},
                    onOpenSaved = {},
                    onOpenSettings = {},
                    onEditInterests = {},
                )
            }
        }

        rule.onNodeWithText(danaks.first().title).assertIsDisplayed()
        // beyondViewportPageCount keeps the next page composed, so the action labels
        // exist more than once; the first in tree order is the visible page.
        rule.onAllNodesWithText("بیشتر بدان").onFirst().assertIsDisplayed()
        rule.visibleNodeWithDescription("ذخیره کردن").assertIsDisplayed()
    }

    @Test
    fun savingSwapsTheBookmarkState() {
        rule.setContent {
            var savedIds by remember { mutableStateOf(emptySet<String>()) }
            DanakTheme {
                FeedScreen(
                    danaks = danaks,
                    isSaved = { it in savedIds },
                    onToggleSave = { id ->
                        savedIds = if (id in savedIds) savedIds - id else savedIds + id
                    },
                    onOpenDetail = {},
                    onOpenSaved = {},
                    onOpenSettings = {},
                    onEditInterests = {},
                )
            }
        }

        rule.visibleNodeWithDescription("ذخیره کردن").performClick()
        rule.visibleNodeWithDescription("حذف از ذخیره‌شده‌ها").assertIsDisplayed()
    }

    @Test
    fun moreInfoReportsTheDanakThatWasOpened() {
        var opened: String? = null
        rule.setContent {
            DanakTheme {
                FeedScreen(
                    danaks = danaks,
                    isSaved = { false },
                    onToggleSave = {},
                    onOpenDetail = { opened = it },
                    onOpenSaved = {},
                    onOpenSettings = {},
                    onEditInterests = {},
                )
            }
        }

        rule.onAllNodesWithText("بیشتر بدان").onFirst().performClick()
        assertEquals(danaks.first().id, opened)
    }

    @Test
    fun theFeedEndsWithAWayForward() {
        rule.setContent {
            // Wrapped the way the app wraps it, so the screenshot shows the real, dark page.
            DanakTheme {
                ImmersiveSurface {
                    FeedScreen(
                        danaks = danaks,
                        isSaved = { false },
                        onToggleSave = {},
                        onOpenDetail = {},
                        onOpenSaved = {},
                        onOpenSettings = {},
                        onEditInterests = {},
                    )
                }
            }
        }

        repeat(danaks.size) { rule.onRoot().performTouchInput { swipeUp() } }
        rule.onNodeWithText("همهٔ دانک‌ها را خواندی").assertIsDisplayed()
        UiAudit.screenshot(rule, "15_end_of_feed")
        UiAudit.auditScreen(rule, "end_of_feed")

        rule.onNodeWithText("از اول").performClick()
        rule.onNodeWithText(danaks.first().title).assertIsDisplayed()
    }
}
