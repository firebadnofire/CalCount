package org.archuser.CalCount.ui

import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.IngredientUnit
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.data.model.InputMode
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.LogSourceType
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.archuser.CalCount.data.model.MacroUnit
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.VolumeUnit
import org.archuser.CalCount.data.model.WeightUnit
import org.archuser.CalCount.domain.NutritionCalculator
import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToLong

object UiFormatters {

    private val numberFormat = DecimalFormat("0.##")
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

    fun number(value: Double): String {
        val roundedWhole = value.roundToLong().toDouble()
        return if (abs(value - roundedWhole) < 0.005) {
            roundedWhole.toLong().toString()
        } else {
            numberFormat.format(value)
        }
    }

    fun calories(value: Double): String = "${nutritionLabelCaloriesValue(value)} cal"

    fun exactCalories(value: Double): String = "${number(value)} cal"

    fun grams(value: Double): String = "${number(value)} g"

    fun milligrams(value: Double): String = "${number(value)} mg"

    fun servings(value: Double): String {
        val label = if (abs(value - 1.0) < 0.005) "serving" else "servings"
        return "${number(value)} $label"
    }

    fun weight(valueGrams: Double, unit: WeightUnit): String {
        val convertedWeight = NutritionCalculator.convertFromGrams(valueGrams, unit)
        return "${number(convertedWeight)} ${unit.shortLabel}"
    }

    fun volume(valueMilliliters: Double, unit: VolumeUnit): String {
        val convertedVolume = NutritionCalculator.convertFromMilliliters(valueMilliliters, unit)
        return "${number(convertedVolume)} ${unit.shortLabel}"
    }

    fun macroLine(
        nutritionSnapshot: NutritionSnapshot,
        showProtein: Boolean = true,
        showCarbs: Boolean = true,
        showFat: Boolean = true
    ): String? {
        val parts = mutableListOf<String>()
        if (showProtein) {
            parts += "Protein ${number(nutritionSnapshot.proteinGrams)}g"
        }
        if (showCarbs) {
            parts += "Carbs ${number(nutritionSnapshot.carbsGrams)}g"
        }
        if (showFat) {
            parts += "Fat ${number(nutritionSnapshot.fatGrams)}g"
        }
        return parts.joinToString(" • ").ifBlank { null }
    }

    fun mainMacroCompactValue(
        nutritionSnapshot: NutritionSnapshot,
        mainMacro: MainMacro,
        completeness: NutrientCompleteness? = null
    ): String {
        if (completeness == NutrientCompleteness.UNKNOWN) return "Unknown"
        val formatted = formatMacroValue(mainMacro.valueOf(nutritionSnapshot), mainMacro.unit)
        return if (completeness == NutrientCompleteness.INCOMPLETE) "$formatted (incomplete)" else formatted
    }

    fun mainMacroLabeledValue(
        nutritionSnapshot: NutritionSnapshot,
        mainMacro: MainMacro,
        completeness: NutrientCompleteness? = null
    ): String {
        if (mainMacro == MainMacro.CALORIES && completeness != NutrientCompleteness.UNKNOWN &&
            completeness != NutrientCompleteness.INCOMPLETE) {
            return calories(nutritionSnapshot.calories)
        }
        return "${mainMacro.shortLabel} ${mainMacroCompactValue(nutritionSnapshot, mainMacro, completeness)}"
    }

    fun logCompleteness(entries: List<LogEntry>): Map<MainMacro, NutrientCompleteness> {
        return MainMacro.entries.mapNotNull { nutrient ->
            val hasUncertainty = entries.any {
                it.nutritionCompleteness?.get(nutrient) in
                    setOf(NutrientCompleteness.UNKNOWN, NutrientCompleteness.INCOMPLETE)
            }
            if (!hasUncertainty) return@mapNotNull null
            val hasValue = entries.any { nutrient.reportedValue(it.calculatedNutrition) != null }
            nutrient to if (hasValue) NutrientCompleteness.INCOMPLETE else NutrientCompleteness.UNKNOWN
        }.toMap()
    }

