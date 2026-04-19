package org.archuser.CalCount.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.archuser.CalCount.data.CalorieRepository
import org.archuser.CalCount.data.model.AppState
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.InputMode
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.MealType
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.VolumeUnit
import org.archuser.CalCount.data.model.WeightUnit
import org.archuser.CalCount.domain.NutritionCalculator
import java.util.UUID

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CalorieRepository(application)

    private var appState = AppState()
    private var editingFoodId: String? = null

    private val _uiState = MutableLiveData(AppUiState())
    val uiState: LiveData<AppUiState> = _uiState

    private val _message = MutableLiveData<String?>()
    val message: LiveData<String?> = _message

    private val _settingsSavedEvent = MutableLiveData<Long?>()
    val settingsSavedEvent: LiveData<Long?> = _settingsSavedEvent

    init {
        val loadResult = repository.loadState()
        appState = loadResult.state
        publishState()
        loadResult.errorMessage?.let(::setMessage)
    }

    fun clearMessage() {
        _message.value = null
    }

    fun consumeSettingsSavedEvent() {
        _settingsSavedEvent.value = null
    }

    fun beginEditingFood(foodId: String) {
        editingFoodId = foodId
        publishState()
    }

    fun cancelEditingFood() {
        editingFoodId = null
        publishState()
    }

    fun getFood(foodId: String): Food? {
        return appState.foods.firstOrNull { it.id == foodId }
    }

    fun saveFood(input: SaveFoodInput): Boolean {
        val name = input.name.trim()
        val servingDescription = input.servingDescription.trim()

        if (name.isBlank()) {
            return fail("Food name is required.")
        }
        if (servingDescription.isBlank()) {
            return fail("Serving description is required.")
        }

        val kind = FoodKind.fromDisplayName(input.kind) ?: FoodKind.FOOD
        val servingAmountLabel = if (kind == FoodKind.LIQUID) "Serving volume" else "Serving weight"
        val servingAmount = parseRequiredPositiveDouble(input.servingAmount, servingAmountLabel) ?: return false
        val servingWeightGrams = if (kind == FoodKind.FOOD) servingAmount else 0.0
        val servingVolumeMilliliters = if (kind == FoodKind.LIQUID) servingAmount else 0.0
        val calories = parseRequiredNonNegativeDouble(input.calories, "Calories") ?: return false
        val fat = parseRequiredNonNegativeDouble(input.fatGrams, "Fat") ?: return false
        val carbs = parseRequiredNonNegativeDouble(input.carbsGrams, "Carbohydrates") ?: return false
        val protein = parseRequiredNonNegativeDouble(input.proteinGrams, "Protein") ?: return false
        val saturatedFatRead = readOptionalNonNegativeDouble(input.saturatedFatGrams, "Saturated fat")
            ?: return false
        val fiberRead = readOptionalNonNegativeDouble(input.fiberGrams, "Fiber") ?: return false
        val sugarsRead = readOptionalNonNegativeDouble(input.sugarGrams, "Sugars") ?: return false
        val addedSugarsRead = readOptionalNonNegativeDouble(input.addedSugarsGrams, "Added sugars")
            ?: return false
        val sugarAlcoholsRead =
            readOptionalNonNegativeDouble(input.sugarAlcoholsGrams, "Sugar alcohols") ?: return false
        val sodiumRead = readOptionalNonNegativeDouble(input.sodiumMilligrams, "Sodium") ?: return false
        val potassiumRead = readOptionalNonNegativeDouble(input.potassiumMilligrams, "Potassium")
            ?: return false
        val cholesterolRead = readOptionalNonNegativeDouble(input.cholesterolMilligrams, "Cholesterol")
            ?: return false
        val transFatRead = readOptionalNonNegativeDouble(input.transFatGrams, "Trans fat") ?: return false
        val monounsaturatedFatRead = readOptionalNonNegativeDouble(
            input.monounsaturatedFatGrams,
            "Monounsaturated fat"
        ) ?: return false
        val polyunsaturatedFatRead = readOptionalNonNegativeDouble(
            input.polyunsaturatedFatGrams,
            "Polyunsaturated fat"
        ) ?: return false
        val omega3Read = readOptionalNonNegativeDouble(input.omega3FattyAcidsGrams, "Omega-3") ?: return false
        val omega6Read = readOptionalNonNegativeDouble(input.omega6FattyAcidsGrams, "Omega-6") ?: return false
        val calciumRead = readOptionalNonNegativeDouble(input.calciumMilligrams, "Calcium") ?: return false
        val chlorideRead = readOptionalNonNegativeDouble(input.chlorideMilligrams, "Chloride") ?: return false
        val folateRead = readOptionalNonNegativeDouble(input.folateMicrograms, "Folate") ?: return false
        val ironRead = readOptionalNonNegativeDouble(input.ironMilligrams, "Iron") ?: return false
        val magnesiumRead = readOptionalNonNegativeDouble(input.magnesiumMilligrams, "Magnesium")
            ?: return false
        val phosphorusRead = readOptionalNonNegativeDouble(input.phosphorusMilligrams, "Phosphorus")
            ?: return false
        val zincRead = readOptionalNonNegativeDouble(input.zincMilligrams, "Zinc") ?: return false
        val vitaminARead = readOptionalNonNegativeDouble(input.vitaminAMicrograms, "Vitamin A")
            ?: return false
        val vitaminB12Read = readOptionalNonNegativeDouble(input.vitaminB12Micrograms, "Vitamin B12")
            ?: return false
        val vitaminCRead = readOptionalNonNegativeDouble(input.vitaminCMilligrams, "Vitamin C")
            ?: return false
        val vitaminDRead = readOptionalNonNegativeDouble(input.vitaminDMicrograms, "Vitamin D")
            ?: return false
        val vitaminERead = readOptionalNonNegativeDouble(input.vitaminEMilligrams, "Vitamin E")
            ?: return false
        val vitaminKRead = readOptionalNonNegativeDouble(input.vitaminKMicrograms, "Vitamin K")
            ?: return false
        val servingsPerContainerRead = readOptionalPositiveDouble(
            input.servingsPerContainer,
            "Servings per container"
        ) ?: return false

        val saturatedFat = saturatedFatRead.value
        val fiber = fiberRead.value
        val sugars = sugarsRead.value
        val addedSugars = addedSugarsRead.value
        val sugarAlcohols = sugarAlcoholsRead.value
        val sodium = sodiumRead.value
        val potassium = potassiumRead.value
        val cholesterol = cholesterolRead.value
        val transFat = transFatRead.value
        val monounsaturatedFat = monounsaturatedFatRead.value
        val polyunsaturatedFat = polyunsaturatedFatRead.value
        val omega3 = omega3Read.value
        val omega6 = omega6Read.value
        val calcium = calciumRead.value
        val chloride = chlorideRead.value
        val folate = folateRead.value
        val iron = ironRead.value
        val magnesium = magnesiumRead.value
        val phosphorus = phosphorusRead.value
        val zinc = zincRead.value
        val vitaminA = vitaminARead.value
        val vitaminB12 = vitaminB12Read.value
        val vitaminC = vitaminCRead.value
        val vitaminD = vitaminDRead.value
        val vitaminE = vitaminERead.value
        val vitaminK = vitaminKRead.value
        val servingsPerContainer = servingsPerContainerRead.value

        val foodId = editingFoodId ?: UUID.randomUUID().toString()
        val createdAt = editingFoodId?.let { existingId ->
            appState.foods.firstOrNull { it.id == existingId }?.createdAtEpochMillis
        } ?: System.currentTimeMillis()

        val food = Food(
            id = foodId,
            name = name,
            servingDescription = servingDescription,
            kind = kind,
            servingWeightGrams = servingWeightGrams,
            servingVolumeMilliliters = servingVolumeMilliliters,
            nutritionPerServing = NutritionSnapshot(
                calories = calories,
                fatGrams = fat,
                carbsGrams = carbs,
                proteinGrams = protein,
                saturatedFatGrams = saturatedFat,
                fiberGrams = fiber,
                sugarGrams = sugars,
                addedSugarsGrams = addedSugars,
                sugarAlcoholsGrams = sugarAlcohols,
                sodiumMilligrams = sodium,
                potassiumMilligrams = potassium,
                cholesterolMilligrams = cholesterol,
                transFatGrams = transFat,
                monounsaturatedFatGrams = monounsaturatedFat,
                polyunsaturatedFatGrams = polyunsaturatedFat,
                omega3FattyAcidsGrams = omega3,
                omega6FattyAcidsGrams = omega6,
                calciumMilligrams = calcium,
                chlorideMilligrams = chloride,
                folateMicrograms = folate,
                ironMilligrams = iron,
                magnesiumMilligrams = magnesium,
                phosphorusMilligrams = phosphorus,
                vitaminAMicrograms = vitaminA,
                vitaminB12Micrograms = vitaminB12,
                vitaminCMilligrams = vitaminC,
                vitaminDMicrograms = vitaminD,
                vitaminEMilligrams = vitaminE,
                vitaminKMicrograms = vitaminK,
                zincMilligrams = zinc
            ),
            servingsPerContainer = servingsPerContainer,
            createdAtEpochMillis = createdAt
        )

        val updatedFoods = if (editingFoodId == null) {
            (appState.foods + food).sortedBy { it.name.lowercase() }
        } else {
            appState.foods.map { existing -> if (existing.id == food.id) food else existing }
                .sortedBy { it.name.lowercase() }
        }

        return persist(
            appState.copy(foods = updatedFoods),
            successMessage = if (editingFoodId == null) {
                "${food.name} saved to your food library."
            } else {
                "${food.name} updated."
            }
        ) {
            editingFoodId = null
        }
    }

    fun logFood(input: LogFoodInput): Boolean {
        val food = getFood(input.foodId)
            ?: return fail("That food is no longer available. Refresh the library and try again.")

        val calculation = try {
            when (input.inputMode) {
                InputMode.SERVINGS -> {
                    val servings = parseRequiredPositiveDouble(input.amount, "Servings") ?: return false
                    NutritionCalculator.calculateForServings(food, servings)
                }

                InputMode.WEIGHT -> {
                    if (food.kind == FoodKind.LIQUID) {
                        val amount = parseRequiredPositiveDouble(
                            input.amount,
                            "Volume in ${appState.goals.preferredLiquidUnit.shortLabel}"
                        ) ?: return false
                        val volumeMilliliters = NutritionCalculator.convertToMilliliters(
                            amount,
                            appState.goals.preferredLiquidUnit
                        )
                        NutritionCalculator.calculateForVolume(food, volumeMilliliters)
                    } else {
                        val amount = parseRequiredPositiveDouble(
                            input.amount,
                            "Weight in ${appState.goals.preferredUnit.shortLabel}"
                        ) ?: return false
                        val weightGrams = NutritionCalculator.convertToGrams(amount, appState.goals.preferredUnit)
                        NutritionCalculator.calculateForWeight(food, weightGrams)
                    }
                }
            }
        } catch (error: IllegalArgumentException) {
            return fail(error.message ?: "The amount entered is invalid.")
        }

        val logEntry = LogEntry(
            id = UUID.randomUUID().toString(),
            foodId = food.id,
            foodName = food.name,
            servingDescription = food.servingDescription,
            mealType = input.mealType,
            foodKind = food.kind,
            inputMode = input.inputMode,
            consumedServings = calculation.servings,
            consumedWeightGrams = calculation.weightGrams,
            consumedVolumeMilliliters = calculation.volumeMilliliters,
            calculatedNutrition = calculation.nutrition,
            loggedAtEpochMillis = System.currentTimeMillis()
        )

        val updatedLogs = (appState.logs + logEntry).sortedByDescending(LogEntry::loggedAtEpochMillis)
        return persist(
            appState.copy(logs = updatedLogs),
            successMessage = "${food.name} logged to ${input.mealType.displayName.lowercase()}."
        )
    }

    fun updateGoals(input: UpdateGoalsInput): Boolean {
        val currentGoals = appState.goals
        val dailyCalories = if (input.dailyCalories.isBlank()) {
            currentGoals.dailyCalories
        } else {
            parseRequiredPositiveDouble(input.dailyCalories, "Daily calorie goal") ?: return false
        }
        val fatTargetRead = readOptionalNonNegativeDouble(input.fatTargetGrams, "Fat target")
            ?: return false
        val carbsTargetRead = readOptionalNonNegativeDouble(
            input.carbsTargetGrams,
            "Carbohydrate target"
        ) ?: return false
        val proteinTargetRead = readOptionalNonNegativeDouble(input.proteinTargetGrams, "Protein target")
            ?: return false

        val saturatedFatTargetRead = readOptionalNonNegativeDouble(
            input.saturatedFatTargetGrams,
            "Saturated fat target"
        ) ?: return false
        val fiberTargetRead = readOptionalNonNegativeDouble(input.fiberTargetGrams, "Fiber target")
            ?: return false
        val sugarsTargetRead = readOptionalNonNegativeDouble(input.sugarsTargetGrams, "Sugars target")
            ?: return false
        val sodiumTargetRead = readOptionalNonNegativeDouble(input.sodiumTargetMilligrams, "Sodium target")
            ?: return false
        val potassiumTargetRead =
            readOptionalNonNegativeDouble(input.potassiumTargetMilligrams, "Potassium target")
                ?: return false

        val fatTarget = fatTargetRead.value
        val carbsTarget = carbsTargetRead.value
        val proteinTarget = proteinTargetRead.value
        val saturatedFatTarget = saturatedFatTargetRead.value
        val fiberTarget = fiberTargetRead.value
        val sugarsTarget = sugarsTargetRead.value
        val sodiumTarget = sodiumTargetRead.value
        val potassiumTarget = potassiumTargetRead.value

        val preferredUnit = if (input.preferredUnit.isBlank()) {
            currentGoals.preferredUnit
        } else {
            WeightUnit.fromDisplayName(input.preferredUnit)
                ?: return fail("Select a valid preferred weight unit.")
        }

        val preferredLiquidUnit = if (input.preferredLiquidUnit.isBlank()) {
            currentGoals.preferredLiquidUnit
        } else {
            VolumeUnit.fromDisplayName(input.preferredLiquidUnit)
                ?: return fail("Select a valid preferred liquid unit.")
        }

        val mainMacro = if (input.mainMacro.isBlank()) {
            currentGoals.mainMacro
        } else {
            MainMacro.fromDisplayName(input.mainMacro)
                ?: return fail("Select a valid main macro.")
        }

        val updatedGoals = Goals(
            dailyCalories = dailyCalories,
            fatTargetGrams = fatTarget,
            carbsTargetGrams = carbsTarget,
            proteinTargetGrams = proteinTarget,
            saturatedFatTargetGrams = saturatedFatTarget,
            fiberTargetGrams = fiberTarget,
            sugarsTargetGrams = sugarsTarget,
            sodiumTargetMilligrams = sodiumTarget,
            potassiumTargetMilligrams = potassiumTarget,
            preferredUnit = preferredUnit,
            preferredLiquidUnit = preferredLiquidUnit,
            useMaterialYou = input.useMaterialYou,
            mainMacro = mainMacro,
            showCaloriesInLivePreview = input.showCaloriesInLivePreview,
            macroSummarySelection = input.macroSummarySelection.filterNot { it == MainMacro.CALORIES }.toSet()
        )

        val didPersist = persist(
            appState.copy(goals = updatedGoals),
            successMessage = null
        )
        if (didPersist) {
            _settingsSavedEvent.value = System.currentTimeMillis()
        }
        return didPersist
    }

    private fun persist(
        updatedState: AppState,
        successMessage: String?,
        afterSuccess: (() -> Unit)? = null
    ): Boolean {
        return when (val writeResult = repository.saveState(updatedState)) {
            CalorieRepository.WriteResult.Success -> {
                appState = updatedState
                afterSuccess?.invoke()
                publishState()
                successMessage?.let(::setMessage)
                true
            }

            is CalorieRepository.WriteResult.Failure -> fail(writeResult.message)
        }
    }

    private fun publishState() {
        _uiState.value = AppUiState(
            foods = appState.foods.sortedBy { it.name.lowercase() },
            logs = appState.logs.sortedByDescending(LogEntry::loggedAtEpochMillis),
            goals = appState.goals,
            editingFood = appState.foods.firstOrNull { it.id == editingFoodId }
        )
    }

    private fun parseRequiredPositiveDouble(rawValue: String, fieldName: String): Double? {
        val parsedValue = rawValue.trim().toDoubleOrNull()
            ?: return failDouble("$fieldName must be a number.")
        if (parsedValue <= 0.0) {
            return failDouble("$fieldName must be greater than zero.")
        }
        return parsedValue
    }

    private fun parseRequiredNonNegativeDouble(rawValue: String, fieldName: String): Double? {
        val parsedValue = rawValue.trim().toDoubleOrNull()
            ?: return failDouble("$fieldName must be a number.")
        if (parsedValue < 0.0) {
            return failDouble("$fieldName cannot be negative.")
        }
        return parsedValue
    }

    private fun parseOptionalPositiveDouble(rawValue: String, fieldName: String): Double? {
        if (rawValue.isBlank()) {
            return null
        }
        val parsedValue = rawValue.trim().toDoubleOrNull()
            ?: return failDouble("$fieldName must be a number.")
        if (parsedValue <= 0.0) {
            return failDouble("$fieldName must be greater than zero.")
        }
        return parsedValue
    }

    private fun parseOptionalNonNegativeDouble(rawValue: String, fieldName: String): Double? {
        if (rawValue.isBlank()) {
            return null
        }
        val parsedValue = rawValue.trim().toDoubleOrNull()
            ?: return failDouble("$fieldName must be a number.")
        if (parsedValue < 0.0) {
            return failDouble("$fieldName cannot be negative.")
        }
        return parsedValue
    }

    private fun readOptionalPositiveDouble(rawValue: String, fieldName: String): OptionalDoubleReadResult? {
        if (rawValue.isBlank()) {
            return OptionalDoubleReadResult(value = null)
        }
        val parsedValue = parseOptionalPositiveDouble(rawValue, fieldName)
            ?: return null
        return OptionalDoubleReadResult(value = parsedValue)
    }

    private fun readOptionalNonNegativeDouble(
        rawValue: String,
        fieldName: String
    ): OptionalDoubleReadResult? {
        if (rawValue.isBlank()) {
            return OptionalDoubleReadResult(value = null)
        }
        val parsedValue = parseOptionalNonNegativeDouble(rawValue, fieldName)
            ?: return null
        return OptionalDoubleReadResult(value = parsedValue)
    }

    private fun fail(message: String): Boolean {
        setMessage(message)
        return false
    }

    private fun failDouble(message: String): Double? {
        setMessage(message)
        return null
    }

    private fun setMessage(message: String) {
        _message.value = message
    }

    private data class OptionalDoubleReadResult(
        val value: Double?
    )

    data class SaveFoodInput(
        val kind: String,
        val name: String,
        val servingDescription: String,
        val servingAmount: String,
        val calories: String,
        val fatGrams: String,
        val carbsGrams: String,
        val proteinGrams: String,
        val saturatedFatGrams: String,
        val fiberGrams: String,
        val sugarGrams: String,
        val addedSugarsGrams: String,
        val sugarAlcoholsGrams: String,
        val sodiumMilligrams: String,
        val potassiumMilligrams: String,
        val cholesterolMilligrams: String,
        val transFatGrams: String,
        val monounsaturatedFatGrams: String,
        val polyunsaturatedFatGrams: String,
        val omega3FattyAcidsGrams: String,
        val omega6FattyAcidsGrams: String,
        val calciumMilligrams: String,
        val chlorideMilligrams: String,
        val folateMicrograms: String,
        val ironMilligrams: String,
        val magnesiumMilligrams: String,
        val phosphorusMilligrams: String,
        val zincMilligrams: String,
        val vitaminAMicrograms: String,
        val vitaminB12Micrograms: String,
        val vitaminCMilligrams: String,
        val vitaminDMicrograms: String,
        val vitaminEMilligrams: String,
        val vitaminKMicrograms: String,
        val servingsPerContainer: String
    )

    data class LogFoodInput(
        val foodId: String,
        val mealType: MealType,
        val inputMode: InputMode,
        val amount: String
    )

    data class UpdateGoalsInput(
        val dailyCalories: String,
        val fatTargetGrams: String,
        val carbsTargetGrams: String,
        val proteinTargetGrams: String,
        val saturatedFatTargetGrams: String,
        val fiberTargetGrams: String,
        val sugarsTargetGrams: String,
        val sodiumTargetMilligrams: String,
        val potassiumTargetMilligrams: String,
        val preferredUnit: String,
        val preferredLiquidUnit: String,
        val useMaterialYou: Boolean,
        val mainMacro: String,
        val showCaloriesInLivePreview: Boolean,
        val macroSummarySelection: Set<MainMacro>
    )
}
