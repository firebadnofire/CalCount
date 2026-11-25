package org.archuser.CalCount

import com.github.luben.zstd.Zstd
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.archuser.CalCount.data.Carousel
import org.archuser.CalCount.data.ImportDraft
import org.archuser.CalCount.data.IncomingTransfer
import org.archuser.CalCount.data.LabelCodec
import org.archuser.CalCount.data.RecipeArchive
import org.archuser.CalCount.data.model.AppState
import org.archuser.CalCount.data.model.CatalogMetadata
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.IngredientUnit
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.data.model.RecipeIngredient
import org.archuser.CalCount.domain.RecipeCalculator
import org.archuser.CalCount.ui.UiFormatters
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class RecipeTransferTest {
    private val first = Food(
        id = "10000000-0000-4000-8000-000000000001", name = "Oats", servingDescription = "1 serving",
        servingWeightGrams = 40.0,
        nutritionPerServing = NutritionSnapshot(150.0, 3.0, 27.0, 5.0, 1.0, 4.0, 2.0,
            0.0, 0.0, 30.0, 0.0, 2.0, 0.0, 1.0, 2.0, 0.1, 0.2,
            10.0, 3.0, 1.0, 2.0, 25.0, 2.0, 1.0, 0.5, 1.0, 2.0, 0.8, 0.6),
        catalog = CatalogMetadata("example.oats", 2, "Example", "Package")
    )
    private val second = first.copy(
        id = "10000000-0000-4000-8000-000000000002", name = "Rice",
        nutritionPerServing = NutritionSnapshot(100.0, 1.0, 24.0, 2.0, potassiumMilligrams = 0.0)
    )
    private val recipe = Recipe(
        "10000000-0000-4000-8000-000000000003", "Breakfast", 2.0,
        listOf(RecipeIngredient(first.id, 40.0, IngredientUnit.GRAMS),
            RecipeIngredient(second.id, 1.0, IngredientUnit.SERVINGS))
    )

    @Test fun labelRoundTripPreservesAllNutrientsAndLegacyJson() {
        val encoded = LabelCodec.encode(first)
        assertEquals(first, LabelCodec.decode(encoded))
        val canonical = JSONObject(encoded.decodeToString())
        val food = canonical.getJSONObject("food")
        assertEquals(first.copy(catalog = null), LabelCodec.decode(food.toString().encodeToByteArray()))
        val legacy = JSONObject().put("type", "food").put("version", 1).put("food", food)
        assertEquals(first.copy(catalog = null), LabelCodec.decode(legacy.toString().encodeToByteArray()))
        canonical.put("schema_version", 2)
        assertThrows(IllegalArgumentException::class.java) { LabelCodec.decode(canonical.toString().encodeToByteArray()) }
        assertThrows(Exception::class.java) { LabelCodec.decode("{\"id\":1,\"id\":2}".encodeToByteArray()) }
        food.getJSONObject("nutritionPerServing").put("sodiumMilligrams", -1)
        assertThrows(IllegalArgumentException::class.java) { LabelCodec.decode(food.toString().encodeToByteArray()) }
    }

    @Test fun liquidRecipesUseMeasuredServingWeightWithoutAssumingDensity() {
        val liquid = first.copy(
            kind = org.archuser.CalCount.data.model.FoodKind.LIQUID,
            servingWeightGrams = 0.0,
            servingVolumeMilliliters = 355.0
        )
        val byWeight = RecipeIngredient(liquid.id, 185.0, IngredientUnit.GRAMS)
        assertFalse(IngredientUnit.GRAMS in IngredientUnit.availableFor(liquid))
        assertThrows(IllegalArgumentException::class.java) {
            RecipeCalculator.ingredientNutrition(byWeight, liquid)
        }
        val measured = LabelCodec.decode(LabelCodec.encode(liquid.copy(servingWeightGrams = 370.0)))
        assertTrue(IngredientUnit.GRAMS in IngredientUnit.availableFor(measured))
        assertTrue(IngredientUnit.OUNCES in IngredientUnit.availableFor(measured))
        assertTrue(IngredientUnit.FLUID_OUNCES in IngredientUnit.availableFor(measured))
        assertEquals(measured.nutritionPerServing.calories / 2,
            RecipeCalculator.ingredientNutrition(byWeight, measured).calories, 0.0)
    }

    @Test fun archiveRoundTripAndCompleteness() {
        val foods = listOf(first, second)
        val bundled = recipe.copy(ingredients = recipe.ingredients + recipe.ingredients.first())
        val bytes = RecipeArchive.encode(bundled, foods)
        assertEquals(RecipeArchive.Bundle(bundled, foods), RecipeArchive.decode(bytes))
        val result = RecipeCalculator.calculate(recipe, foods)
        assertEquals(250.0, result.total.calories, 0.0)
        assertEquals(125.0, result.perServing.calories, 0.0)
        assertEquals(15.0, result.perServing.sodiumMilligrams)
        assertEquals(NutrientCompleteness.INCOMPLETE, result.completeness[MainMacro.SODIUM])
        assertEquals("Sodium 15mg (incomplete)", UiFormatters.mainMacroLabeledValue(
            result.perServing, MainMacro.SODIUM, result.completeness[MainMacro.SODIUM]
        ))
        assertEquals(0.0, result.perServing.potassiumMilligrams)
        assertEquals(NutrientCompleteness.COMPLETE, result.completeness[MainMacro.POTASSIUM])
        val unknown = RecipeCalculator.calculate(recipe, foods.map {
            it.copy(nutritionPerServing = it.nutritionPerServing.copy(sodiumMilligrams = null))
        })
        assertNull(unknown.total.sodiumMilligrams)
        assertEquals(NutrientCompleteness.UNKNOWN, unknown.completeness[MainMacro.SODIUM])
        assertThrows(IllegalArgumentException::class.java) { RecipeCalculator.calculate(recipe, listOf(first)) }
        assertThrows(IllegalArgumentException::class.java) { RecipeCalculator.calculate(recipe.copy(servings = 0.0), foods) }
    }

    @Test fun rejectsTraversalAndExcessiveExpansion() {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry("labels/../manifest.json"))
            tar.closeArchiveEntry()
        }
        assertThrows(IllegalArgumentException::class.java) { RecipeArchive.decode(Zstd.compress(bytes.toByteArray())) }
        assertThrows(Exception::class.java) { RecipeArchive.decode(Zstd.compress(ByteArray(8 * 1024 * 1024 + 1))) }
    }

    @Test fun stagesIngredientAndRecipeConflictsWithoutWritingOriginal() {
        val local = first.copy(name = "Local oats", nutritionPerServing = first.nutritionPerServing.copy(sodiumMilligrams = null))
        val original = AppState(foods = listOf(local, second), recipes = listOf(recipe))
        for (choice in ImportDraft.Choice.entries) {
            val incoming = RecipeArchive.decode(RecipeArchive.encode(recipe.copy(name = "New breakfast"), listOf(first, second)))
            val draft = ImportDraft(original, IncomingTransfer(incoming.foods, incoming.recipe))
            assertTrue(draft.conflict is ImportDraft.Conflict.Label)
            draft.resolve(choice)
            assertTrue(draft.conflict is ImportDraft.Conflict.RecipeDefinition)
            draft.resolve(ImportDraft.Choice.IMPORTED)
            val result = draft.finish(original)
            assertEquals("Local oats", original.foods.first().name)
            val received = result.recipes.single()
            assertEquals("New breakfast", received.name)
            assertEquals(if (choice == ImportDraft.Choice.BOTH) 3 else 2, result.foods.size)
            val assembled = RecipeCalculator.calculate(received, result.foods)
            assertEquals(if (choice == ImportDraft.Choice.LOCAL) NutrientCompleteness.UNKNOWN
                else NutrientCompleteness.INCOMPLETE, assembled.completeness[MainMacro.SODIUM])
        }
        assertNull(ImportDraft(AppState(foods = listOf(first)), IncomingTransfer(listOf(first))).conflict)
    }

    @Test fun documentedBundleImportsThroughCarouselAndLogsCalculatedServing() {
        val examples = java.io.File("../docs/examples")
        val manifest = java.io.File(examples, "manifest.json").readBytes()
        val label = java.io.File(examples, "oats.ccnl").readBytes()
        val id = LabelCodec.decode(label).id
        val buffer = ByteArrayOutputStream()
        TarArchiveOutputStream(buffer).use { tar ->
            for ((path, contents) in listOf("manifest.json" to manifest, "labels/$id.ccnl" to label)) {
                val file = TarArchiveEntry(path)
                file.size = contents.size.toLong()
                tar.putArchiveEntry(file)
                tar.write(contents)
                tar.closeArchiveEntry()
            }
        }
        val packets = Carousel.encode("ccr", Zstd.compress(buffer.toByteArray()))
        val receiver = Carousel.Receiver()
        val received = packets.reversed().mapNotNull(receiver::accept).single()
        val incoming = IncomingTransfer.decode(received.bytes, received.type)
        val empty = AppState()
        val stored = ImportDraft(empty, incoming).finish(empty)
        val recipe = stored.recipes.single()
        val calculated = RecipeCalculator.calculate(recipe, stored.foods)
        assertEquals(150.0, calculated.perServing.calories, 0.0)
        assertEquals(NutrientCompleteness.COMPLETE, calculated.completeness[MainMacro.SODIUM])
        assertEquals(NutrientCompleteness.UNKNOWN, calculated.completeness[MainMacro.POTASSIUM])
    }
}
