package ir.danak.app.ui.theme

import androidx.compose.ui.graphics.Color
import ir.danak.app.model.Category

/**
 * The accent a category lends to chips and small highlights. Kept out of [Category]
 * itself so the model stays free of UI types.
 */
val Category.accent: Color
    get() = when (this) {
        Category.Technology -> AccentTechnology
        Category.Programming -> AccentProgramming
        Category.Science -> AccentScience
        Category.Psychology -> AccentPsychology
        Category.Economy -> AccentEconomy
        Category.History -> AccentHistory
        Category.Productivity -> AccentProductivity
        Category.Curiosities -> AccentCuriosities
    }
