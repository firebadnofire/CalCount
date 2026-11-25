package org.archuser.CalCount.data.model

data class LogEntry(
    val id: String,
    val foodId: String,
    val foodName: String,
    val servingDescription: String,
    val mealType: MealType,
    val foodKind: FoodKind = FoodKind.FOOD,
    val inputMode: InputMode,
    val consumedServings: Double,
    val consumedWeightGrams: Double,
    val consumedVolumeMilliliters: Double = 0.0,
    val calculatedNutrition: NutritionSnapshot,
    val loggedAtEpochMillis: Long = System.currentTimeMillis(),
    val sourceType: LogSourceType = LogSourceType.FOOD,
    val nutritionCompleteness: Map<MainMacro, NutrientCompleteness>? = null
)

enum class LogSourceType(val wireName: String) {
    FOOD("food"),
    RECIPE("recipe");

    companion object {
        fun fromWireName(value: String): LogSourceType =
            entries.firstOrNull { it.wireName == value } ?: error("Unknown log source type: $value")
    }
}

enum class NutrientCompleteness {
    COMPLETE,
    INCOMPLETE,
    UNKNOWN
}
