package ir.danak.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * A thin column of ticks showing where you are in the feed. It is decorative — the pager
 * itself carries the accessible position — so it is hidden from screen readers.
 *
 * With more items than [maxTicks] the column becomes a sliding window, which keeps the
 * indicator the same height whether the feed holds eight Danaks or eighty.
 */
@Composable
fun FeedProgress(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
    maxTicks: Int = 7,
) {
    if (pageCount <= 1) return
    val visible = min(pageCount, maxTicks)
    val half = visible / 2
    val start = max(0, min(currentPage - half, pageCount - visible))

    Column(
        modifier = modifier.clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (index in start until start + visible) {
            val active = index == currentPage
            val height by animateDpAsState(
                targetValue = if (active) 22.dp else 6.dp,
                animationSpec = tween(220),
                label = "tickHeight",
            )
            val color by animateColorAsState(
                targetValue = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground.copy(alpha = 0.28f)
                },
                animationSpec = tween(220),
                label = "tickColor",
            )
            Spacer(
                Modifier
                    .width(3.dp)
                    .height(height)
                    .background(color, RoundedCornerShape(2.dp)),
            )
        }
    }
}
