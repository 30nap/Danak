package ir.danak.app.ui.screens.feed

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.danak.app.model.Danak
import ir.danak.app.ui.components.FeedProgress
import ir.danak.app.ui.components.TopScrim

/**
 * The feed. One Danak fills one screen, a swipe moves exactly one item, and nothing on
 * top of the artwork exists unless it earns its place.
 */
@Composable
fun FeedScreen(
    danaks: List<Danak>,
    isSaved: (String) -> Boolean,
    onToggleSave: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { danaks.size })

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (danaks.isEmpty()) {
            EmptyFeed(Modifier.align(Alignment.Center))
        } else {
            VerticalPager(
                state = pagerState,
                // One page either side is enough to have the next hero decoded before it
                // is swiped to, without holding four bitmaps in memory.
                beyondViewportPageCount = 1,
                key = { index -> danaks[index].id },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val danak = danaks[page]
                FeedPage(
                    danak = danak,
                    saved = isSaved(danak.id),
                    onToggleSave = { onToggleSave(danak.id) },
                    onOpenDetail = { onOpenDetail(danak.id) },
                    pageOffset = { pagerState.offsetForPage(page) },
                )
            }

            SettleHaptics(pagerState)

            FeedProgress(
                pageCount = danaks.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp),
            )
        }

        FeedTopBar(
            onOpenSaved = onOpenSaved,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * A short tick when a new Danak settles — the same confirmation a physical detent gives.
 * It fires on settle rather than on every frame, and never on first composition.
 */
@Composable
private fun SettleHaptics(pagerState: PagerState) {
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pagerState) {
        var previous = pagerState.settledPage
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != previous) {
                previous = page
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }
}

@Composable
private fun FeedTopBar(
    onOpenSaved: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(132.dp),
        ) {
            TopScrim(MaterialTheme.colorScheme.background)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "دانَک",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSaved) {
                Icon(
                    Icons.Outlined.BookmarkBorder,
                    contentDescription = "ذخیره‌شده‌ها",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "تنظیمات",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyFeed(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "هنوز دانکی اینجا نیست",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "از تنظیمات، موضوعات مورد علاقه‌ات را انتخاب کن.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
