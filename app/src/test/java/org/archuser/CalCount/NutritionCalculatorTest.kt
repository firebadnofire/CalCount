package org.archuser.CalCount

import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.VolumeUnit
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

    private val testLiquid = Food(
        id = "food-2",
        name = "Coke",
        servingDescription = "1 can",
        kind = FoodKind.LIQUID,
        servingWeightGrams = 0.0,
        servingVolumeMilliliters = 355.0,
        nutritionPerServing = NutritionSnapshot(
            calories = 140.0,
            fatGrams = 0.0,
            carbsGrams = 39.0,
            proteinGrams = 0.0,
            sodiumMilligrams = 45.0
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

    @Test
    fun calculateForVolume_convertsBottleVolumeIntoServingFraction() {
        val result = NutritionCalculator.calculateForVolume(testLiquid, volumeMilliliters = 710.0)

        assertEquals(2.0, result.servings, 0.0001)
        assertEquals(710.0, result.volumeMilliliters, 0.0001)
        assertEquals(280.0, result.nutrition.calories, 0.0001)
        assertEquals(78.0, result.nutrition.carbsGrams, 0.0001)
        assertEquals(90.0, result.nutrition.sodiumMilligrams ?: 0.0, 0.0001)
    }

    @Test
    fun convertToMilliliters_supportsFluidOuncesInternally() {
        val milliliters = NutritionCalculator.convertToMilliliters(12.0, VolumeUnit.FLUID_OUNCES)

        assertEquals(354.88235475, milliliters, 0.000000001)
    }
}
