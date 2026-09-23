package ir.danak.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * The feed's save control: just a bookmark in a quiet ring, so it sits beside «بیشتر بدان»
 * without competing with it. On save the icon fills and pops in, and the ring picks up the
 * accent — one small change, tied to one action.
 *
 * 48dp, so the touch target stays comfortable even though only an icon is visible.
 */
@Composable
fun SaveCircleButton(
    saved: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val accent = MaterialTheme.colorScheme.primary
    val ring by animateColorAsState(
        targetValue = if (saved) accent.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outline,
        animationSpec = tween(220),
        label = "saveRing",
    )
    val tint by animateColorAsState(
        targetValue = if (saved) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(220),
        label = "saveTint",
    )

    OutlinedIconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        border = BorderStroke(1.dp, ring),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        ),
        modifier = modifier
            .size(48.dp)
            .semantics { stateDescription = if (saved) "ذخیره شده" else "ذخیره نشده" },
    ) {
        AnimatedContent(
            targetState = saved,
            transitionSpec = {
                (scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), 0.6f) +
                    fadeIn(tween(120))) togetherWith fadeOut(tween(90))
            },
            label = "saveIcon",
        ) { isSaved ->
            Icon(
                imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = if (isSaved) "حذف از ذخیره‌شده‌ها" else "ذخیره کردن",
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
