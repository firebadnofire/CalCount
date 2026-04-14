package org.archuser.CalCount

import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.WeightUnit
import org.archuser.CalCount.domain.NutritionCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class NutritionCalculatorTest {

    private val testFood = Food(
        id = "food-1",
        name = "Protein Bar",
        servingDescription = "1 bar",
        servingWeightGrams = 55.0,
        nutritionPerServing = NutritionSnapshot(
            calories = 220.0,
            fatGrams = 8.0,
            carbsGrams = 24.0,
            proteinGrams = 20.0,
            potassiumMilligrams = 180.0
        )
    )

    @Test
    fun calculateForServings_scalesNutritionCorrectly() {
        val result = NutritionCalculator.calculateForServings(testFood, servings = 1.5)

        assertEquals(1.5, result.servings, 0.0001)
        assertEquals(82.5, result.weightGrams, 0.0001)
        assertEquals(330.0, result.nutrition.calories, 0.0001)
        assertEquals(12.0, result.nutrition.fatGrams, 0.0001)
        assertEquals(36.0, result.nutrition.carbsGrams, 0.0001)
        assertEquals(30.0, result.nutrition.proteinGrams, 0.0001)
        assertEquals(270.0, result.nutrition.potassiumMilligrams ?: 0.0, 0.0001)
    }

    @Test
    fun calculateForWeight_convertsWeightIntoServingFraction() {
        val result = NutritionCalculator.calculateForWeight(testFood, weightGrams = 27.5)

        assertEquals(0.5, result.servings, 0.0001)
        assertEquals(27.5, result.weightGrams, 0.0001)
        assertEquals(110.0, result.nutrition.calories, 0.0001)
        assertEquals(4.0, result.nutrition.fatGrams, 0.0001)
        assertEquals(12.0, result.nutrition.carbsGrams, 0.0001)
        assertEquals(10.0, result.nutrition.proteinGrams, 0.0001)
        assertEquals(90.0, result.nutrition.potassiumMilligrams ?: 0.0, 0.0001)
    }

    @Test
    fun convertToGrams_supportsOuncesInternally() {
        val grams = NutritionCalculator.convertToGrams(1.0, WeightUnit.OUNCES)

        assertEquals(28.349523125, grams, 0.000000001)
    }
}
