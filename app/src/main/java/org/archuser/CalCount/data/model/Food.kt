package org.archuser.CalCount.data.model

data class Food(
    val id: String,
    val name: String,
    val servingDescription: String,
    val servingWeightGrams: Double,
    val nutritionPerServing: NutritionSnapshot,
    val servingsPerContainer: Double? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
