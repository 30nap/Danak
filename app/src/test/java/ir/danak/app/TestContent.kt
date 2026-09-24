package ir.danak.app

import ir.danak.app.data.ContentPack
import ir.danak.app.model.Danak
import ir.danak.app.model.DanakImage
import java.io.File

/**
 * The bundled content pack, read straight from the source tree (unit tests run with the
 * app module as their working directory), through the same parser the app uses.
 */
object TestContent {
    val dir = File("src/main/assets/content")

    val parsed: ContentPack.Parsed by lazy {
        ContentPack.parse(File(dir, "content.json").readText()) { src -> DanakImage.Asset("content/$src") }
    }

    val all: List<Danak> get() = parsed.danaks
}
