package ir.danak.app.model

import androidx.compose.runtime.Immutable

/**
 * One small piece of knowledge — the unit the whole app is built around.
 *
 * [summary] is what the feed shows (three to five lines); [sections] is the longer read
 * behind «بیشتر بدان».
 */
@Immutable
data class Danak(
    val id: String,
    val category: Category,
    val title: String,
    val summary: String,
    val sections: List<DanakSection>,
    val readingSeconds: Int,
    val image: DanakImage,
    val sourceName: String,
    val sourceUrl: String,
)

/** A titled block of the full explanation. A null [heading] means an opening paragraph. */
@Immutable
data class DanakSection(
    val heading: String?,
    val body: String,
)
