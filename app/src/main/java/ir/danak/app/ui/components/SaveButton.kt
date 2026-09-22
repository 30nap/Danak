package ir.danak.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * A bookmark toggle that answers immediately: the icon fills and springs a little past its
 * size on the way in. One animation, tied to one state change.
 */
@Composable
fun SaveIconButton(
    saved: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    savedTint: Color = tint,
) {
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (saved) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "saveScale",
    )
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        modifier = modifier.semantics {
            stateDescription = if (saved) "ذخیره شده" else "ذخیره نشده"
        },
    ) {
        Icon(
            imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (saved) "حذف از ذخیره‌شده‌ها" else "ذخیره کردن",
            tint = if (saved) savedTint else tint,
            modifier = Modifier
                .size(24.dp)
                .scale(scale),
        )
    }
}
