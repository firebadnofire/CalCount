package org.archuser.CalCount

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.archuser.CalCount.data.CalorieRepository
import org.archuser.CalCount.data.model.*
import org.archuser.CalCount.domain.RecipeCalculator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LogPersistenceInstrumentedTest {
    @Test fun migratesLegacyFoodLogAndPreservesHistoricalRecipeNutrition() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val directory = File(app.cacheDir, id).apply { mkdirs() }
        val context = object : ContextWrapper(app) {
            override fun getFilesDir(): File = directory
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(id, mode)
        }
        try {
            val legacyLog = JSONObject()
                .put("id", id).put("foodId", id).put("foodName", "Legacy oats").put("servingDescription", "1 serving")
                .put("mealType", "BREAKFAST").put("inputMode", "SERVINGS")
                .put("consumedServings", 1.0).put("consumedWeightGrams", 40.0)
                .put("calculatedNutrition", JSONObject().put("calories", 150).put("fatGrams", 3)
                    .put("carbsGrams", 27).put("proteinGrams", 5))
            val legacy = JSONObject().put("logs", JSONArray().put(legacyLog)).toString()
            assertTrue(context.getSharedPreferences("cal_count", Context.MODE_PRIVATE).edit().putString("app_state", legacy).commit())
            val repository = CalorieRepository(context)
            val state = repository.loadState().state
            val original = state.logs.single()
            assertEquals(LogSourceType.FOOD, original.sourceType)
            assertNull(original.nutritionCompleteness)

            val nutrition = original.calculatedNutrition.copy(sodiumMilligrams = 0.0)
            val food = Food(UUID.randomUUID().toString(), "Oats", "1 serving", servingWeightGrams = 40.0,
                nutritionPerServing = nutrition)
            val unreported = food.copy(id = UUID.randomUUID().toString(), name = "Fruit",
                nutritionPerServing = nutrition.copy(sodiumMilligrams = null))
            val recipe = Recipe(UUID.randomUUID().toString(), "Breakfast", 2.0,
                listOf(RecipeIngredient(food.id, 2.0, IngredientUnit.SERVINGS),
                    RecipeIngredient(unreported.id, 1.0, IngredientUnit.SERVINGS)))
            val calculated = RecipeCalculator.calculate(recipe, listOf(food, unreported))
            val recipeLog = original.copy(id = UUID.randomUUID().toString(), foodId = recipe.id, foodName = recipe.name,
                sourceType = LogSourceType.RECIPE, calculatedNutrition = calculated.perServing,
                nutritionCompleteness = calculated.completeness)
            val expanded = state.copy(foods = listOf(food, unreported), recipes = listOf(recipe), logs = listOf(original, recipeLog))
            assertEquals(CalorieRepository.WriteResult.Success, repository.saveState(expanded))
            assertEquals(expanded, CalorieRepository(context).loadState().state)
            val changed = expanded.copy(foods = listOf(food.copy(nutritionPerServing = nutrition.copy(sodiumMilligrams = null))),
                recipes = emptyList())
            assertEquals(CalorieRepository.WriteResult.Success, repository.saveState(changed))
            val restored = CalorieRepository(context).loadState().state
            assertEquals(listOf(original, recipeLog), restored.logs)
            assertEquals(NutrientCompleteness.INCOMPLETE, restored.logs.last().nutritionCompleteness?.get(MainMacro.SODIUM))
            assertEquals(0.0, restored.logs.last().calculatedNutrition.sodiumMilligrams)
            assertEquals(NutrientCompleteness.UNKNOWN, restored.logs.last().nutritionCompleteness?.get(MainMacro.POTASSIUM))
            assertEquals(NutrientCompleteness.COMPLETE, restored.logs.last().nutritionCompleteness?.get(MainMacro.CALORIES))
        } finally {
            app.deleteSharedPreferences(id)
            directory.deleteRecursively()
        }
    }
}
