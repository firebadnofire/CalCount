package org.archuser.CalCount.data

import org.archuser.CalCount.data.LabelCodec.identityContent
import org.archuser.CalCount.data.model.AppState
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.domain.RecipeCalculator
import java.util.UUID

internal data class IncomingTransfer(val foods: List<Food>, val recipe: Recipe? = null) {
    companion object {
        fun decode(bytes: ByteArray, declaredType: String? = null): IncomingTransfer {
            val archive = RecipeArchive.hasMagic(bytes)
            require(declaredType == null || declaredType == if (archive) "ccr" else "ccnl") {
                "The transfer type does not match its contents."
            }
            return if (archive) {
                val bundle = RecipeArchive.decode(bytes)
                IncomingTransfer(bundle.foods, bundle.recipe)
            } else {
                IncomingTransfer(listOf(LabelCodec.decode(bytes)))
            }
        }
    }
}

internal class ImportDraft(val original: AppState, val incoming: IncomingTransfer) {
    enum class Choice(val label: String) {
        LOCAL("Use Local"), IMPORTED("Use Imported"), BOTH("Keep Both")
    }

    sealed interface Conflict {
        data class Label(val local: Food, val imported: Food) : Conflict
        data class RecipeDefinition(val local: Recipe, val imported: Recipe) : Conflict
    }

    private val foods = original.foods.toMutableList()
    private val recipes = original.recipes.toMutableList()
    private val resolvedIds = mutableMapOf<String, String>()
    private var cursor = 0
    private var recipeResolved = false
    private val summaryLines = mutableListOf<String>()
    val summary: List<String> get() = summaryLines.toList()
    val proposedFoods: List<Food> get() = foods.toList()
    val incomingRecipe: Recipe? get() = incoming.recipe?.let(::remap)
    var conflict: Conflict? = null
        private set

    init {
        advance()
    }

    fun resolve(choice: Choice) {
        when (val current = checkNotNull(conflict)) {
            is Conflict.Label -> {
                val local = current.local
                val imported = current.imported
                val resolved = when (choice) {
                    Choice.LOCAL -> local
                    Choice.IMPORTED -> imported
                    Choice.BOTH -> imported.copy(id = UUID.randomUUID().toString())
                }
                incoming.recipe?.let { recipe ->
                    val mapped = remap(recipe)
                    val mappedRecipe = mapped.copy(ingredients = mapped.ingredients.map {
                        if (it.foodId == imported.id) it.copy(foodId = resolved.id) else it
                    })
                    val previewFoods = (foods + incoming.foods.drop(cursor + 1) + resolved).associateBy(Food::id)
                    validate(mappedRecipe, previewFoods.values.toList())
                }
                if (choice == Choice.IMPORTED) {
                    val updatedFoods = foods.map { if (it.id == local.id) resolved else it }
                    recipes.filter { recipe -> recipe.ingredients.any { it.foodId == local.id } }
                        .forEach { recipe -> validate(recipe, updatedFoods) }
                    val affected = recipes.filter { recipe -> recipe.ingredients.any { it.foodId == local.id } }
                    if (affected.isNotEmpty()) summaryLines += "Linked recipes updated: ${affected.joinToString { it.name }}"
                    foods[foods.indexOf(local)] = resolved
                }
                if (choice == Choice.BOTH) foods.add(resolved)
                resolvedIds[imported.id] = resolved.id
                summaryLines += "${choice.label}: ${resolved.name}"
                cursor++
            }
            is Conflict.RecipeDefinition -> {
                val chosen = when (choice) {
                    Choice.LOCAL -> current.local
                    Choice.IMPORTED -> current.imported
                    Choice.BOTH -> current.imported.copy(id = UUID.randomUUID().toString())
                }
                validate(chosen, foods)
                when (choice) {
                    Choice.LOCAL -> Unit
                    Choice.IMPORTED -> recipes[recipes.indexOf(current.local)] = chosen
                    Choice.BOTH -> recipes.add(chosen)
                }
                summaryLines += "${choice.label}: ${chosen.name}"
                recipeResolved = true
            }
        }
        conflict = null
        advance()
    }

    fun finish(current: AppState): AppState {
        check(conflict == null && recipeResolved) { "Finish resolving import conflicts first." }
        check(current.foods == original.foods && current.recipes == original.recipes) { "The library changed during import." }
        return current.copy(foods = foods.toList(), recipes = recipes.toList())
    }

    private fun advance() {
        while (cursor < incoming.foods.size) {
            val imported = incoming.foods[cursor]
            val local = foods.firstOrNull { it.id == imported.id }
            if (local != null && local.identityContent() != imported.identityContent()) {
                conflict = Conflict.Label(local, imported)
                return
            }
            if (local == null) foods.add(imported)
            summaryLines += if (local == null) "Add label: ${imported.name}" else "Reuse label: ${imported.name}"
            resolvedIds[imported.id] = imported.id
            cursor++
        }
        if (recipeResolved) return
        val recipe = incomingRecipe
        val local = recipes.firstOrNull { it.id == recipe?.id }
        if (recipe != null) {
            if (local != null && local != recipe) {
                conflict = Conflict.RecipeDefinition(local, recipe)
                return
            }
            validate(recipe, foods)
            if (local == null) recipes.add(recipe)
            summaryLines += if (local == null) "Add recipe: ${recipe.name}" else "Reuse recipe: ${recipe.name}"
        }
        recipeResolved = true
    }

    private fun remap(recipe: Recipe) = recipe.copy(
        ingredients = recipe.ingredients.map { it.copy(foodId = resolvedIds[it.foodId] ?: it.foodId) }
    )

    private fun validate(recipe: Recipe, foods: List<Food>) {
        try {
            RecipeCalculator.calculate(recipe, foods)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("${recipe.name}: ${error.message}", error)
        }
    }
}