    fun logNutrientDetails(entry: LogEntry, goals: Goals): String {
        val statuses = entry.nutritionCompleteness
            ?: return macroSummarySelectionLine(entry.calculatedNutrition, goals).orEmpty()
        val summary = macroSummarySelectionLine(entry.calculatedNutrition, goals, completeness = statuses)
        val uncertain = MainMacro.entries.filter {
            it != goals.mainMacro && it.targetOf(goals) == null && !it.isSelectedInMacroSummary(goals)
        }
        val incomplete = uncertain.filter { statuses[it] == NutrientCompleteness.INCOMPLETE }
        val unknown = uncertain.filter { statuses[it] == NutrientCompleteness.UNKNOWN }
        return buildList {
            if (!summary.isNullOrBlank()) add(summary)
            incomplete.forEach { add(mainMacroLabeledValue(entry.calculatedNutrition, it, statuses[it])) }
            if (unknown.isNotEmpty()) add("Unknown: ${unknown.joinToString { it.displayName }}")
        }.joinToString("\n")
    }

    fun macroSummarySelectionLine(
        nutritionSnapshot: NutritionSnapshot,
        goals: Goals,
        includeCaloriesUnderMainMacro: Boolean = true,
        completeness: Map<MainMacro, NutrientCompleteness> = emptyMap()
    ): String? {
        val mainMacro = goals.mainMacro
        val parts = buildList {
            if (
                includeCaloriesUnderMainMacro &&
                goals.showCaloriesInLivePreview &&
                mainMacro != MainMacro.CALORIES
            ) {
                add(calories(nutritionSnapshot.calories))
            }
        }.toMutableList()

        val selectedMacros = summaryNutrients(goals).map { macro ->
            mainMacroLabeledValue(nutritionSnapshot, macro, completeness[macro])
        }
        parts += selectedMacros

        return parts.joinToString(" • ").ifBlank { null }
    }

    fun summaryNutrients(goals: Goals): List<MainMacro> = MainMacro.entries.filter {
        it != MainMacro.CALORIES && it != goals.mainMacro &&
            (it.targetOf(goals) != null || it.isSelectedInMacroSummary(goals))
    }

    fun recipeMetadata(recipe: Recipe): String {
        val count = recipe.ingredients.size
        val ingredient = if (count == 1) "ingredient" else "ingredients"
        return "$count $ingredient • Yields ${servings(recipe.servings)}"
    }

    fun ingredientAmount(quantity: Double, unit: IngredientUnit): String =
        if (unit == IngredientUnit.SERVINGS) servings(quantity)
        else "${number(quantity)} ${ingredientUnit(unit)}"

    fun ingredientUnit(unit: IngredientUnit): String = when (unit) {
        IngredientUnit.SERVINGS -> "servings"
        IngredientUnit.GRAMS -> "g"
        IngredientUnit.OUNCES -> "oz"
        IngredientUnit.MILLILITERS -> "mL"
        IngredientUnit.LITERS -> "L"
        IngredientUnit.FLUID_OUNCES -> "fl oz"
    }

    fun recipeNutrientValue(
        nutrient: MainMacro,
        nutrition: NutritionSnapshot,
        status: NutrientCompleteness?
    ): String {
        val value = nutrient.reportedValue(nutrition)
        if (value == null || status == NutrientCompleteness.UNKNOWN) return "Unknown"
        val formatted = if (nutrient == MainMacro.CALORIES) calories(value)
            else "${number(value)} ${nutrient.unit.suffix.trim()}"
        return if (status == NutrientCompleteness.INCOMPLETE) "$formatted subtotal" else formatted
    }

    fun recipeNutrientLabel(
        nutrient: MainMacro,
        nutrition: NutritionSnapshot,
        status: NutrientCompleteness?
    ): String {
        val value = recipeNutrientValue(nutrient, nutrition, status)
        return if (nutrient == MainMacro.CALORIES && value != "Unknown") value else "${nutrient.shortLabel} $value"
    }

    private fun formatMacroValue(value: Double, unit: MacroUnit): String {
        return when (unit) {
            MacroUnit.CALORIES -> calories(value)
            MacroUnit.GRAMS -> "${number(value)}g"
            MacroUnit.MILLIGRAMS -> "${number(value)}mg"
            MacroUnit.MICROGRAMS -> "${number(value)}mcg"
        }
    }

