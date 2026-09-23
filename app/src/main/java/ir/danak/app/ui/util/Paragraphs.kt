package ir.danak.app.ui.util

private val SENTENCE_END = Regex("""(?<=[.!؟?])\s+""")

/**
 * Breaks a block of explanation into short paragraphs of at most [maxSentences] sentences.
 * Long unbroken Persian paragraphs are tiring on a phone; this keeps every block to a
 * glance without rewriting the content by hand.
 *
 * A trailing short sentence is folded into the previous paragraph rather than left alone
 * as a one-line orphan.
 */
fun splitIntoParagraphs(text: String, maxSentences: Int = 2): List<String> {
    val sentences = text.trim().split(SENTENCE_END).map { it.trim() }.filter { it.isNotEmpty() }
    if (sentences.size <= maxSentences) return listOf(sentences.joinToString(" ")).filter { it.isNotEmpty() }

    val paragraphs = sentences.chunked(maxSentences).map { it.joinToString(" ") }.toMutableList()
    val last = paragraphs.last()
    if (paragraphs.size > 1 && last.length < 60) {
        paragraphs.removeAt(paragraphs.lastIndex)
        paragraphs[paragraphs.lastIndex] = paragraphs.last() + " " + last
    }
    return paragraphs
}
