package org.archuser.CalCount.domain

import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.IngredientUnit
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.data.model.RecipeIngredient
import org.archuser.CalCount.data.model.VolumeUnit
import org.archuser.CalCount.data.model.WeightUnit

object RecipeCalculator {
    data class Result(
        val total: NutritionSnapshot,
        val perServing: NutritionSnapshot,
        val completeness: Map<MainMacro, NutrientCompleteness>
    )

    fun calculate(recipe: Recipe, foods: List<Food>): Result {
        require(recipe.name.isNotBlank()) { "Enter a recipe name." }
        require(recipe.servings.isFinite() && recipe.servings > 0) { "Recipe yield must be positive and finite." }
        require(recipe.ingredients.isNotEmpty()) { "Add at least one ingredient." }
        require(recipe.ingredients.size <= 512) { "A recipe can have at most 512 ingredient rows." }

        val foodById = foods.associateBy(Food::id)
        val ingredients = recipe.ingredients.map { ingredient ->
            val food = requireNotNull(foodById[ingredient.foodId]) { "An ingredient is missing from the library." }
            ingredientNutrition(ingredient, food)
        }

        val total = ingredients.fold(NutritionSnapshot.ZERO, NutritionSnapshot::plus)
        val perServing = NutritionCalculator.scaleNutrition(total, 1.0 / recipe.servings)
        val completeness = MainMacro.entries.associateWith { nutrient ->
            require(nutrient.reportedValue(total)?.isFinite() != false &&
                nutrient.reportedValue(perServing)?.isFinite() != false) { "Recipe nutrition exceeds the supported range." }
            when (ingredients.count { nutrient.reportedValue(it) != null }) {
                0 -> NutrientCompleteness.UNKNOWN
                ingredients.size -> NutrientCompleteness.COMPLETE
                else -> NutrientCompleteness.INCOMPLETE
            }
        }
        return Result(total, perServing, completeness)
    }

    fun ingredientNutrition(ingredient: RecipeIngredient, food: Food): NutritionSnapshot {
        val quantity = ingredient.quantity
        require(quantity.isFinite() && quantity > 0) { "Enter a positive quantity for ${food.name}." }
        val result = when (ingredient.unit) {
            IngredientUnit.SERVINGS -> NutritionCalculator.calculateForServings(food, quantity)
            IngredientUnit.GRAMS, IngredientUnit.OUNCES -> {
                require(food.servingWeightGrams.isFinite() && food.servingWeightGrams > 0) {
                    "Add a measured serving weight to ${food.name}’s label to use g or oz."
                }
                val unit = if (ingredient.unit == IngredientUnit.GRAMS) WeightUnit.GRAMS else WeightUnit.OUNCES
                NutritionCalculator.calculateForWeight(food, NutritionCalculator.convertToGrams(quantity, unit))
            }
            IngredientUnit.MILLILITERS, IngredientUnit.LITERS, IngredientUnit.FLUID_OUNCES -> {
                require(food.kind == FoodKind.LIQUID) { "${food.name} is measured by weight. Choose servings, g, or oz." }
                val unit = when (ingredient.unit) {
                    IngredientUnit.MILLILITERS -> VolumeUnit.MILLILITERS
                    IngredientUnit.LITERS -> VolumeUnit.LITERS
                    else -> VolumeUnit.FLUID_OUNCES
                }
                NutritionCalculator.calculateForVolume(food, NutritionCalculator.convertToMilliliters(quantity, unit))
            }
        }
        require(result.servings.isFinite() && result.servings > 0) { "Invalid serving amount for ${food.name}." }
        return result.nutrition
    }
}
