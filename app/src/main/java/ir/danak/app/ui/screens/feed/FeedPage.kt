package ir.danak.app.ui.screens.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ir.danak.app.data.BundledContent
import ir.danak.app.model.Danak
import ir.danak.app.model.ThemeMode
import ir.danak.app.ui.components.CategoryChip
import ir.danak.app.ui.components.DanakHeroImage
import ir.danak.app.ui.components.HeroScrim
import ir.danak.app.ui.components.SaveCircleButton
import ir.danak.app.ui.components.SourceLink
import ir.danak.app.ui.theme.DanakTheme
import ir.danak.app.ui.theme.PillShape
import ir.danak.app.ui.util.formatReadingTime
import kotlin.math.absoluteValue

/** Below this height the page switches to its compact arrangement. */
private val COMPACT_HEIGHT = 560.dp

/** The widest a line of feed text is allowed to run. */
private val MAX_TEXT_WIDTH = 560.dp

/**
 * How far this page is from settled, in pages: 0 when it fills the screen, ±1 when it is
 * exactly one swipe away. Drives the parallax and the fade of the outgoing page.
 */
fun PagerState.offsetForPage(page: Int): Float =
    (currentPage - page) + currentPageOffsetFraction

@Composable
fun FeedPage(
    danak: Danak,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onOpenDetail: () -> Unit,
    pageOffset: () -> Float,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // A landscape phone or a split-screen window leaves little more than 360dp of
        // height: at full size the title and three summary lines climbed under the top bar.
        val compact = maxHeight < COMPACT_HEIGHT
        val topBarBottom =
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + FeedTopBarHeight

        DanakHeroImage(
            image = danak.image,
            // The artwork is atmosphere; the title below carries the meaning, so
            // announcing it again would only make the page noisier to listen to.
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Read inside the layer block, so a swipe only redraws the page
                    // instead of recomposing every page that the pager keeps alive.
                    val offset = pageOffset()
                    // The hero drifts at a fraction of the swipe, which gives the feed
                    // depth without ever detaching the image from the gesture.
                    translationY = -offset * size.height * 0.14f
                    alpha = 1f - offset.absoluteValue.coerceAtMost(1f) * 0.35f
                },
        )

        HeroScrim(
            background = MaterialTheme.colorScheme.background,
            startFraction = 0.26f,
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                // On a tablet a full-width column would stretch a line of Persian across
                // the whole screen; this keeps it at a readable measure.
                .widthIn(max = MAX_TEXT_WIDTH)
                .fillMaxWidth()
                // Never taller than the space under the top bar. If a long title at a large
                // font size still does not fit, the summary gives up lines (with an
                // ellipsis) rather than the title sliding under the controls.
                .heightIn(max = (maxHeight - topBarBottom).coerceAtLeast(0.dp))
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(start = 24.dp, end = 24.dp, bottom = if (compact) 8.dp else 20.dp)
                .graphicsLayer {
                    val offset = pageOffset()
                    // Text settles into place a little after the image does.
                    translationY = offset * size.height * 0.22f
                    alpha = 1f - offset.absoluteValue.coerceAtMost(1f)
                },
        ) {
            // Spacing is set per gap rather than uniformly: the title gets room to breathe,
            // the secondary lines sit close to what they describe.
            if (compact) {
                // Short window: the reading time moves up beside the chip to save a row.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CategoryChip(danak.category)
                    ReadingTime(danak.readingSeconds)
                }
            } else {
                CategoryChip(danak.category)
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

            // The title is the page — everything else is secondary to it.
            Text(
                text = danak.title,
                style = if (compact) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineLarge
                },
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))

            // Two or three lines: enough to make the title worth opening, never a wall.
            Text(
                text = danak.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )

            if (!compact) {
                Spacer(Modifier.height(10.dp))
                ReadingTime(danak.readingSeconds)
            }
            Spacer(Modifier.height(if (compact) 12.dp else 20.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Primary action first, so in RTL it leads from the right; it sizes to its
                // label instead of spanning the row, which kept it from dominating.
                Button(
                    onClick = onOpenDetail,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    modifier = Modifier
                        .height(46.dp)
                        .widthIn(min = 148.dp),
                ) {
                    Text("بیشتر بدان", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        // Auto-mirrored, so "forward" points the way Persian reads.
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                SaveCircleButton(saved = saved, onToggle = onToggleSave)
            }
            Spacer(Modifier.height(8.dp))

            SourceLink(sourceName = danak.sourceName, sourceUrl = danak.sourceUrl)
        }
    }
}

@Composable
private fun ReadingTime(seconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = formatReadingTime(seconds),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
}

@Preview(name = "Feed — dark", showBackground = true, device = Devices.PIXEL_7)
@Composable
private fun FeedPageDarkPreview() {
    DanakTheme(themeMode = ThemeMode.Dark) {
        FeedPage(
            danak = previewDanaks()[0],
            saved = false,
            onToggleSave = {},
            onOpenDetail = {},
            pageOffset = { 0f },
        )
    }
}

@Preview(name = "Feed — light, saved", showBackground = true, device = Devices.PIXEL_7)
@Composable
private fun FeedPageLightPreview() {
    DanakTheme(themeMode = ThemeMode.Light) {
        FeedPage(
            danak = previewDanaks()[1],
            saved = true,
            onToggleSave = {},
            onOpenDetail = {},
            pageOffset = { 0f },
        )
    }
}

/** Previews render the real bundled content rather than a copy of it. */
@Composable
private fun previewDanaks(): List<Danak> {
    val context = LocalContext.current
    return remember { BundledContent.read(context) }
}
