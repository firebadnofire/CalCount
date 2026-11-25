package org.archuser.CalCount

import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.IngredientUnit
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.ui.UiFormatters
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipePresentationTest {
    @Test fun distinguishesReportedZeroFromSubtotalsAndUnknown() {
        val reported = NutritionSnapshot.ZERO.copy(sodiumMilligrams = 0.0)
        val sodium = MainMacro.SODIUM
        assertEquals("0 mg", UiFormatters.recipeNutrientValue(sodium, reported, NutrientCompleteness.COMPLETE))
        assertEquals("0 mg subtotal", UiFormatters.recipeNutrientValue(sodium, reported, NutrientCompleteness.INCOMPLETE))
        assertEquals("Unknown", UiFormatters.recipeNutrientValue(sodium, NutritionSnapshot.ZERO, NutrientCompleteness.UNKNOWN))
    }

    @Test fun usesHumanReadableIngredientUnits() {
        assertEquals("1 serving", UiFormatters.ingredientAmount(1.0, IngredientUnit.SERVINGS))
        assertEquals("2 servings", UiFormatters.ingredientAmount(2.0, IngredientUnit.SERVINGS))
        assertEquals("1 fl oz", UiFormatters.ingredientAmount(1.0, IngredientUnit.FLUID_OUNCES))
    }

    @Test fun summaryHonorsPreferencesWithoutRepeatingPrimary() {
        val goals = Goals(mainMacro = MainMacro.PROTEIN, showCaloriesInLivePreview = true,
            macroSummarySelection = setOf(MainMacro.PROTEIN, MainMacro.SODIUM))
        assertEquals(listOf(MainMacro.SODIUM), UiFormatters.summaryNutrients(goals))
    }
}