    fun entryAmount(logEntry: LogEntry, goals: Goals): String {
        if (logEntry.sourceType == LogSourceType.RECIPE) return servings(logEntry.consumedServings)
        val amountLabel = when (logEntry.foodKind) {
            FoodKind.FOOD -> weight(logEntry.consumedWeightGrams, goals.preferredUnit)
            FoodKind.LIQUID -> volume(logEntry.consumedVolumeMilliliters, goals.preferredLiquidUnit)
        }

        return when (logEntry.inputMode) {
            InputMode.SERVINGS -> "${servings(logEntry.consumedServings)} • " +
                amountLabel

            InputMode.WEIGHT -> "$amountLabel • ${servings(logEntry.consumedServings)}"
            InputMode.VOLUME -> "$amountLabel • ${servings(logEntry.consumedServings)}"
        }
    }

    fun time(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).format(timeFormat)
    }

    fun macroProgress(label: String, consumed: Double, target: Double?): String {
        return nutrientProgress(label, consumed, target, unit = "g")
    }

    fun previewNutrientLine(
        nutritionSnapshot: NutritionSnapshot,
        showProtein: Boolean,
        showCarbs: Boolean,
        showFat: Boolean,
        showSaturatedFat: Boolean,
        showFiber: Boolean,
        showSugars: Boolean,
        showSodium: Boolean,
        showPotassium: Boolean
    ): String? {
        val parts = mutableListOf<String>()
        if (showProtein) {
            parts += "Protein ${number(nutritionSnapshot.proteinGrams)}g"
        }
        if (showCarbs) {
            parts += "Carbs ${number(nutritionSnapshot.carbsGrams)}g"
        }
        if (showFat) {
            parts += "Fat ${number(nutritionSnapshot.fatGrams)}g"
        }
        if (showSaturatedFat) {
            parts += "Sat fat ${number(nutritionSnapshot.saturatedFatGrams ?: 0.0)}g"
        }
        if (showFiber) {
            parts += "Fiber ${number(nutritionSnapshot.fiberGrams ?: 0.0)}g"
        }
        if (showSugars) {
            parts += "Sugars ${number(nutritionSnapshot.sugarGrams ?: 0.0)}g"
        }
        if (showSodium) {
            parts += "Sodium ${number(nutritionSnapshot.sodiumMilligrams ?: 0.0)}mg"
        }
        if (showPotassium) {
            parts += "Potassium ${number(nutritionSnapshot.potassiumMilligrams ?: 0.0)}mg"
        }
        return parts.joinToString(" • ").ifBlank { null }
    }

    fun dashboardSummaryLines(
        nutritionSnapshot: NutritionSnapshot,
        goals: Goals,
        completeness: Map<MainMacro, NutrientCompleteness> = emptyMap()
    ): List<String> {
        val mainMacro = goals.mainMacro
        val lines = mutableListOf<String>()
        if (goals.showCaloriesInLivePreview) {
            lines += nutrientProgress(
                label = "Calories",
                consumed = nutritionSnapshot.calories,
                target = goals.dailyCalories,
                unit = " cal"
            )
        }

        lines += MainMacro.entries
            .filter { it != MainMacro.CALORIES }
            .filter { macro ->
                macro.targetOf(goals) != null ||
                    (macro != mainMacro && macro.isSelectedInMacroSummary(goals))
            }
            .map { macro ->
                if (completeness[macro] != null) {
                    return@map "${macro.displayName} ${mainMacroCompactValue(nutritionSnapshot, macro, completeness[macro])}"
                }
                val consumed = macro.valueOf(nutritionSnapshot)
                val target = macro.targetOf(goals)
                when (macro.unit) {
                    MacroUnit.GRAMS -> nutrientProgress(macro.displayName, consumed, target, unit = "g")
                    MacroUnit.MILLIGRAMS -> nutrientProgress(macro.displayName, consumed, target, unit = "mg")
                    MacroUnit.CALORIES -> nutrientProgress(macro.displayName, consumed, target, unit = " cal")
                    MacroUnit.MICROGRAMS -> nutrientProgress(macro.displayName, consumed, target, unit = "mcg")
                }
            }

        return lines
    }

    private fun nutrientProgress(label: String, consumed: Double, target: Double?, unit: String): String {
        return if (target == null) {
            "$label ${number(consumed)}$unit"
        } else {
            "$label ${number(consumed)} / ${number(target)}$unit"
        }
    }

    private fun nutritionLabelCaloriesValue(value: Double): Int {
        val positiveValue = value.coerceAtLeast(0.0)
        return when {
            positiveValue < 5.0 -> 0
            positiveValue <= 50.0 -> ((positiveValue / 5.0).roundToLong() * 5L).toInt()
            else -> ((positiveValue / 10.0).roundToLong() * 10L).toInt()
        }
    }
}
