package ir.danak.app.model

import androidx.compose.runtime.Immutable

/**
 * One small piece of knowledge — the unit the whole app is built around.
 *
 * [summary] is what the feed shows (two or three lines); [sections] is the longer read
 * behind «بیشتر بدان», closed by an optional [keyTakeaway] — the one sentence worth
 * remembering.
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
    val keyTakeaway: String? = null,
    val photoCredit: PhotoCredit? = null,
)

/**
 * Who made the hero photo and under which licence. CC BY and CC BY-SA require this to be
 * shown; [pageUrl] is the photo's page on Wikimedia Commons.
 */
@Immutable
data class PhotoCredit(
    val author: String,
    val license: String,
    val pageUrl: String,
)

/** A titled block of the full explanation. A null [heading] means an opening paragraph. */
@Immutable
data class DanakSection(
    val heading: String?,
    val body: String,
)
