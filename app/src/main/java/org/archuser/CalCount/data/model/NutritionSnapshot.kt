package org.archuser.CalCount.data.model

data class NutritionSnapshot(
    val calories: Double,
    val fatGrams: Double,
    val carbsGrams: Double,
    val proteinGrams: Double,
    val saturatedFatGrams: Double? = null,
    val fiberGrams: Double? = null,
    val sugarGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
    val potassiumMilligrams: Double? = null
) {
    operator fun plus(other: NutritionSnapshot): NutritionSnapshot {
        return NutritionSnapshot(
            calories = calories + other.calories,
            fatGrams = fatGrams + other.fatGrams,
            carbsGrams = carbsGrams + other.carbsGrams,
            proteinGrams = proteinGrams + other.proteinGrams,
            saturatedFatGrams = mergeOptional(saturatedFatGrams, other.saturatedFatGrams),
            fiberGrams = mergeOptional(fiberGrams, other.fiberGrams),
            sugarGrams = mergeOptional(sugarGrams, other.sugarGrams),
            sodiumMilligrams = mergeOptional(sodiumMilligrams, other.sodiumMilligrams),
            potassiumMilligrams = mergeOptional(potassiumMilligrams, other.potassiumMilligrams)
        )
    }

    companion object {
        val ZERO = NutritionSnapshot(
            calories = 0.0,
            fatGrams = 0.0,
            carbsGrams = 0.0,
            proteinGrams = 0.0
        )

        private fun mergeOptional(left: Double?, right: Double?): Double? {
            return when {
                left == null && right == null -> null
                else -> (left ?: 0.0) + (right ?: 0.0)
            }
        }
    }
}
