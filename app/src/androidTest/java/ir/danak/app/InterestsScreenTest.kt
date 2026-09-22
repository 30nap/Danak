package ir.danak.app

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.danak.app.model.Category
import ir.danak.app.ui.screens.interests.InterestsScreen
import ir.danak.app.ui.theme.DanakTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterestsScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun continueIsDisabledUntilSomethingIsSelected() {
        rule.setContent {
            var selected by remember { mutableStateOf(emptySet<Category>()) }
            DanakTheme {
                InterestsScreen(
                    selected = selected,
                    onToggle = { category ->
                        selected = if (category in selected) selected - category else selected + category
                    },
                    onContinue = {},
                    continueLabel = "ادامه",
                    allowEmpty = false,
                    onBack = null,
                )
            }
        }

        rule.onNode(hasText("ادامه")).assertIsNotEnabled()
        rule.onNodeWithText(Category.History.label).performClick()
        rule.onNode(hasText("ادامه")).assertIsEnabled()
    }

    @Test
    fun tappingACategoryTwiceDeselectsIt() {
        val toggles = mutableListOf<Category>()
        rule.setContent {
            DanakTheme {
                InterestsScreen(
                    selected = emptySet(),
                    onToggle = { toggles += it },
                    onContinue = {},
                    continueLabel = "ادامه",
                    allowEmpty = false,
                    onBack = null,
                )
            }
        }

        rule.onNodeWithText(Category.Science.label).performClick()
        rule.onNodeWithText(Category.Science.label).performClick()

        assertEquals(listOf(Category.Science, Category.Science), toggles)
    }

    @Test
    fun editingMayClearEveryTopic() {
        rule.setContent {
            DanakTheme {
                InterestsScreen(
                    selected = emptySet(),
                    onToggle = {},
                    onContinue = {},
                    continueLabel = "ذخیره",
                    allowEmpty = true,
                    onBack = {},
                )
            }
        }

        rule.onNode(hasText("ذخیره")).assertIsEnabled()
        rule.onNodeWithText("موضوعی انتخاب نشده؛ دانک‌های همهٔ موضوعات نمایش داده می‌شوند.")
            .assertExists()
    }
}
