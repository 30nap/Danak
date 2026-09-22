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
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.danak.app.data.MockDanaks
import ir.danak.app.ui.screens.feed.FeedScreen
import ir.danak.app.ui.theme.DanakTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FeedScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val danaks = MockDanaks.all.take(3)

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
                )
            }
        }

        rule.onNodeWithText(danaks.first().title).assertIsDisplayed()
        // beyondViewportPageCount keeps the next page composed, so the action labels
        // exist more than once; the first in tree order is the visible page.
        rule.onAllNodesWithText("بیشتر بدان").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("ذخیره").onFirst().assertIsDisplayed()
    }

    @Test
    fun savingSwapsTheButtonLabel() {
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
                )
            }
        }

        rule.onAllNodesWithText("ذخیره").onFirst().performClick()
        rule.onAllNodesWithText("ذخیره شد").onFirst().assertIsDisplayed()
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
                )
            }
        }

        rule.onAllNodesWithText("بیشتر بدان").onFirst().performClick()
        assertEquals(danaks.first().id, opened)
    }
}
