package ir.danak.app.model

import androidx.compose.runtime.Immutable
import java.io.File

/**
 * Where a Danak's hero image comes from. Every hero is rendered through Coil via [model],
 * so a bundled photo and a downloaded one differ only in data, never in UI code.
 */
@Immutable
sealed interface DanakImage {

    /** Anything Coil can load: an asset URI, a file or a URL string; null for no image. */
    val model: Any?

    /** A photo shipped inside the APK, at [path] under `assets/`. */
    @JvmInline
    value class Asset(val path: String) : DanakImage {
        override val model: Any get() = "file:///android_asset/$path"
    }

    /** A published photo that was downloaded and verified into the app's own storage. */
    @JvmInline
    value class Cached(val path: String) : DanakImage {
        override val model: Any get() = File(path)
    }

    @JvmInline
    value class Remote(val url: String) : DanakImage {
        override val model: Any get() = url
    }

    /**
     * The published photo could not be used and there is no bundled one to fall back on.
     * The page shows its plain background; the text never depended on the photo.
     */
    data object None : DanakImage {
        override val model: Any? get() = null
    }
}
