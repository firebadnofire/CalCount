package org.archuser.CalCount.data

import org.archuser.CalCount.data.model.CatalogMetadata
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.json.JSONObject

internal object FoodJson {
    fun parseFood(jsonObject: JSONObject): Food {
        val kind = runCatching {
            FoodKind.valueOf(jsonObject.optString("kind", FoodKind.FOOD.name))
        }.getOrDefault(FoodKind.FOOD)
        val servingWeightGrams = jsonObject.optNullableDouble("servingWeightGrams")
            ?: if (kind == FoodKind.FOOD) {
                jsonObject.getDouble("servingWeightGrams")
            } else {
                0.0
            }
        val servingVolumeMilliliters = jsonObject.optNullableDouble("servingVolumeMilliliters")
            ?: if (kind == FoodKind.LIQUID) {
                jsonObject.getDouble("servingVolumeMilliliters")
            } else {
                0.0
            }

        return Food(
            id = jsonObject.getString("id"),
            name = jsonObject.getString("name"),
            servingDescription = jsonObject.getString("servingDescription"),
            kind = kind,
            servingWeightGrams = servingWeightGrams,
            servingVolumeMilliliters = servingVolumeMilliliters,
            nutritionPerServing = parseNutrition(jsonObject.getJSONObject("nutritionPerServing")),
            servingsPerContainer = jsonObject.optNullableDouble("servingsPerContainer"),
            createdAtEpochMillis = jsonObject.optLong("createdAtEpochMillis"),
            catalog = jsonObject.optJSONObject("catalog")?.let(::decodeCatalog)
        )
    }

    fun parseNutrition(jsonObject: JSONObject): NutritionSnapshot {
        return NutritionSnapshot(
            calories = jsonObject.getDouble("calories"),
            fatGrams = jsonObject.getDouble("fatGrams"),
            carbsGrams = jsonObject.getDouble("carbsGrams"),
            proteinGrams = jsonObject.getDouble("proteinGrams"),
            saturatedFatGrams = jsonObject.optNullableDouble("saturatedFatGrams"),
            fiberGrams = jsonObject.optNullableDouble("fiberGrams"),
            sugarGrams = jsonObject.optNullableDouble("sugarGrams"),
            addedSugarsGrams = jsonObject.optNullableDouble("addedSugarsGrams"),
            sugarAlcoholsGrams = jsonObject.optNullableDouble("sugarAlcoholsGrams"),
            sodiumMilligrams = jsonObject.optNullableDouble("sodiumMilligrams"),
            potassiumMilligrams = jsonObject.optNullableDouble("potassiumMilligrams"),
            cholesterolMilligrams = jsonObject.optNullableDouble("cholesterolMilligrams"),
            transFatGrams = jsonObject.optNullableDouble("transFatGrams"),
            monounsaturatedFatGrams = jsonObject.optNullableDouble("monounsaturatedFatGrams"),
            polyunsaturatedFatGrams = jsonObject.optNullableDouble("polyunsaturatedFatGrams"),
            omega3FattyAcidsGrams = jsonObject.optNullableDouble("omega3FattyAcidsGrams"),
            omega6FattyAcidsGrams = jsonObject.optNullableDouble("omega6FattyAcidsGrams"),
            calciumMilligrams = jsonObject.optNullableDouble("calciumMilligrams"),
            chlorideMilligrams = jsonObject.optNullableDouble("chlorideMilligrams"),
            folateMicrograms = jsonObject.optNullableDouble("folateMicrograms"),
            ironMilligrams = jsonObject.optNullableDouble("ironMilligrams"),
            magnesiumMilligrams = jsonObject.optNullableDouble("magnesiumMilligrams"),
            phosphorusMilligrams = jsonObject.optNullableDouble("phosphorusMilligrams"),
            vitaminAMicrograms = jsonObject.optNullableDouble("vitaminAMicrograms"),
            vitaminB12Micrograms = jsonObject.optNullableDouble("vitaminB12Micrograms"),
            vitaminCMilligrams = jsonObject.optNullableDouble("vitaminCMilligrams"),
            vitaminDMicrograms = jsonObject.optNullableDouble("vitaminDMicrograms"),
            vitaminEMilligrams = jsonObject.optNullableDouble("vitaminEMilligrams"),
            vitaminKMicrograms = jsonObject.optNullableDouble("vitaminKMicrograms"),
            zincMilligrams = jsonObject.optNullableDouble("zincMilligrams")
        )
    }

    fun serializeFood(food: Food): JSONObject {
        return JSONObject()
            .put("id", food.id)
            .put("name", food.name)
            .put("servingDescription", food.servingDescription)
            .put("kind", food.kind.name)
            .put("servingWeightGrams", food.servingWeightGrams)
            .put("servingVolumeMilliliters", food.servingVolumeMilliliters)
            .put("nutritionPerServing", serializeNutrition(food.nutritionPerServing))
            .putNullable("servingsPerContainer", food.servingsPerContainer)
            .put("createdAtEpochMillis", food.createdAtEpochMillis)
            .put("catalog", food.catalog?.let(::encodeCatalog))
    }

