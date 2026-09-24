package ir.danak.app.model

/**
 * The eight topics Danak ships with.
 *
 * [id] is how content files name the category (schema v1); it never changes, whatever the
 * enum constant is called. [label] is the Persian name shown everywhere in the UI; [emoji]
 * is used only in the interest picker, where a little colour helps the grid read as a set
 * of choices.
 */
enum class Category(val id: String, val label: String, val emoji: String) {
    Technology("technology", "تکنولوژی", "🛰️"),
    Programming("programming", "برنامه‌نویسی", "⌨️"),
    Science("science", "علم", "🔬"),
    Psychology("psychology", "روان‌شناسی", "🧠"),
    Economy("economy", "اقتصاد", "📈"),
    History("history", "تاریخ", "🏛️"),
    Productivity("productivity", "بهره‌وری", "⚡"),
    Curiosities("curiosities", "دانستنی‌ها", "✨"),
    ;

    companion object {
        fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}
