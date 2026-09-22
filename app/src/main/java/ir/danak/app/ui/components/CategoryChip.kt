package ir.danak.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.danak.app.model.Category
import ir.danak.app.ui.theme.PillShape
import ir.danak.app.ui.theme.accent

/** The quiet label above a title. It names the topic without competing with it. */
@Composable
fun CategoryChip(
    category: Category,
    modifier: Modifier = Modifier,
) {
    val accent = category.accent
    Text(
        text = category.label,
        style = MaterialTheme.typography.labelMedium,
        color = accent,
        modifier = modifier
            .background(accent.copy(alpha = 0.13f), PillShape)
            .border(1.dp, accent.copy(alpha = 0.26f), PillShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}
