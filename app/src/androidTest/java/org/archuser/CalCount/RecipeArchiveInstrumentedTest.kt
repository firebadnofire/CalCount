package org.archuser.CalCount

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.archuser.CalCount.data.RecipeArchive
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.IngredientUnit
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.data.model.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RecipeArchiveInstrumentedTest {
    @Test fun compressesAndDecompressesWithPackagedNativeLibrary() {
        val food = Food(UUID.randomUUID().toString(), "Oats", "1 serving",
            servingWeightGrams = 40.0, nutritionPerServing = NutritionSnapshot(150.0, 3.0, 27.0, 5.0))
        val recipe = Recipe(UUID.randomUUID().toString(), "Breakfast", 2.0,
            listOf(RecipeIngredient(food.id, 80.0, IngredientUnit.GRAMS)))
        val bytes = RecipeArchive.encode(recipe, listOf(food))
        assertTrue(RecipeArchive.hasMagic(bytes))
        assertEquals(RecipeArchive.Bundle(recipe, listOf(food)), RecipeArchive.decode(bytes))
    }
}
