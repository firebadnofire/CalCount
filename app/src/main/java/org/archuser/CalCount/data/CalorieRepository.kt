package org.archuser.CalCount.data

import android.content.Context
import org.archuser.CalCount.data.FoodJson.parseFood
import org.archuser.CalCount.data.FoodJson.parseNutrition
import org.archuser.CalCount.data.FoodJson.serializeFood
import org.archuser.CalCount.data.FoodJson.serializeNutrition
import org.archuser.CalCount.data.model.CatalogMetadata
import org.archuser.CalCount.data.model.AppState
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.Goals
import org.archuser.CalCount.data.model.InputMode
import org.archuser.CalCount.data.model.LogEntry
import org.archuser.CalCount.data.model.MainMacro
import org.archuser.CalCount.data.model.MealType
import org.archuser.CalCount.data.model.VolumeUnit
import org.archuser.CalCount.data.model.WeightUnit
import org.archuser.CalCount.data.model.LogSourceType
import org.archuser.CalCount.data.model.NutrientCompleteness
import org.json.JSONArray
import org.json.JSONObject

class CalorieRepository(context: Context) {

    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val stateFile = StateFile(context.filesDir)
    private var lastValidState: String? = null
    private var writesEnabled = false

    @Synchronized
    fun loadState(): LoadResult {
        writesEnabled = false
        return try {
            val stored = stateFile.load({ preferences.getString(KEY_APP_STATE, null) }) {
                parseAppState(TransferJson.decode(it.encodeToByteArray(), Int.MAX_VALUE))
            }
            lastValidState = stored?.raw
            writesEnabled = true
            LoadResult(
                state = stored?.value ?: AppState(),
                errorMessage = if (stored?.recovered == true) {
                    "The latest save could not be read. The previous valid CalCount state was recovered."
                } else null
            )
        } catch (error: Exception) {
            LoadResult(
                state = AppState(),
                errorMessage = "Stored data could not be recovered. Saving is disabled to protect existing data: ${error.message}"
            )
        }
    }

    @Synchronized
    fun saveState(state: AppState): WriteResult {
        if (!writesEnabled) return WriteResult.Failure("Saving is disabled because stored data could not be loaded.")
        return try {
            val raw = serializeAppState(state).toString()
            stateFile.save(raw, lastValidState)
            lastValidState = raw
            WriteResult.Success
        } catch (error: Exception) {
            WriteResult.Failure("Local changes could not be saved: ${error.message ?: "unknown error"}")
        }
    }

    fun serializeFoodTransfer(food: Food): String = LabelCodec.encode(food).decodeToString()

    fun serializeCatalogFoodTransfer(food: Food, catalogMetadata: CatalogMetadata): String =
        LabelCodec.encode(food.copy(catalog = catalogMetadata.copy(
            verifiedAtEpochMillis = System.currentTimeMillis()
        ))).decodeToString()

    private fun parseAppState(jsonObject: JSONObject): AppState {
        require(jsonObject.has("foods") || jsonObject.has("logs") || jsonObject.has("goals")) {
            "The saved document is missing application state."
        }
        for (key in listOf("foods", "logs", "recipes")) {
            if (jsonObject.has(key)) require(jsonObject.optJSONArray(key) != null) { "Invalid saved $key." }
        }
        if (jsonObject.has("goals")) require(jsonObject.optJSONObject("goals") != null) { "Invalid saved goals." }
        val foods = jsonObject.optJSONArray("foods")?.let(::parseFoods).orEmpty()
        val logs = jsonObject.optJSONArray("logs")?.let(::parseLogs).orEmpty()
        val goals = jsonObject.optJSONObject("goals")?.let(::parseGoals) ?: Goals()
        val recipes = jsonObject.optJSONArray("recipes")?.let { array ->
            List(array.length()) { RecipeJson.decode(array.getJSONObject(it)) }
        }.orEmpty()
        return AppState(foods = foods, logs = logs, goals = goals, recipes = recipes)
    }

