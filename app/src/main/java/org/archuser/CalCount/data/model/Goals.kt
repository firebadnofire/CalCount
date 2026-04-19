package org.archuser.CalCount.data.model

data class Goals(
    val dailyCalories: Double = 2000.0,
    val fatTargetGrams: Double? = null,
    val carbsTargetGrams: Double? = null,
    val proteinTargetGrams: Double? = null,
    val saturatedFatTargetGrams: Double? = null,
    val fiberTargetGrams: Double? = null,
    val sugarsTargetGrams: Double? = null,
    val sodiumTargetMilligrams: Double? = null,
    val potassiumTargetMilligrams: Double? = null,
    val preferredUnit: WeightUnit = WeightUnit.GRAMS,
    val preferredLiquidUnit: VolumeUnit = VolumeUnit.MILLILITERS,
    val useMaterialYou: Boolean = true,
    val mainMacro: MainMacro = MainMacro.CALORIES,
    val showCaloriesInLivePreview: Boolean = false,
    val macroSummarySelection: Set<MainMacro> = setOf(
        MainMacro.PROTEIN,
        MainMacro.CARBS,
        MainMacro.FAT
    )
)
