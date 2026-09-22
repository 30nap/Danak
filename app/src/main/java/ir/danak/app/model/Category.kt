package ir.danak.app.model

/**
 * The eight topics Danak ships with in V0.
 *
 * [label] is the Persian name shown everywhere in the UI; [emoji] is used only in the
 * interest picker, where a little colour helps the grid read as a set of choices.
 */
enum class Category(val label: String, val emoji: String) {
    Technology("تکنولوژی", "🧠"),
    Programming("برنامه‌نویسی", "⌨️"),
    Science("علم", "🔬"),
    Psychology("روان‌شناسی", "💡"),
    Economy("اقتصاد", "📈"),
    History("تاریخ", "🏛️"),
    Productivity("بهره‌وری", "⚡"),
    Curiosities("دانستنی‌ها", "✨"),
}
