package org.archuser.CalCount.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.archuser.CalCount.data.CalorieRepository
import org.archuser.CalCount.data.model.AppState
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.InputMode
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.MealType
import org.archuser.CalCount.data.model.NutritionSnapshot
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

        val servingWeightGrams = parseRequiredPositiveDouble(input.servingWeightGrams, "Serving weight")
            ?: return false
        val calories = parseRequiredNonNegativeDouble(input.calories, "Calories") ?: return false
        val fat = parseRequiredNonNegativeDouble(input.fatGrams, "Fat") ?: return false
        val carbs = parseRequiredNonNegativeDouble(input.carbsGrams, "Carbohydrates") ?: return false
        val protein = parseRequiredNonNegativeDouble(input.proteinGrams, "Protein") ?: return false
        val saturatedFatRead = readOptionalNonNegativeDouble(input.saturatedFatGrams, "Saturated fat")
            ?: return false
        val fiberRead = readOptionalNonNegativeDouble(input.fiberGrams, "Fiber") ?: return false
        val sugarsRead = readOptionalNonNegativeDouble(input.sugarGrams, "Sugars") ?: return false
        val sodiumRead = readOptionalNonNegativeDouble(input.sodiumMilligrams, "Sodium") ?: return false
        val potassiumRead = readOptionalNonNegativeDouble(input.potassiumMilligrams, "Potassium")
            ?: return false
        val servingsPerContainerRead = readOptionalPositiveDouble(
            input.servingsPerContainer,
            "Servings per container"
        ) ?: return false

        val saturatedFat = saturatedFatRead.value
        val fiber = fiberRead.value
        val sugars = sugarsRead.value
        val sodium = sodiumRead.value
        val potassium = potassiumRead.value
        val servingsPerContainer = servingsPerContainerRead.value

        val foodId = editingFoodId ?: UUID.randomUUID().toString()
        val createdAt = editingFoodId?.let { existingId ->
            appState.foods.firstOrNull { it.id == existingId }?.createdAtEpochMillis
        } ?: System.currentTimeMillis()

        val food = Food(
            id = foodId,
            name = name,
            servingDescription = servingDescription,
            servingWeightGrams = servingWeightGrams,
            nutritionPerServing = NutritionSnapshot(
                calories = calories,
                fatGrams = fat,
                carbsGrams = carbs,
                proteinGrams = protein,
                saturatedFatGrams = saturatedFat,
                fiberGrams = fiber,
                sugarGrams = sugars,
                sodiumMilligrams = sodium,
                potassiumMilligrams = potassium
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
                    val amount = parseRequiredPositiveDouble(
                        input.amount,
                        "Weight in ${appState.goals.preferredUnit.shortLabel}"
                    ) ?: return false
                    val weightGrams = NutritionCalculator.convertToGrams(amount, appState.goals.preferredUnit)
                    NutritionCalculator.calculateForWeight(food, weightGrams)
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
            inputMode = input.inputMode,
            consumedServings = calculation.servings,
            consumedWeightGrams = calculation.weightGrams,
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
            mainMacro = mainMacro,
            showCaloriesInLivePreview = input.showCaloriesInLivePreview,
            showProteinInLivePreview = input.showProteinInLivePreview,
            showCarbsInLivePreview = input.showCarbsInLivePreview,
            showFatInLivePreview = input.showFatInLivePreview,
            showSaturatedFatInLivePreview = input.showSaturatedFatInLivePreview,
            showFiberInLivePreview = input.showFiberInLivePreview,
            showSugarsInLivePreview = input.showSugarsInLivePreview,
            showSodiumInLivePreview = input.showSodiumInLivePreview,
            showPotassiumInLivePreview = input.showPotassiumInLivePreview
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
        val name: String,
        val servingDescription: String,
        val servingWeightGrams: String,
        val calories: String,
        val fatGrams: String,
        val carbsGrams: String,
        val proteinGrams: String,
        val saturatedFatGrams: String,
        val fiberGrams: String,
        val sugarGrams: String,
        val sodiumMilligrams: String,
        val potassiumMilligrams: String,
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
        val mainMacro: String,
        val showCaloriesInLivePreview: Boolean,
        val showProteinInLivePreview: Boolean,
        val showCarbsInLivePreview: Boolean,
        val showFatInLivePreview: Boolean,
        val showSaturatedFatInLivePreview: Boolean,
        val showFiberInLivePreview: Boolean,
        val showSugarsInLivePreview: Boolean,
        val showSodiumInLivePreview: Boolean,
        val showPotassiumInLivePreview: Boolean
    )
}
