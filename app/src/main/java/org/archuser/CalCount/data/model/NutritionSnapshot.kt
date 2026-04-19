package org.archuser.CalCount.data.model

data class NutritionSnapshot(
    val calories: Double,
    val fatGrams: Double,
    val carbsGrams: Double,
    val proteinGrams: Double,
    val saturatedFatGrams: Double? = null,
    val fiberGrams: Double? = null,
    val sugarGrams: Double? = null,
    val addedSugarsGrams: Double? = null,
    val sugarAlcoholsGrams: Double? = null,
    val sodiumMilligrams: Double? = null,
    val potassiumMilligrams: Double? = null,
    val cholesterolMilligrams: Double? = null,
    val transFatGrams: Double? = null,
    val monounsaturatedFatGrams: Double? = null,
    val polyunsaturatedFatGrams: Double? = null,
    val omega3FattyAcidsGrams: Double? = null,
    val omega6FattyAcidsGrams: Double? = null,
    val calciumMilligrams: Double? = null,
    val chlorideMilligrams: Double? = null,
    val folateMicrograms: Double? = null,
    val ironMilligrams: Double? = null,
    val magnesiumMilligrams: Double? = null,
    val phosphorusMilligrams: Double? = null,
    val vitaminAMicrograms: Double? = null,
    val vitaminB12Micrograms: Double? = null,
    val vitaminCMilligrams: Double? = null,
    val vitaminDMicrograms: Double? = null,
    val vitaminEMilligrams: Double? = null,
    val vitaminKMicrograms: Double? = null,
    val zincMilligrams: Double? = null
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
            addedSugarsGrams = mergeOptional(addedSugarsGrams, other.addedSugarsGrams),
            sugarAlcoholsGrams = mergeOptional(sugarAlcoholsGrams, other.sugarAlcoholsGrams),
            sodiumMilligrams = mergeOptional(sodiumMilligrams, other.sodiumMilligrams),
            potassiumMilligrams = mergeOptional(potassiumMilligrams, other.potassiumMilligrams),
            cholesterolMilligrams = mergeOptional(cholesterolMilligrams, other.cholesterolMilligrams),
            transFatGrams = mergeOptional(transFatGrams, other.transFatGrams),
            monounsaturatedFatGrams = mergeOptional(monounsaturatedFatGrams, other.monounsaturatedFatGrams),
            polyunsaturatedFatGrams = mergeOptional(polyunsaturatedFatGrams, other.polyunsaturatedFatGrams),
            omega3FattyAcidsGrams = mergeOptional(omega3FattyAcidsGrams, other.omega3FattyAcidsGrams),
            omega6FattyAcidsGrams = mergeOptional(omega6FattyAcidsGrams, other.omega6FattyAcidsGrams),
            calciumMilligrams = mergeOptional(calciumMilligrams, other.calciumMilligrams),
            chlorideMilligrams = mergeOptional(chlorideMilligrams, other.chlorideMilligrams),
            folateMicrograms = mergeOptional(folateMicrograms, other.folateMicrograms),
            ironMilligrams = mergeOptional(ironMilligrams, other.ironMilligrams),
            magnesiumMilligrams = mergeOptional(magnesiumMilligrams, other.magnesiumMilligrams),
            phosphorusMilligrams = mergeOptional(phosphorusMilligrams, other.phosphorusMilligrams),
            vitaminAMicrograms = mergeOptional(vitaminAMicrograms, other.vitaminAMicrograms),
            vitaminB12Micrograms = mergeOptional(vitaminB12Micrograms, other.vitaminB12Micrograms),
            vitaminCMilligrams = mergeOptional(vitaminCMilligrams, other.vitaminCMilligrams),
            vitaminDMicrograms = mergeOptional(vitaminDMicrograms, other.vitaminDMicrograms),
            vitaminEMilligrams = mergeOptional(vitaminEMilligrams, other.vitaminEMilligrams),
            vitaminKMicrograms = mergeOptional(vitaminKMicrograms, other.vitaminKMicrograms),
            zincMilligrams = mergeOptional(zincMilligrams, other.zincMilligrams)
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
