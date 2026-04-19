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
    val loggedAtEpochMillis: Long = System.currentTimeMillis()
)
