package ir.danak.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ir.danak.app.model.Danak
import ir.danak.app.ui.components.CategoryChip
import ir.danak.app.ui.components.DanakHeroImage
import ir.danak.app.ui.components.HeroScrim
import ir.danak.app.ui.components.SaveIconButton
import ir.danak.app.ui.components.SourceLink
import ir.danak.app.ui.components.TopScrim
import ir.danak.app.ui.util.formatReadingTime

private const val HERO_HEIGHT_FRACTION = 0.52f

/**
 * The deeper layer of a Danak. It keeps the same hero, the same chip and the same title
 * the feed showed, so opening it reads as sinking into the item rather than leaving it.
 */
@Composable
fun DetailScreen(
    danak: Danak,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val background = MaterialTheme.colorScheme.background
    val heroHeight = LocalConfiguration.current.screenHeightDp.dp * HERO_HEIGHT_FRACTION

    Box(modifier.fillMaxSize().background(background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .graphicsLayer {
                        // The hero stays put at half the scroll speed while the text
                        // rises over it.
                        translationY = scroll.value * 0.5f
                    },
            ) {
                DanakHeroImage(
                    image = danak.image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
                HeroScrim(background = background, startFraction = 0.34f)
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .background(background)
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CategoryChip(danak.category)

                Text(
                    text = danak.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                )

                Text(
                    text = formatReadingTime(danak.readingSeconds),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )

                for (section in danak.sections) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (section.heading != null) {
                            Text(
                                text = section.heading,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.semantics { heading() },
                            )
                        }
                        Text(
                            text = section.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

                SourceLink(
                    sourceName = danak.sourceName,
                    sourceUrl = danak.sourceUrl,
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                Spacer(Modifier.height(28.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        DetailTopBar(
            saved = saved,
            onToggleSave = onToggleSave,
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun DetailTopBar(
    saved: Boolean,
    onToggleSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(128.dp),
        ) {
            TopScrim(MaterialTheme.colorScheme.background)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "بازگشت",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(22.dp),
                )
            }
            SaveIconButton(
                saved = saved,
                onToggle = onToggleSave,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                savedTint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
