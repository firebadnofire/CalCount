package org.archuser.CalCount.data.model

enum class MealType(val displayName: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    DINNER("Dinner"),
    SNACK("Snack");

    companion object {
        fun fromDisplayName(value: String): MealType? {
            return entries.firstOrNull { it.displayName == value }
        }
    }
}

enum class InputMode(val displayName: String) {
    SERVINGS("Servings"),
    WEIGHT("Weight")
}

enum class WeightUnit(
    val displayName: String,
    val shortLabel: String,
    val gramsPerUnit: Double
) {
    GRAMS(displayName = "Grams", shortLabel = "g", gramsPerUnit = 1.0),
    OUNCES(displayName = "Ounces", shortLabel = "oz", gramsPerUnit = 28.349523125);

    companion object {
        fun fromDisplayName(value: String): WeightUnit? {
            return entries.firstOrNull { it.displayName == value }
        }
    }
}

enum class MainMacro(val displayName: String) {
    CALORIES("Calories"),
    PROTEIN("Protein"),
    CARBS("Carbs"),
    FAT("Fat");

    companion object {
        fun fromDisplayName(value: String): MainMacro? {
            return entries.firstOrNull { it.displayName == value }
        }
    }
}
