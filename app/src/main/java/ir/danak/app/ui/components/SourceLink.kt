package ir.danak.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import ir.danak.app.ui.theme.PillShape
import ir.danak.app.ui.util.sourceDomain

/**
 * Attribution, and the way out to the original. Deliberately the quietest thing on the
 * page: it should be findable, not noticeable.
 */
@Composable
fun SourceLink(
    sourceName: String,
    sourceUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val domain = remember(sourceUrl) { sourceDomain(sourceUrl) }
    Row(
        modifier = modifier
            .clip(PillShape)
            .clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, sourceUrl.toUri()))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "مرورگری برای باز کردن پیوند پیدا نشد", Toast.LENGTH_SHORT)
                        .show()
                }
            }
            .heightIn(min = 44.dp) // keeps the touch target usable without a visible button
            .padding(horizontal = 4.dp)
            .clearAndSetSemantics {
                contentDescription = "منبع: $sourceName. برای باز کردن در مرورگر لمس کنید"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = sourceName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = domain,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
    }
}
