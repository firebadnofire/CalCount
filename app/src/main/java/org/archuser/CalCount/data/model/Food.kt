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
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val catalog: CatalogMetadata? = null
)

data class CatalogMetadata(
    val catalogId: String,
    val revision: Int,
    val publisher: String,
    val sourceName: String,
    val sourceUrl: String? = null,
    val license: String? = null,
    val regionCode: String? = null,
    val notes: String? = null,
    val verifiedAtEpochMillis: Long? = null
)
