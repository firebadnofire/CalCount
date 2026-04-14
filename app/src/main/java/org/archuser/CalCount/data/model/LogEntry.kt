package org.archuser.CalCount.data.model

data class LogEntry(
    val id: String,
    val foodId: String,
    val foodName: String,
    val servingDescription: String,
    val mealType: MealType,
    val inputMode: InputMode,
    val consumedServings: Double,
    val consumedWeightGrams: Double,
    val calculatedNutrition: NutritionSnapshot,
    val loggedAtEpochMillis: Long = System.currentTimeMillis()
)
