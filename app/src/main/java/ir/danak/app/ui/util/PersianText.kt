package ir.danak.app.ui.util

private const val PERSIAN_ZERO = '۰'

/**
 * Rewrites ASCII digits as Persian ones. Everything numeric the user reads goes through
 * here — a Persian interface that prints "45" instead of "۴۵" reads as a translation.
 */
fun String.toPersianDigits(): String {
    if (none { it in '0'..'9' }) return this
    return map { ch -> if (ch in '0'..'9') PERSIAN_ZERO + (ch - '0') else ch }
        .joinToString("")
}

fun Int.toPersianDigits(): String = toString().toPersianDigits()

/**
 * Reading time, phrased the way a person would say it: seconds below a minute,
 * whole minutes above it.
 */
fun formatReadingTime(seconds: Int): String = when {
    seconds < 60 -> "${seconds.toPersianDigits()} ثانیه"
    seconds % 60 == 0 -> "${(seconds / 60).toPersianDigits()} دقیقه"
    else -> "${(seconds / 60).toPersianDigits()} دقیقه و ${(seconds % 60).toPersianDigits()} ثانیه"
}

/** Short form for dense surfaces such as the saved list. */
fun formatReadingTimeShort(seconds: Int): String =
    if (seconds < 60) "${seconds.toPersianDigits()} ثانیه"
    else "${(seconds / 60 + if (seconds % 60 >= 30) 1 else 0).coerceAtLeast(1).toPersianDigits()} دقیقه"

/**
 * The publisher's own domain, shown next to the source name. Kept in Latin on purpose —
 * transliterating a domain would make it unrecognisable.
 */
fun sourceDomain(url: String): String = url
    .substringAfter("://", url)
    .substringBefore('/')
    .removePrefix("www.")
