package org.archuser.CalCount.ui

import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.InputMode
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.NutritionSnapshot
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

    fun mainMacroValue(nutritionSnapshot: NutritionSnapshot, mainMacro: MainMacro): String {
        return when (mainMacro) {
            MainMacro.CALORIES -> calories(nutritionSnapshot.calories)
            MainMacro.PROTEIN -> "Protein ${number(nutritionSnapshot.proteinGrams)}g"
            MainMacro.CARBS -> "Carbs ${number(nutritionSnapshot.carbsGrams)}g"
            MainMacro.FAT -> "Fat ${number(nutritionSnapshot.fatGrams)}g"
        }
    }

    fun macroSummarySelectionLine(nutritionSnapshot: NutritionSnapshot, goals: Goals): String? {
        val showProtein = (goals.proteinTargetGrams != null || goals.showProteinInLivePreview) &&
            goals.mainMacro != MainMacro.PROTEIN
        val showCarbs = (goals.carbsTargetGrams != null || goals.showCarbsInLivePreview) &&
            goals.mainMacro != MainMacro.CARBS
        val showFat = (goals.fatTargetGrams != null || goals.showFatInLivePreview) &&
            goals.mainMacro != MainMacro.FAT

        val showSaturatedFat = goals.saturatedFatTargetGrams != null || goals.showSaturatedFatInLivePreview
        val showFiber = goals.fiberTargetGrams != null || goals.showFiberInLivePreview
        val showSugars = goals.sugarsTargetGrams != null || goals.showSugarsInLivePreview
        val showSodium = goals.sodiumTargetMilligrams != null || goals.showSodiumInLivePreview
        val showPotassium = goals.potassiumTargetMilligrams != null || goals.showPotassiumInLivePreview

        return previewNutrientLine(
            nutritionSnapshot = nutritionSnapshot,
            showProtein = showProtein,
            showCarbs = showCarbs,
            showFat = showFat,
            showSaturatedFat = showSaturatedFat,
            showFiber = showFiber,
            showSugars = showSugars,
            showSodium = showSodium,
            showPotassium = showPotassium
        )
    }

    fun entryAmount(logEntry: LogEntry, preferredUnit: WeightUnit): String {
        return when (logEntry.inputMode) {
            InputMode.SERVINGS -> "${servings(logEntry.consumedServings)} • " +
                weight(logEntry.consumedWeightGrams, preferredUnit)

            InputMode.WEIGHT -> "${weight(logEntry.consumedWeightGrams, preferredUnit)} • " +
                servings(logEntry.consumedServings)
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
        goals: Goals
    ): List<String> {
        return buildList {
            val shouldShowProtein = goals.proteinTargetGrams != null ||
                (goals.showProteinInLivePreview && goals.mainMacro != MainMacro.PROTEIN)
            if (shouldShowProtein) {
                add(
                    macroProgress(
                        label = "Protein",
                        consumed = nutritionSnapshot.proteinGrams,
                        target = goals.proteinTargetGrams
                    )
                )
            }

            val shouldShowCarbs = goals.carbsTargetGrams != null ||
                (goals.showCarbsInLivePreview && goals.mainMacro != MainMacro.CARBS)
            if (shouldShowCarbs) {
                add(
                    macroProgress(
                        label = "Carbs",
                        consumed = nutritionSnapshot.carbsGrams,
                        target = goals.carbsTargetGrams
                    )
                )
            }

            val shouldShowFat = goals.fatTargetGrams != null ||
                (goals.showFatInLivePreview && goals.mainMacro != MainMacro.FAT)
            if (shouldShowFat) {
                add(
                    macroProgress(
                        label = "Fat",
                        consumed = nutritionSnapshot.fatGrams,
                        target = goals.fatTargetGrams
                    )
                )
            }

            val shouldShowSaturatedFat =
                goals.saturatedFatTargetGrams != null || goals.showSaturatedFatInLivePreview
            if (shouldShowSaturatedFat) {
                add(
                    macroProgress(
                        label = "Saturated fat",
                        consumed = nutritionSnapshot.saturatedFatGrams ?: 0.0,
                        target = goals.saturatedFatTargetGrams
                    )
                )
            }

            val shouldShowFiber = goals.fiberTargetGrams != null || goals.showFiberInLivePreview
            if (shouldShowFiber) {
                add(
                    macroProgress(
                        label = "Fiber",
                        consumed = nutritionSnapshot.fiberGrams ?: 0.0,
                        target = goals.fiberTargetGrams
                    )
                )
            }

            val shouldShowSugars = goals.sugarsTargetGrams != null || goals.showSugarsInLivePreview
            if (shouldShowSugars) {
                add(
                    macroProgress(
                        label = "Sugars",
                        consumed = nutritionSnapshot.sugarGrams ?: 0.0,
                        target = goals.sugarsTargetGrams
                    )
                )
            }

            val shouldShowSodium =
                goals.sodiumTargetMilligrams != null || goals.showSodiumInLivePreview
            if (shouldShowSodium) {
                add(
                    nutrientProgress(
                        label = "Sodium",
                        consumed = nutritionSnapshot.sodiumMilligrams ?: 0.0,
                        target = goals.sodiumTargetMilligrams,
                        unit = "mg"
                    )
                )
            }

            val shouldShowPotassium =
                goals.potassiumTargetMilligrams != null || goals.showPotassiumInLivePreview
            if (shouldShowPotassium) {
                add(
                    nutrientProgress(
                        label = "Potassium",
                        consumed = nutritionSnapshot.potassiumMilligrams ?: 0.0,
                        target = goals.potassiumTargetMilligrams,
                        unit = "mg"
                    )
                )
            }
        }
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
