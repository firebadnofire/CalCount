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
    WEIGHT("Weight"),
    VOLUME("Volume")
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

enum class VolumeUnit(
    val displayName: String,
    val shortLabel: String,
    val millilitersPerUnit: Double
) {
    MILLILITERS(displayName = "Milliliters", shortLabel = "ml", millilitersPerUnit = 1.0),
    LITERS(displayName = "Liters", shortLabel = "L", millilitersPerUnit = 1000.0),
    FLUID_OUNCES(displayName = "Fluid ounces", shortLabel = "fl oz", millilitersPerUnit = 29.5735295625);

    companion object {
        fun fromDisplayName(value: String): VolumeUnit? {
            return entries.firstOrNull { it.displayName == value }
        }
    }
}

enum class FoodKind(val displayName: String) {
    FOOD("Food"),
    LIQUID("Liquid");

    companion object {
        fun fromDisplayName(value: String): FoodKind? {
            return entries.firstOrNull { it.displayName == value }
        }
    }
}

enum class MacroUnit(val suffix: String) {
    CALORIES(" cal"),
    GRAMS("g"),
    MILLIGRAMS("mg"),
    MICROGRAMS("mcg")
}

enum class MacroTier(val displayName: String, val isCollapsible: Boolean) {
    MAIN(displayName = "Main nutrients", isCollapsible = false),
    USEFUL(displayName = "Useful nutrients", isCollapsible = true),
    EXTENDED(displayName = "Extended nutrients", isCollapsible = true)
}

