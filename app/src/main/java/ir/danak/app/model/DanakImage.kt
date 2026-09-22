package ir.danak.app.model

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable

/**
 * Where a Danak's hero image comes from.
 *
 * V0 ships only [Local] artwork, but every hero is rendered through Coil via [model],
 * so switching an item to a [Remote] URL later is a data change and not a UI change.
 */
@Immutable
sealed interface DanakImage {

    /** Anything Coil can load: a drawable id or a URL string. */
    val model: Any

    @JvmInline
    value class Local(@param:DrawableRes val resId: Int) : DanakImage {
        override val model: Any get() = resId
    }

    @JvmInline
    value class Remote(val url: String) : DanakImage {
        override val model: Any get() = url
    }
}
