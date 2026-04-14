package org.archuser.CalCount.domain

import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.WeightUnit

object NutritionCalculator {

    data class CalculationResult(
        val servings: Double,
        val weightGrams: Double,
        val nutrition: NutritionSnapshot
    )

    fun calculateForServings(food: Food, servings: Double): CalculationResult {
        require(food.servingWeightGrams > 0.0) { "Serving weight must be greater than zero." }
        require(servings > 0.0) { "Servings must be greater than zero." }

        return CalculationResult(
            servings = servings,
            weightGrams = food.servingWeightGrams * servings,
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

    fun convertToGrams(amount: Double, unit: WeightUnit): Double {
        require(amount >= 0.0) { "Weight cannot be negative." }
        return amount * unit.gramsPerUnit
    }

    fun convertFromGrams(weightGrams: Double, unit: WeightUnit): Double {
        require(weightGrams >= 0.0) { "Weight cannot be negative." }
        return weightGrams / unit.gramsPerUnit
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
            sodiumMilligrams = nutrition.sodiumMilligrams?.times(factor),
            potassiumMilligrams = nutrition.potassiumMilligrams?.times(factor)
        )
    }
}
