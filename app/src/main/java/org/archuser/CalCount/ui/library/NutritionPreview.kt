package org.archuser.CalCount.ui.library

import androidx.core.view.isVisible
import org.archuser.CalCount.R
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.databinding.NutritionPreviewBinding
import org.archuser.CalCount.ui.UiFormatters

internal fun NutritionPreviewBinding.showMessage(message: String, showPlaceholders: Boolean = false) {
    previewAmount.text = message
    previewAmount.isVisible = true
    previewCalories.text = root.context.getString(R.string.preview_waiting_calories)
    previewCalories.isVisible = showPlaceholders
    previewSecondaryCalories.isVisible = false
    previewMacros.text = root.context.getString(R.string.preview_waiting_macros)
    previewMacros.isVisible = showPlaceholders
    previewLegend.isVisible = false
}

internal fun NutritionPreviewBinding.showNutrition(
    nutrition: NutritionSnapshot,
    goals: Goals,
    amount: String? = null,
    completeness: Map<MainMacro, NutrientCompleteness> = emptyMap()
) {
    previewAmount.text = amount
    previewAmount.isVisible = amount != null
    val primary = goals.mainMacro
    val secondary = goals.showCaloriesInLivePreview && primary != MainMacro.CALORIES
    if (completeness.isEmpty()) {
        previewCalories.text = UiFormatters.mainMacroLabeledValue(nutrition, primary)
        previewSecondaryCalories.text = UiFormatters.calories(nutrition.calories)
        previewMacros.text = UiFormatters.macroSummarySelectionLine(nutrition, goals, false)
            ?: root.context.getString(R.string.preview_macros_hidden)
    } else {
        fun label(nutrient: MainMacro) = UiFormatters.recipeNutrientLabel(nutrient, nutrition, completeness[nutrient])
        previewCalories.text = label(primary)
        previewSecondaryCalories.text = label(MainMacro.CALORIES)
        previewMacros.text = UiFormatters.summaryNutrients(goals).joinToString(" • ", transform = ::label)
            .ifEmpty { root.context.getString(R.string.preview_macros_hidden) }
    }
    previewCalories.isVisible = true
    previewSecondaryCalories.isVisible = secondary
    previewMacros.isVisible = true
    val displayed = UiFormatters.summaryNutrients(goals) + primary + if (secondary) listOf(MainMacro.CALORIES) else emptyList()
    previewLegend.isVisible = displayed.any { completeness[it] == NutrientCompleteness.INCOMPLETE }
    previewLegend.text = root.context.getString(R.string.recipe_subtotal_legend)
}
