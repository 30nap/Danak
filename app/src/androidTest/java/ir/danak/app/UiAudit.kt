package ir.danak.app

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Screenshots and an accessibility audit for the CI run.
 *
 * Files go to the app's internal storage so the workflow can pull them with `run-as`
 * regardless of scoped-storage rules on the emulator image.
 */
object UiAudit {

    private const val TAG = "DanakUiAudit"
    private const val MIN_TOUCH_DP = 48f

    private val prefix: String =
        InstrumentationRegistry.getArguments().getString("screenshotPrefix").orEmpty()

    private val dir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screenshots")
            .apply { mkdirs() }
    }

    fun screenshot(rule: ComposeTestRule, name: String) {
        rule.waitForIdle()
        // Coil decodes on its own dispatcher, which Compose's idling does not track.
        Thread.sleep(700)
        rule.waitForIdle()
        val bitmap = rule.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$prefix$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Adds a line to the report the CI job prints (used for measurements). */
    fun note(line: String) {
        Log.i(TAG, line)
        File(dir, "a11y-report.txt").appendText(line + "\n")
    }

    /**
     * Records clickable nodes whose touch target is under 48dp or that have no label a
     * screen reader could announce. Findings are reported, not asserted: they are UX
     * issues to triage, and one of them should not hide every functional failure.
     */
    fun auditScreen(rule: ComposeTestRule, screen: String) {
        rule.waitForIdle()
        val density = InstrumentationRegistry.getInstrumentation().targetContext
            .resources.displayMetrics.density
        val findings = mutableListOf<String>()
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        // Pages the pager keeps composed off screen are not part of what the user sees.
        val nodes = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            .filter { it.touchBoundsInRoot.overlaps(root) }
            // A node the viewport cuts off (the edge of a neighbouring pager page) reports
            // only its visible slice; measuring that would flag a 52dp button as 32dp.
            .filter { node ->
                val full = node.layoutInfo.coordinates.size
                node.boundsInRoot.height >= full.height - 1 && node.boundsInRoot.width >= full.width - 1
            }
        for (node in nodes) {
            val label = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            val bounds = node.touchBoundsInRoot
            val w = bounds.width / density
            val h = bounds.height / density
            // Nodes scrolled out of the viewport report empty bounds; skip them.
            if (w <= 0f || h <= 0f) continue
            if (w < MIN_TOUCH_DP || h < MIN_TOUCH_DP) {
                findings += "touch target %.0fx%.0fdp < 48dp: %s".format(w, h, label ?: "(no label)")
            }
            if (label.isNullOrBlank()) {
                findings += "clickable without a label at (%.0f, %.0f)dp".format(
                    bounds.left / density, bounds.top / density,
                )
            }
        }
        val report = if (findings.isEmpty()) {
            "[$prefix$screen] OK — ${nodes.size} clickable nodes"
        } else {
            "[$prefix$screen] ${findings.size} finding(s)\n" + findings.joinToString("\n") { "  - $it" }
        }
        Log.i(TAG, report)
        File(dir, "a11y-report.txt").appendText(report + "\n")
    }
}

/** The pager keeps the next page composed, so shared labels appear more than once. */
fun ComposeTestRule.visibleNodeWithText(text: String): SemanticsNodeInteraction {
    val all = onAllNodesWithText(text)
    val count = all.fetchSemanticsNodes().size
    for (i in 0 until count) {
        if (all[i].isDisplayed()) return all[i]
    }
    throw AssertionError("No visible node with text \"$text\" (found $count offscreen)")
}

/** As [visibleNodeWithText], for icon-only controls identified by their description. */
fun ComposeTestRule.visibleNodeWithDescription(description: String): SemanticsNodeInteraction {
    val all = onAllNodesWithContentDescription(description)
    val count = all.fetchSemanticsNodes().size
    for (i in 0 until count) {
        if (all[i].isDisplayed()) return all[i]
    }
    throw AssertionError("No visible node described \"$description\" (found $count offscreen)")
}