    private fun serializeAppState(state: AppState): JSONObject {
        return JSONObject()
            .put("foods", JSONArray().apply { state.foods.forEach { put(serializeFood(it)) } })
            .put("logs", JSONArray().apply { state.logs.forEach { put(serializeLog(it)) } })
            .put("goals", serializeGoals(state.goals))
            .put("recipes", JSONArray().apply { state.recipes.forEach { put(RecipeJson.encode(it)) } })
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

    private fun parseLog(jsonObject: JSONObject): LogEntry {
        return LogEntry(
            id = jsonObject.getString("id"),
            foodId = jsonObject.getString("foodId"),
            foodName = jsonObject.getString("foodName"),
            servingDescription = jsonObject.getString("servingDescription"),
            mealType = MealType.valueOf(jsonObject.getString("mealType")),
            foodKind = runCatching {
                FoodKind.valueOf(jsonObject.optString("foodKind", FoodKind.FOOD.name))
            }.getOrDefault(FoodKind.FOOD),
            inputMode = InputMode.valueOf(jsonObject.getString("inputMode")),
            consumedServings = jsonObject.getDouble("consumedServings"),
            consumedWeightGrams = jsonObject.getDouble("consumedWeightGrams"),
            consumedVolumeMilliliters = jsonObject.optDouble("consumedVolumeMilliliters", 0.0),
            calculatedNutrition = parseNutrition(jsonObject.getJSONObject("calculatedNutrition")),
            loggedAtEpochMillis = jsonObject.optLong("loggedAtEpochMillis"),
            sourceType = LogSourceType.fromWireName(jsonObject.optString("source_type", "food")),
            nutritionCompleteness = jsonObject.optJSONObject("nutrition_completeness")?.let { stored ->
                MainMacro.entries.filter { stored.has(it.name) }.associateWith {
                    NutrientCompleteness.valueOf(stored.getString(it.name))
                }
            }
        )
    }

    private fun parseGoals(jsonObject: JSONObject): Goals {
        val showCaloriesInLivePreview = jsonObject.optBoolean("showCaloriesInLivePreview", false)
        val showProteinInLivePreview = jsonObject.optBoolean("showProteinInLivePreview", true)
        val showCarbsInLivePreview = jsonObject.optBoolean("showCarbsInLivePreview", true)
        val showFatInLivePreview = jsonObject.optBoolean("showFatInLivePreview", true)
        val showSaturatedFatInLivePreview = jsonObject.optBoolean("showSaturatedFatInLivePreview", false)
        val showFiberInLivePreview = jsonObject.optBoolean("showFiberInLivePreview", false)
        val showSugarsInLivePreview = jsonObject.optBoolean("showSugarsInLivePreview", false)
        val showSodiumInLivePreview = jsonObject.optBoolean("showSodiumInLivePreview", false)
        val showPotassiumInLivePreview = jsonObject.optBoolean("showPotassiumInLivePreview", false)

        val mainMacro = readMainMacro(
            jsonObject = jsonObject,
            showCaloriesInLivePreview = showCaloriesInLivePreview,
            showProteinInLivePreview = showProteinInLivePreview,
            showCarbsInLivePreview = showCarbsInLivePreview,
            showFatInLivePreview = showFatInLivePreview
        )

        val macroSummarySelection = jsonObject.optJSONArray("macroSummarySelection")
            ?.let { selectionArray ->
                val parsedSelection = mutableSetOf<MainMacro>()
                for (index in 0 until selectionArray.length()) {
                    val value = selectionArray.optString(index).orEmpty()
                    if (value.isBlank()) {
                        continue
                    }
                    runCatching {
                        parsedSelection += MainMacro.valueOf(value)
                    }
                }
                parsedSelection.filterNot { it == MainMacro.CALORIES }.toSet()
            }
            ?: buildSet {
                if (showProteinInLivePreview) {
                    add(MainMacro.PROTEIN)
                }
                if (showCarbsInLivePreview) {
                    add(MainMacro.CARBS)
                }
                if (showFatInLivePreview) {
                    add(MainMacro.FAT)
                }
                if (showSaturatedFatInLivePreview) {
                    add(MainMacro.SATURATED_FAT)
                }
                if (showFiberInLivePreview) {
                    add(MainMacro.FIBER)
                }
                if (showSugarsInLivePreview) {
                    add(MainMacro.SUGARS)
                }
                if (showSodiumInLivePreview) {
                    add(MainMacro.SODIUM)
                }
                if (showPotassiumInLivePreview) {
                    add(MainMacro.POTASSIUM)
                }
            }

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
            preferredLiquidUnit = VolumeUnit.valueOf(
                jsonObject.optString("preferredLiquidUnit", VolumeUnit.MILLILITERS.name)
            ),
            useMaterialYou = jsonObject.optBoolean("useMaterialYou", true),
            mainMacro = mainMacro,
            showCaloriesInLivePreview = showCaloriesInLivePreview,
            macroSummarySelection = macroSummarySelection
        )
    }

    private fun serializeLog(logEntry: LogEntry): JSONObject {
        return JSONObject()
            .put("id", logEntry.id)
            .put("foodId", logEntry.foodId)
            .put("foodName", logEntry.foodName)
            .put("servingDescription", logEntry.servingDescription)
            .put("mealType", logEntry.mealType.name)
            .put("foodKind", logEntry.foodKind.name)
            .put("inputMode", logEntry.inputMode.name)
            .put("consumedServings", logEntry.consumedServings)
            .put("consumedWeightGrams", logEntry.consumedWeightGrams)
            .put("consumedVolumeMilliliters", logEntry.consumedVolumeMilliliters)
            .put("calculatedNutrition", serializeNutrition(logEntry.calculatedNutrition))
            .put("loggedAtEpochMillis", logEntry.loggedAtEpochMillis)
            .put("source_type", logEntry.sourceType.wireName)
            .apply {
                logEntry.nutritionCompleteness?.let { completeness ->
                    put("nutrition_completeness", JSONObject().apply {
                        completeness.forEach { (nutrient, status) -> put(nutrient.name, status.name) }
                    })
                }
            }
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
            .put("preferredLiquidUnit", goals.preferredLiquidUnit.name)
            .put("useMaterialYou", goals.useMaterialYou)
            .put("mainMacro", goals.mainMacro.name)
            .put("showCaloriesInLivePreview", goals.showCaloriesInLivePreview)
            .put(
                "macroSummarySelection",
                JSONArray().apply {
                    goals.macroSummarySelection
                        .filterNot { it == MainMacro.CALORIES }
                        .forEach { put(it.name) }
                }
            )
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
