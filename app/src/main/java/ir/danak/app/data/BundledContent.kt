package ir.danak.app.data

import android.content.Context
import ir.danak.app.model.Danak
import ir.danak.app.model.DanakImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The content pack shipped inside the APK, under `assets/content/`: `content.json` and the
 * photos it points to in `images/`. It is what the app shows with no network, and the
 * layout is the same as a published pack's.
 */
object BundledContent {

    private const val DIR = "content"

    /** Blocking read, for tests and previews; the app goes through [load]. */
    fun read(context: Context): List<Danak> {
        val text = context.assets.open("$DIR/content.json").bufferedReader().use { it.readText() }
        return ContentPack.parse(text) { src -> DanakImage.Asset("$DIR/$src") }.danaks
    }

    suspend fun load(context: Context): List<Danak> = withContext(Dispatchers.IO) { read(context) }
}