enum class MainMacro(
    val displayName: String,
    val shortLabel: String,
    val unit: MacroUnit,
    val tier: MacroTier
) {
    CALORIES(displayName = "Calories", shortLabel = "Calories", unit = MacroUnit.CALORIES, tier = MacroTier.MAIN),
    CARBS(displayName = "Carbs", shortLabel = "Carbs", unit = MacroUnit.GRAMS, tier = MacroTier.MAIN),
    FAT(displayName = "Fat", shortLabel = "Fat", unit = MacroUnit.GRAMS, tier = MacroTier.MAIN),
    PROTEIN(displayName = "Protein", shortLabel = "Protein", unit = MacroUnit.GRAMS, tier = MacroTier.MAIN),
    FIBER(displayName = "Fiber", shortLabel = "Fiber", unit = MacroUnit.GRAMS, tier = MacroTier.MAIN),
    SUGARS(displayName = "Sugars", shortLabel = "Sugars", unit = MacroUnit.GRAMS, tier = MacroTier.MAIN),
    SODIUM(displayName = "Sodium", shortLabel = "Sodium", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.MAIN),

    ADDED_SUGARS(displayName = "Added sugars", shortLabel = "Added sugars", unit = MacroUnit.GRAMS, tier = MacroTier.USEFUL),
    SATURATED_FAT(displayName = "Saturated fat", shortLabel = "Sat fat", unit = MacroUnit.GRAMS, tier = MacroTier.USEFUL),
    CHOLESTEROL(displayName = "Cholesterol", shortLabel = "Cholesterol", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.USEFUL),
    POTASSIUM(displayName = "Potassium", shortLabel = "Potassium", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.USEFUL),

    CALCIUM(displayName = "Calcium", shortLabel = "Calcium", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.EXTENDED),
    CHLORIDE(displayName = "Chloride", shortLabel = "Chloride", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.EXTENDED),
    FOLATE(displayName = "Folate (B9)", shortLabel = "Folate", unit = MacroUnit.MICROGRAMS, tier = MacroTier.EXTENDED),
    IRON(displayName = "Iron", shortLabel = "Iron", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.EXTENDED),
    MAGNESIUM(displayName = "Magnesium", shortLabel = "Magnesium", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.EXTENDED),
    MONOUNSATURATED_FAT(displayName = "Monounsaturated fat", shortLabel = "Mono fat", unit = MacroUnit.GRAMS, tier = MacroTier.EXTENDED),
    OMEGA_3(displayName = "Omega-3", shortLabel = "Omega-3", unit = MacroUnit.GRAMS, tier = MacroTier.EXTENDED),
    OMEGA_6(displayName = "Omega-6", shortLabel = "Omega-6", unit = MacroUnit.GRAMS, tier = MacroTier.EXTENDED),
    PHOSPHORUS(displayName = "Phosphorus", shortLabel = "Phosphorus", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.EXTENDED),
    POLYUNSATURATED_FAT(displayName = "Polyunsaturated fat", shortLabel = "Poly fat", unit = MacroUnit.GRAMS, tier = MacroTier.EXTENDED),
    SUGAR_ALCOHOLS(displayName = "Sugar alcohols", shortLabel = "Sugar alc", unit = MacroUnit.GRAMS, tier = MacroTier.EXTENDED),
    TRANS_FAT(displayName = "Trans fat", shortLabel = "Trans fat", unit = MacroUnit.GRAMS, tier = MacroTier.EXTENDED),
    VITAMIN_A(displayName = "Vitamin A", shortLabel = "Vit A", unit = MacroUnit.MICROGRAMS, tier = MacroTier.EXTENDED),
    VITAMIN_B12(displayName = "Vitamin B12", shortLabel = "B12", unit = MacroUnit.MICROGRAMS, tier = MacroTier.EXTENDED),
    VITAMIN_C(displayName = "Vitamin C", shortLabel = "Vit C", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.EXTENDED),
    VITAMIN_D(displayName = "Vitamin D", shortLabel = "Vit D", unit = MacroUnit.MICROGRAMS, tier = MacroTier.EXTENDED),
    VITAMIN_E(displayName = "Vitamin E", shortLabel = "Vit E", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.EXTENDED),
    VITAMIN_K(displayName = "Vitamin K", shortLabel = "Vit K", unit = MacroUnit.MICROGRAMS, tier = MacroTier.EXTENDED),
    ZINC(displayName = "Zinc", shortLabel = "Zinc", unit = MacroUnit.MILLIGRAMS, tier = MacroTier.EXTENDED);

    fun valueOf(nutritionSnapshot: NutritionSnapshot): Double = reportedValue(nutritionSnapshot) ?: 0.0

    fun reportedValue(nutritionSnapshot: NutritionSnapshot): Double? {
        return when (this) {
            CALORIES -> nutritionSnapshot.calories
            CARBS -> nutritionSnapshot.carbsGrams
            FAT -> nutritionSnapshot.fatGrams
            PROTEIN -> nutritionSnapshot.proteinGrams
            FIBER -> nutritionSnapshot.fiberGrams
            SUGARS -> nutritionSnapshot.sugarGrams
            SODIUM -> nutritionSnapshot.sodiumMilligrams
            ADDED_SUGARS -> nutritionSnapshot.addedSugarsGrams
            SATURATED_FAT -> nutritionSnapshot.saturatedFatGrams
            CHOLESTEROL -> nutritionSnapshot.cholesterolMilligrams
            POTASSIUM -> nutritionSnapshot.potassiumMilligrams

            CALCIUM -> nutritionSnapshot.calciumMilligrams
            CHLORIDE -> nutritionSnapshot.chlorideMilligrams
            FOLATE -> nutritionSnapshot.folateMicrograms
            IRON -> nutritionSnapshot.ironMilligrams
            MAGNESIUM -> nutritionSnapshot.magnesiumMilligrams
            MONOUNSATURATED_FAT -> nutritionSnapshot.monounsaturatedFatGrams
            OMEGA_3 -> nutritionSnapshot.omega3FattyAcidsGrams
            OMEGA_6 -> nutritionSnapshot.omega6FattyAcidsGrams
            PHOSPHORUS -> nutritionSnapshot.phosphorusMilligrams
            POLYUNSATURATED_FAT -> nutritionSnapshot.polyunsaturatedFatGrams
            SUGAR_ALCOHOLS -> nutritionSnapshot.sugarAlcoholsGrams
            TRANS_FAT -> nutritionSnapshot.transFatGrams
            VITAMIN_A -> nutritionSnapshot.vitaminAMicrograms
            VITAMIN_B12 -> nutritionSnapshot.vitaminB12Micrograms
            VITAMIN_C -> nutritionSnapshot.vitaminCMilligrams
            VITAMIN_D -> nutritionSnapshot.vitaminDMicrograms
            VITAMIN_E -> nutritionSnapshot.vitaminEMilligrams
            VITAMIN_K -> nutritionSnapshot.vitaminKMicrograms
            ZINC -> nutritionSnapshot.zincMilligrams
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
            else -> null
        }
    }

    fun isSelectedInMacroSummary(goals: Goals): Boolean {
        if (this == CALORIES) {
            return false
        }
        return goals.macroSummarySelection.contains(this)
    }

    companion object {
        fun fromDisplayName(value: String): MainMacro? {
            return entries.firstOrNull { it.displayName == value }
        }
    }
}
