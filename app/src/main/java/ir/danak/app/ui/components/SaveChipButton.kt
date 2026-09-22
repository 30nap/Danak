package ir.danak.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ir.danak.app.ui.theme.PillShape

/**
 * The feed's save control: a labelled pill that sits beside «بیشتر بدان» without
 * competing with it. The icon springs once on save and the label crossfades — enough
 * to confirm the tap, not enough to draw the eye back.
 */
@Composable
fun SaveChipButton(
    saved: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val iconScale by animateFloatAsState(
        targetValue = if (saved) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "saveChipScale",
    )

    FilledTonalButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onToggle()
        },
        shape = PillShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
            contentColor = if (saved) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = modifier
            .height(52.dp)
            .semantics { stateDescription = if (saved) "ذخیره شده" else "ذخیره نشده" },
    ) {
        Icon(
            imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            modifier = Modifier
                .size(18.dp)
                .scale(iconScale),
        )
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = saved,
            transitionSpec = { fadeIn(spring()) togetherWith fadeOut(spring()) },
            label = "saveLabel",
        ) { isSaved ->
            Text(
                text = if (isSaved) "ذخیره شد" else "ذخیره",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
