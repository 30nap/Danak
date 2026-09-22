package ir.danak.app.ui.screens.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ir.danak.app.data.MockDanaks
import ir.danak.app.model.Danak
import ir.danak.app.model.ThemeMode
import ir.danak.app.ui.components.CategoryChip
import ir.danak.app.ui.components.DanakHeroImage
import ir.danak.app.ui.components.HeroScrim
import ir.danak.app.ui.components.SaveChipButton
import ir.danak.app.ui.components.SourceLink
import ir.danak.app.ui.theme.DanakTheme
import ir.danak.app.ui.theme.PillShape
import ir.danak.app.ui.util.formatReadingTime
import kotlin.math.absoluteValue

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
    Box(modifier.fillMaxSize()) {
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
                .fillMaxWidth()
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                .graphicsLayer {
                    val offset = pageOffset()
                    // Text settles into place a little after the image does.
                    translationY = offset * size.height * 0.22f
                    alpha = 1f - offset.absoluteValue.coerceAtMost(1f)
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CategoryChip(danak.category)

            Text(
                text = danak.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )

            Text(
                text = danak.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )

            ReadingTime(danak.readingSeconds)

            Spacer(Modifier.height(2.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SaveChipButton(saved = saved, onToggle = onToggleSave)

                Button(
                    onClick = onOpenDetail,
                    shape = PillShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
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
            }

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
            danak = MockDanaks.all.first(),
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
            danak = MockDanaks.all[1],
            saved = true,
            onToggleSave = {},
            onOpenDetail = {},
            pageOffset = { 0f },
        )
    }
}