    fun serializeFoodTransferPayload(food: Food): JSONObject {
        return JSONObject()
            .put("id", food.id)
            .put("name", food.name)
            .put("servingDescription", food.servingDescription)
            .put("kind", food.kind.name)
            .putNullable(
                "servingWeightGrams",
                food.servingWeightGrams.takeIf { food.kind == FoodKind.FOOD || it != 0.0 }
            )
            .putNullable(
                "servingVolumeMilliliters",
                food.servingVolumeMilliliters.takeIf { food.kind == FoodKind.LIQUID || it != 0.0 }
            )
            .put("nutritionPerServing", serializeNutrition(food.nutritionPerServing))
            .putNullable("servingsPerContainer", food.servingsPerContainer)
            .put("createdAtEpochMillis", food.createdAtEpochMillis)
    }

    fun serializeNutrition(nutritionSnapshot: NutritionSnapshot): JSONObject {
        return JSONObject()
            .put("calories", nutritionSnapshot.calories)
            .put("fatGrams", nutritionSnapshot.fatGrams)
            .put("carbsGrams", nutritionSnapshot.carbsGrams)
            .put("proteinGrams", nutritionSnapshot.proteinGrams)
            .putNullable("saturatedFatGrams", nutritionSnapshot.saturatedFatGrams)
            .putNullable("fiberGrams", nutritionSnapshot.fiberGrams)
            .putNullable("sugarGrams", nutritionSnapshot.sugarGrams)
            .putNullable("addedSugarsGrams", nutritionSnapshot.addedSugarsGrams)
            .putNullable("sugarAlcoholsGrams", nutritionSnapshot.sugarAlcoholsGrams)
            .putNullable("sodiumMilligrams", nutritionSnapshot.sodiumMilligrams)
            .putNullable("potassiumMilligrams", nutritionSnapshot.potassiumMilligrams)
            .putNullable("cholesterolMilligrams", nutritionSnapshot.cholesterolMilligrams)
            .putNullable("transFatGrams", nutritionSnapshot.transFatGrams)
            .putNullable("monounsaturatedFatGrams", nutritionSnapshot.monounsaturatedFatGrams)
            .putNullable("polyunsaturatedFatGrams", nutritionSnapshot.polyunsaturatedFatGrams)
            .putNullable("omega3FattyAcidsGrams", nutritionSnapshot.omega3FattyAcidsGrams)
            .putNullable("omega6FattyAcidsGrams", nutritionSnapshot.omega6FattyAcidsGrams)
            .putNullable("calciumMilligrams", nutritionSnapshot.calciumMilligrams)
            .putNullable("chlorideMilligrams", nutritionSnapshot.chlorideMilligrams)
            .putNullable("folateMicrograms", nutritionSnapshot.folateMicrograms)
            .putNullable("ironMilligrams", nutritionSnapshot.ironMilligrams)
            .putNullable("magnesiumMilligrams", nutritionSnapshot.magnesiumMilligrams)
            .putNullable("phosphorusMilligrams", nutritionSnapshot.phosphorusMilligrams)
            .putNullable("vitaminAMicrograms", nutritionSnapshot.vitaminAMicrograms)
            .putNullable("vitaminB12Micrograms", nutritionSnapshot.vitaminB12Micrograms)
            .putNullable("vitaminCMilligrams", nutritionSnapshot.vitaminCMilligrams)
            .putNullable("vitaminDMicrograms", nutritionSnapshot.vitaminDMicrograms)
            .putNullable("vitaminEMilligrams", nutritionSnapshot.vitaminEMilligrams)
            .putNullable("vitaminKMicrograms", nutritionSnapshot.vitaminKMicrograms)
            .putNullable("zincMilligrams", nutritionSnapshot.zincMilligrams)
    }

    fun encodeCatalog(catalog: CatalogMetadata): JSONObject = JSONObject()
        .put("catalogId", catalog.catalogId)
        .put("revision", catalog.revision)
        .put("publisher", catalog.publisher)
        .put("sourceName", catalog.sourceName)
        .put("sourceUrl", catalog.sourceUrl)
        .put("license", catalog.license)
        .put("regionCode", catalog.regionCode)
        .put("notes", catalog.notes)
        .put("verifiedAtEpochMillis", catalog.verifiedAtEpochMillis)

    fun decodeCatalog(json: JSONObject): CatalogMetadata = CatalogMetadata(
        catalogId = json.getString("catalogId"),
        revision = (json.get("revision") as? Number)?.toInt()?.takeIf {
            it > 0 && json.getDouble("revision") == it.toDouble()
        } ?: error("Invalid catalog revision."),
        publisher = json.getString("publisher"),
        sourceName = json.getString("sourceName"),
        sourceUrl = json.optionalString("sourceUrl"),
        license = json.optionalString("license"),
        regionCode = json.optionalString("regionCode"),
        notes = json.optionalString("notes"),
        verifiedAtEpochMillis = if (json.isNull("verifiedAtEpochMillis")) null else json.getLong("verifiedAtEpochMillis")
    )

    private fun JSONObject.optionalString(key: String): String? =
        if (isNull(key)) null else getString(key)

    private fun JSONObject.putNullable(key: String, value: Double?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key)) null else getDouble(key)
}
