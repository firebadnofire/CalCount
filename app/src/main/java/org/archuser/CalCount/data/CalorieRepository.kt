package org.archuser.CalCount.data

import android.content.Context
import org.archuser.CalCount.data.model.AppState
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.InputMode
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.MealType
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.archuser.CalCount.data.model.WeightUnit
import org.json.JSONArray
import org.json.JSONObject

class CalorieRepository(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadState(): LoadResult {
        val rawState = preferences.getString(KEY_APP_STATE, null)
            ?: return LoadResult(AppState())

        return try {
            LoadResult(parseAppState(JSONObject(rawState)))
        } catch (error: Exception) {
            LoadResult(
                state = AppState(),
                errorMessage = "Stored app data could not be read and was reset. Create foods again to continue."
            )
        }
    }

    fun saveState(state: AppState): WriteResult {
        return try {
            val didPersist = preferences.edit()
                .putString(KEY_APP_STATE, serializeAppState(state).toString())
                .commit()

            if (didPersist) {
                WriteResult.Success
            } else {
                WriteResult.Failure("Local changes could not be saved. Check device storage and try again.")
            }
        } catch (error: Exception) {
            WriteResult.Failure("Local changes could not be saved: ${error.message ?: "unknown error"}")
        }
    }

    private fun parseAppState(jsonObject: JSONObject): AppState {
        val foods = jsonObject.optJSONArray("foods")?.let(::parseFoods).orEmpty()
        val logs = jsonObject.optJSONArray("logs")?.let(::parseLogs).orEmpty()
        val goals = jsonObject.optJSONObject("goals")?.let(::parseGoals) ?: Goals()
        return AppState(foods = foods, logs = logs, goals = goals)
    }

    private fun serializeAppState(state: AppState): JSONObject {
        return JSONObject()
            .put("foods", JSONArray().apply { state.foods.forEach { put(serializeFood(it)) } })
            .put("logs", JSONArray().apply { state.logs.forEach { put(serializeLog(it)) } })
            .put("goals", serializeGoals(state.goals))
    }

    private fun parseFoods(array: JSONArray): List<Food> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(parseFood(array.getJSONObject(index)))
            }
        }
    }

    private fun parseLogs(array: JSONArray): List<LogEntry> {
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                add(parseLog(array.getJSONObject(index)))
            }
        }
    }

    private fun parseFood(jsonObject: JSONObject): Food {
        return Food(
            id = jsonObject.getString("id"),
            name = jsonObject.getString("name"),
            servingDescription = jsonObject.getString("servingDescription"),
            servingWeightGrams = jsonObject.getDouble("servingWeightGrams"),
            nutritionPerServing = parseNutrition(jsonObject.getJSONObject("nutritionPerServing")),
            servingsPerContainer = jsonObject.optNullableDouble("servingsPerContainer"),
            createdAtEpochMillis = jsonObject.optLong("createdAtEpochMillis")
        )
    }

    private fun parseLog(jsonObject: JSONObject): LogEntry {
        return LogEntry(
            id = jsonObject.getString("id"),
            foodId = jsonObject.getString("foodId"),
            foodName = jsonObject.getString("foodName"),
            servingDescription = jsonObject.getString("servingDescription"),
            mealType = MealType.valueOf(jsonObject.getString("mealType")),
            inputMode = InputMode.valueOf(jsonObject.getString("inputMode")),
            consumedServings = jsonObject.getDouble("consumedServings"),
            consumedWeightGrams = jsonObject.getDouble("consumedWeightGrams"),
            calculatedNutrition = parseNutrition(jsonObject.getJSONObject("calculatedNutrition")),
            loggedAtEpochMillis = jsonObject.optLong("loggedAtEpochMillis")
        )
    }

    private fun parseGoals(jsonObject: JSONObject): Goals {
        val showCaloriesInLivePreview = jsonObject.optBoolean("showCaloriesInLivePreview", false)
        val showProteinInLivePreview = jsonObject.optBoolean("showProteinInLivePreview", true)
        val showCarbsInLivePreview = jsonObject.optBoolean("showCarbsInLivePreview", true)
        val showFatInLivePreview = jsonObject.optBoolean("showFatInLivePreview", true)

        val mainMacro = readMainMacro(
            jsonObject = jsonObject,
            showCaloriesInLivePreview = showCaloriesInLivePreview,
            showProteinInLivePreview = showProteinInLivePreview,
            showCarbsInLivePreview = showCarbsInLivePreview,
            showFatInLivePreview = showFatInLivePreview
        )

        return Goals(
            dailyCalories = jsonObject.optDouble("dailyCalories", 2000.0),
            fatTargetGrams = jsonObject.optNullableDouble("fatTargetGrams"),
            carbsTargetGrams = jsonObject.optNullableDouble("carbsTargetGrams"),
            proteinTargetGrams = jsonObject.optNullableDouble("proteinTargetGrams"),
            saturatedFatTargetGrams = jsonObject.optNullableDouble("saturatedFatTargetGrams"),
            fiberTargetGrams = jsonObject.optNullableDouble("fiberTargetGrams"),
            sugarsTargetGrams = jsonObject.optNullableDouble("sugarsTargetGrams"),
            sodiumTargetMilligrams = jsonObject.optNullableDouble("sodiumTargetMilligrams"),
            potassiumTargetMilligrams = jsonObject.optNullableDouble("potassiumTargetMilligrams"),
            preferredUnit = WeightUnit.valueOf(
                jsonObject.optString("preferredUnit", WeightUnit.GRAMS.name)
            ),
            mainMacro = mainMacro,
            showCaloriesInLivePreview = showCaloriesInLivePreview,
            showProteinInLivePreview = showProteinInLivePreview,
            showCarbsInLivePreview = showCarbsInLivePreview,
            showFatInLivePreview = showFatInLivePreview,
            showSaturatedFatInLivePreview = jsonObject.optBoolean("showSaturatedFatInLivePreview", false),
            showFiberInLivePreview = jsonObject.optBoolean("showFiberInLivePreview", false),
            showSugarsInLivePreview = jsonObject.optBoolean("showSugarsInLivePreview", false),
            showSodiumInLivePreview = jsonObject.optBoolean("showSodiumInLivePreview", false),
            showPotassiumInLivePreview = jsonObject.optBoolean("showPotassiumInLivePreview", false)
        )
    }

    private fun parseNutrition(jsonObject: JSONObject): NutritionSnapshot {
        return NutritionSnapshot(
            calories = jsonObject.getDouble("calories"),
            fatGrams = jsonObject.getDouble("fatGrams"),
            carbsGrams = jsonObject.getDouble("carbsGrams"),
            proteinGrams = jsonObject.getDouble("proteinGrams"),
            saturatedFatGrams = jsonObject.optNullableDouble("saturatedFatGrams"),
            fiberGrams = jsonObject.optNullableDouble("fiberGrams"),
            sugarGrams = jsonObject.optNullableDouble("sugarGrams"),
            sodiumMilligrams = jsonObject.optNullableDouble("sodiumMilligrams"),
            potassiumMilligrams = jsonObject.optNullableDouble("potassiumMilligrams")
        )
    }

    private fun serializeFood(food: Food): JSONObject {
        return JSONObject()
            .put("id", food.id)
            .put("name", food.name)
            .put("servingDescription", food.servingDescription)
            .put("servingWeightGrams", food.servingWeightGrams)
            .put("nutritionPerServing", serializeNutrition(food.nutritionPerServing))
            .putNullable("servingsPerContainer", food.servingsPerContainer)
            .put("createdAtEpochMillis", food.createdAtEpochMillis)
    }

    private fun serializeLog(logEntry: LogEntry): JSONObject {
        return JSONObject()
            .put("id", logEntry.id)
            .put("foodId", logEntry.foodId)
            .put("foodName", logEntry.foodName)
            .put("servingDescription", logEntry.servingDescription)
            .put("mealType", logEntry.mealType.name)
            .put("inputMode", logEntry.inputMode.name)
            .put("consumedServings", logEntry.consumedServings)
            .put("consumedWeightGrams", logEntry.consumedWeightGrams)
            .put("calculatedNutrition", serializeNutrition(logEntry.calculatedNutrition))
            .put("loggedAtEpochMillis", logEntry.loggedAtEpochMillis)
    }

    private fun serializeGoals(goals: Goals): JSONObject {
        return JSONObject()
            .put("dailyCalories", goals.dailyCalories)
            .putNullable("fatTargetGrams", goals.fatTargetGrams)
            .putNullable("carbsTargetGrams", goals.carbsTargetGrams)
            .putNullable("proteinTargetGrams", goals.proteinTargetGrams)
            .putNullable("saturatedFatTargetGrams", goals.saturatedFatTargetGrams)
            .putNullable("fiberTargetGrams", goals.fiberTargetGrams)
            .putNullable("sugarsTargetGrams", goals.sugarsTargetGrams)
            .putNullable("sodiumTargetMilligrams", goals.sodiumTargetMilligrams)
            .putNullable("potassiumTargetMilligrams", goals.potassiumTargetMilligrams)
            .put("preferredUnit", goals.preferredUnit.name)
            .put("mainMacro", goals.mainMacro.name)
            .put("showCaloriesInLivePreview", goals.showCaloriesInLivePreview)
            .put("showProteinInLivePreview", goals.showProteinInLivePreview)
            .put("showCarbsInLivePreview", goals.showCarbsInLivePreview)
            .put("showFatInLivePreview", goals.showFatInLivePreview)
            .put("showSaturatedFatInLivePreview", goals.showSaturatedFatInLivePreview)
            .put("showFiberInLivePreview", goals.showFiberInLivePreview)
            .put("showSugarsInLivePreview", goals.showSugarsInLivePreview)
            .put("showSodiumInLivePreview", goals.showSodiumInLivePreview)
            .put("showPotassiumInLivePreview", goals.showPotassiumInLivePreview)
    }

    private fun serializeNutrition(nutritionSnapshot: NutritionSnapshot): JSONObject {
        return JSONObject()
            .put("calories", nutritionSnapshot.calories)
            .put("fatGrams", nutritionSnapshot.fatGrams)
            .put("carbsGrams", nutritionSnapshot.carbsGrams)
            .put("proteinGrams", nutritionSnapshot.proteinGrams)
            .putNullable("saturatedFatGrams", nutritionSnapshot.saturatedFatGrams)
            .putNullable("fiberGrams", nutritionSnapshot.fiberGrams)
            .putNullable("sugarGrams", nutritionSnapshot.sugarGrams)
            .putNullable("sodiumMilligrams", nutritionSnapshot.sodiumMilligrams)
            .putNullable("potassiumMilligrams", nutritionSnapshot.potassiumMilligrams)
    }

    private fun JSONObject.putNullable(key: String, value: Double?): JSONObject {
        return put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        return if (has(key) && !isNull(key)) getDouble(key) else null
    }

    private fun readMainMacro(
        jsonObject: JSONObject,
        showCaloriesInLivePreview: Boolean,
        showProteinInLivePreview: Boolean,
        showCarbsInLivePreview: Boolean,
        showFatInLivePreview: Boolean
    ): MainMacro {
        val storedValue = jsonObject.optString("mainMacro", "")
        if (storedValue.isNotBlank()) {
            runCatching { return MainMacro.valueOf(storedValue) }
        }

        if (showCaloriesInLivePreview) {
            return MainMacro.CALORIES
        }
        if (showProteinInLivePreview) {
            return MainMacro.PROTEIN
        }
        if (showCarbsInLivePreview) {
            return MainMacro.CARBS
        }
        if (showFatInLivePreview) {
            return MainMacro.FAT
        }
        return MainMacro.CALORIES
    }

    data class LoadResult(
        val state: AppState,
        val errorMessage: String? = null
    )

    sealed interface WriteResult {
        data object Success : WriteResult
        data class Failure(val message: String) : WriteResult
    }

    companion object {
        private const val PREFS_NAME = "cal_count"
        private const val KEY_APP_STATE = "app_state"
    }
}
