package ir.danak.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import ir.danak.app.model.DanakImage

/**
 * Every hero in the app goes through here. Coil is used even for bundled artwork so that
 * pointing a Danak at [DanakImage.Remote] later needs no change on this side.
 */
@Composable
fun DanakHeroImage(
    image: DanakImage,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(image.model)
            .crossfade(durationMillis = 220)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/**
 * Starts decoding [image] into Coil's memory cache ahead of time, so the first frame that
 * shows it is a finished photo instead of a dark page the photo then fades into.
 *
 * The request has no size or transformations, so its cache entry is exactly the one a
 * later [DanakHeroImage] of the same image looks up.
 */
fun prefetchHero(context: Context, image: DanakImage) {
    context.imageLoader.enqueue(ImageRequest.Builder(context).data(image.model).build())
}

/**
 * The gradient that carries text over artwork. It stays clear at the top so the image can
 * breathe, then deepens to the page background well before the first line of text.
 */
@Composable
fun HeroScrim(
    background: Color,
    modifier: Modifier = Modifier,
    startFraction: Float = 0.30f,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    // Four stops rather than two: a linear fade leaves a visible grey haze
                    // over the middle of the image.
                    colorStops = arrayOf(
                        // A light veil over the whole photo: real photographs range from
                        // night skies to white studio backdrops, and this keeps them sitting
                        // in the same dark app rather than flashing between pages.
                        0f to background.copy(alpha = 0.10f),
                        startFraction to background.copy(alpha = 0.14f),
                        (startFraction + 0.24f) to background.copy(alpha = 0.66f),
                        (startFraction + 0.42f) to background.copy(alpha = 0.94f),
                        // Fully opaque a little before the edge and held there: ending the
                        // ramp on the last pixel left a one-pixel line of artwork showing.
                        0.93f to background,
                        1f to background,
                    ),
                ),
            ),
    )
}

/** A short scrim under the top bar, so its controls stay legible over a bright hero. */
@Composable
fun TopScrim(background: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to background.copy(alpha = 0.78f),
                        0.6f to background.copy(alpha = 0.22f),
                        1f to Color.Transparent,
                    ),
                ),
            ),
    )
}
