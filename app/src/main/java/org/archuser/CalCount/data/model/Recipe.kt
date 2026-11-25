package org.archuser.CalCount.data.model

data class Recipe(
    val id: String,
    val name: String,
    val servings: Double,
    val ingredients: List<RecipeIngredient>,
    val description: String? = null,
    val notes: String? = null
)

data class RecipeIngredient(
    val foodId: String,
    val quantity: Double,
    val unit: IngredientUnit,
    val notes: String? = null
)

enum class IngredientUnit(val label: String) {
    SERVINGS("servings"),
    GRAMS("g"),
    OUNCES("oz"),
    MILLILITERS("mL"),
    LITERS("L"),
    FLUID_OUNCES("fl_oz");

    companion object {
        fun availableFor(food: Food?): List<IngredientUnit> = buildList {
            add(SERVINGS)
            if (food?.servingWeightGrams?.let { it.isFinite() && it > 0 } == true) {
                add(GRAMS)
                add(OUNCES)
            }
            if (food?.kind == FoodKind.LIQUID) {
                add(MILLILITERS)
                add(LITERS)
                add(FLUID_OUNCES)
            }
        }

        fun fromLabel(label: String): IngredientUnit =
            entries.firstOrNull { it.label == label } ?: error("Unsupported ingredient unit: $label")
    }
}
