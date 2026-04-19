package org.archuser.CalCount.data.model

data class Food(
    val id: String,
    val name: String,
    val servingDescription: String,
    val kind: FoodKind = FoodKind.FOOD,
    val servingWeightGrams: Double,
    val servingVolumeMilliliters: Double = 0.0,
    val nutritionPerServing: NutritionSnapshot,
    val servingsPerContainer: Double? = null,
    val createdAtEpochMillis: Long = System.currentTimeMillis()
)
