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

enum class MacroUnit(val suffix: String) {
    CALORIES(" cal"),
    GRAMS("g"),
    MILLIGRAMS("mg")
}

enum class MainMacro(
    val displayName: String,
    val shortLabel: String,
    val unit: MacroUnit
) {
    CALORIES(displayName = "Calories", shortLabel = "Calories", unit = MacroUnit.CALORIES),
    PROTEIN(displayName = "Protein", shortLabel = "Protein", unit = MacroUnit.GRAMS),
    CARBS(displayName = "Carbs", shortLabel = "Carbs", unit = MacroUnit.GRAMS),
    FAT(displayName = "Fat", shortLabel = "Fat", unit = MacroUnit.GRAMS),
    SATURATED_FAT(displayName = "Saturated fat", shortLabel = "Sat fat", unit = MacroUnit.GRAMS),
    FIBER(displayName = "Fiber", shortLabel = "Fiber", unit = MacroUnit.GRAMS),
    SUGARS(displayName = "Sugars", shortLabel = "Sugars", unit = MacroUnit.GRAMS),
    SODIUM(displayName = "Sodium", shortLabel = "Sodium", unit = MacroUnit.MILLIGRAMS),
    POTASSIUM(displayName = "Potassium", shortLabel = "Potassium", unit = MacroUnit.MILLIGRAMS);

    fun valueOf(nutritionSnapshot: NutritionSnapshot): Double {
        return when (this) {
            CALORIES -> nutritionSnapshot.calories
            PROTEIN -> nutritionSnapshot.proteinGrams
            CARBS -> nutritionSnapshot.carbsGrams
            FAT -> nutritionSnapshot.fatGrams
            SATURATED_FAT -> nutritionSnapshot.saturatedFatGrams ?: 0.0
            FIBER -> nutritionSnapshot.fiberGrams ?: 0.0
            SUGARS -> nutritionSnapshot.sugarGrams ?: 0.0
            SODIUM -> nutritionSnapshot.sodiumMilligrams ?: 0.0
            POTASSIUM -> nutritionSnapshot.potassiumMilligrams ?: 0.0
        }
    }

    fun targetOf(goals: Goals): Double? {
        return when (this) {
            CALORIES -> goals.dailyCalories
            PROTEIN -> goals.proteinTargetGrams
            CARBS -> goals.carbsTargetGrams
            FAT -> goals.fatTargetGrams
            SATURATED_FAT -> goals.saturatedFatTargetGrams
            FIBER -> goals.fiberTargetGrams
            SUGARS -> goals.sugarsTargetGrams
            SODIUM -> goals.sodiumTargetMilligrams
            POTASSIUM -> goals.potassiumTargetMilligrams
        }
    }

    fun isSelectedInMacroSummary(goals: Goals): Boolean {
        return when (this) {
            CALORIES -> false
            PROTEIN -> goals.showProteinInLivePreview
            CARBS -> goals.showCarbsInLivePreview
            FAT -> goals.showFatInLivePreview
            SATURATED_FAT -> goals.showSaturatedFatInLivePreview
            FIBER -> goals.showFiberInLivePreview
            SUGARS -> goals.showSugarsInLivePreview
            SODIUM -> goals.showSodiumInLivePreview
            POTASSIUM -> goals.showPotassiumInLivePreview
        }
    }

    companion object {
        fun fromDisplayName(value: String): MainMacro? {
            return entries.firstOrNull { it.displayName == value }
        }
    }
}
