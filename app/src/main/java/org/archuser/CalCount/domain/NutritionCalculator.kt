package org.archuser.CalCount.domain

import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.VolumeUnit
import org.archuser.CalCount.data.model.WeightUnit

object NutritionCalculator {

    data class CalculationResult(
        val servings: Double,
        val weightGrams: Double = 0.0,
        val volumeMilliliters: Double = 0.0,
        val nutrition: NutritionSnapshot
    )

    fun calculateForServings(food: Food, servings: Double): CalculationResult {
        when (food.kind) {
            FoodKind.FOOD -> require(food.servingWeightGrams > 0.0) {
                "Serving weight must be greater than zero."
            }

            FoodKind.LIQUID -> require(food.servingVolumeMilliliters > 0.0) {
                "Serving volume must be greater than zero."
            }
        }
        require(servings > 0.0) { "Servings must be greater than zero." }

        val amountWeightGrams = when (food.kind) {
            FoodKind.FOOD -> food.servingWeightGrams * servings
            FoodKind.LIQUID -> 0.0
        }
        val amountVolumeMilliliters = when (food.kind) {
            FoodKind.FOOD -> 0.0
            FoodKind.LIQUID -> food.servingVolumeMilliliters * servings
        }

        return CalculationResult(
            servings = servings,
            weightGrams = amountWeightGrams,
            volumeMilliliters = amountVolumeMilliliters,
            nutrition = scaleNutrition(food.nutritionPerServing, servings)
        )
    }

    fun calculateForWeight(food: Food, weightGrams: Double): CalculationResult {
        require(food.servingWeightGrams > 0.0) { "Serving weight must be greater than zero." }
        require(weightGrams > 0.0) { "Weight must be greater than zero." }

        val servingFraction = weightGrams / food.servingWeightGrams
        return CalculationResult(
            servings = servingFraction,
            weightGrams = weightGrams,
            nutrition = scaleNutrition(food.nutritionPerServing, servingFraction)
        )
    }

    fun calculateForVolume(food: Food, volumeMilliliters: Double): CalculationResult {
        require(food.servingVolumeMilliliters > 0.0) { "Serving volume must be greater than zero." }
        require(volumeMilliliters > 0.0) { "Volume must be greater than zero." }

        val servingFraction = volumeMilliliters / food.servingVolumeMilliliters
        return CalculationResult(
            servings = servingFraction,
            volumeMilliliters = volumeMilliliters,
            nutrition = scaleNutrition(food.nutritionPerServing, servingFraction)
        )
    }

    fun convertToGrams(amount: Double, unit: WeightUnit): Double {
        require(amount >= 0.0) { "Weight cannot be negative." }
        return amount * unit.gramsPerUnit
    }

    fun convertFromGrams(weightGrams: Double, unit: WeightUnit): Double {
        require(weightGrams >= 0.0) { "Weight cannot be negative." }
        return weightGrams / unit.gramsPerUnit
    }

    fun convertToMilliliters(amount: Double, unit: VolumeUnit): Double {
        require(amount >= 0.0) { "Volume cannot be negative." }
        return amount * unit.millilitersPerUnit
    }

    fun convertFromMilliliters(volumeMilliliters: Double, unit: VolumeUnit): Double {
        require(volumeMilliliters >= 0.0) { "Volume cannot be negative." }
        return volumeMilliliters / unit.millilitersPerUnit
    }

    private fun scaleNutrition(nutrition: NutritionSnapshot, factor: Double): NutritionSnapshot {
        return NutritionSnapshot(
            calories = nutrition.calories * factor,
            fatGrams = nutrition.fatGrams * factor,
            carbsGrams = nutrition.carbsGrams * factor,
            proteinGrams = nutrition.proteinGrams * factor,
            saturatedFatGrams = nutrition.saturatedFatGrams?.times(factor),
            fiberGrams = nutrition.fiberGrams?.times(factor),
            sugarGrams = nutrition.sugarGrams?.times(factor),
            addedSugarsGrams = nutrition.addedSugarsGrams?.times(factor),
            sugarAlcoholsGrams = nutrition.sugarAlcoholsGrams?.times(factor),
            sodiumMilligrams = nutrition.sodiumMilligrams?.times(factor),
            potassiumMilligrams = nutrition.potassiumMilligrams?.times(factor),
            cholesterolMilligrams = nutrition.cholesterolMilligrams?.times(factor),
            transFatGrams = nutrition.transFatGrams?.times(factor),
            monounsaturatedFatGrams = nutrition.monounsaturatedFatGrams?.times(factor),
            polyunsaturatedFatGrams = nutrition.polyunsaturatedFatGrams?.times(factor),
            omega3FattyAcidsGrams = nutrition.omega3FattyAcidsGrams?.times(factor),
            omega6FattyAcidsGrams = nutrition.omega6FattyAcidsGrams?.times(factor),
            calciumMilligrams = nutrition.calciumMilligrams?.times(factor),
            chlorideMilligrams = nutrition.chlorideMilligrams?.times(factor),
            folateMicrograms = nutrition.folateMicrograms?.times(factor),
            ironMilligrams = nutrition.ironMilligrams?.times(factor),
            magnesiumMilligrams = nutrition.magnesiumMilligrams?.times(factor),
            phosphorusMilligrams = nutrition.phosphorusMilligrams?.times(factor),
            vitaminAMicrograms = nutrition.vitaminAMicrograms?.times(factor),
            vitaminB12Micrograms = nutrition.vitaminB12Micrograms?.times(factor),
            vitaminCMilligrams = nutrition.vitaminCMilligrams?.times(factor),
            vitaminDMicrograms = nutrition.vitaminDMicrograms?.times(factor),
            vitaminEMilligrams = nutrition.vitaminEMilligrams?.times(factor),
            vitaminKMicrograms = nutrition.vitaminKMicrograms?.times(factor),
            zincMilligrams = nutrition.zincMilligrams?.times(factor)
        )
    }
}
