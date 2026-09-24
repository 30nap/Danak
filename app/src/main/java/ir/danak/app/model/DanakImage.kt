package ir.danak.app.model

import androidx.compose.runtime.Immutable

/**
 * Where a Danak's hero image comes from. Every hero is rendered through Coil via [model],
 * so a bundled photo and a published one differ only in data, never in UI code.
 */
@Immutable
sealed interface DanakImage {

    /** Anything Coil can load: an asset URI or a URL string. */
    val model: Any

    /** A photo shipped inside the APK, at [path] under `assets/`. */
    @JvmInline
    value class Asset(val path: String) : DanakImage {
        override val model: Any get() = "file:///android_asset/$path"
    }

    @JvmInline
    value class Remote(val url: String) : DanakImage {
        override val model: Any get() = url
    }
}
