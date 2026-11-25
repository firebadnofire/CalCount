package org.archuser.CalCount.data

import org.archuser.CalCount.data.model.IngredientUnit
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.data.model.RecipeIngredient
import org.json.JSONArray
import org.json.JSONObject

internal object RecipeJson {
    fun encode(recipe: Recipe): JSONObject = JSONObject()
        .put("id", recipe.id)
        .put("name", recipe.name)
        .put("description", recipe.description)
        .put("notes", recipe.notes)
        .put("servings", recipe.servings)
        .put("ingredients", JSONArray().apply {
            recipe.ingredients.forEach { ingredient ->
                put(JSONObject()
                    .put("label_id", ingredient.foodId)
                    .put("quantity", ingredient.quantity)
                    .put("unit", ingredient.unit.label)
                    .put("notes", ingredient.notes))
            }
        })

    fun decode(json: JSONObject): Recipe {
        val ingredients = json.getJSONArray("ingredients")
        require(ingredients.length() in 1..512) { "A recipe needs 1–512 ingredient rows." }
        return Recipe(
            id = json.string("id"),
            name = json.string("name"),
            description = json.optionalText("description"),
            notes = json.optionalText("notes"),
            servings = json.number("servings"),
            ingredients = List(ingredients.length()) { index ->
                val ingredient = ingredients.getJSONObject(index)
                RecipeIngredient(
                    foodId = ingredient.string("label_id"),
                    quantity = ingredient.number("quantity"),
                    unit = IngredientUnit.fromLabel(ingredient.string("unit")),
                    notes = ingredient.optionalText("notes")
                )
            }
        )
    }

    private fun JSONObject.string(key: String): String =
        (get(key) as? String)?.takeIf(String::isNotBlank) ?: error("Invalid recipe field: $key")

    private fun JSONObject.optionalText(key: String): String? =
        if (isNull(key)) null else get(key) as? String ?: error("Invalid recipe field: $key")

    private fun JSONObject.number(key: String): Double =
        (get(key) as? Number)?.toDouble()?.takeIf { it.isFinite() && it > 0 }
            ?: error("Recipe field $key must be a positive number.")
}
