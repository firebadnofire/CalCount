package org.archuser.CalCount.data

import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.FoodKind
import org.archuser.CalCount.data.model.NutritionSnapshot
import org.json.JSONObject
import java.util.UUID

object LabelCodec {
    const val MAX_BYTES = 64 * 1024

    fun encode(food: Food): ByteArray {
        require(TransferJson.isUuid(food.id)) { "A nutritional label requires a valid UUID." }
        return JSONObject()
            .put("format", "ccnl")
            .put("schema_version", 1)
            .put("food", FoodJson.serializeFoodTransferPayload(food))
            .put("catalog", food.catalog?.let(FoodJson::encodeCatalog))
            .toString(2).encodeToByteArray().also { decode(it, allowLegacy = false) }
    }

    fun decode(bytes: ByteArray, allowLegacy: Boolean = true): Food {
        val root = TransferJson.decode(bytes, MAX_BYTES)
        val canonical = root.has("format")
        val value = when {
            canonical -> {
                TransferJson.requireVersion(root, "ccnl")
                root.getJSONObject("food")
            }
            !allowLegacy -> error("Bundled labels must be CCNL version 1.")
            root.has("food") -> {
                require(root.get("type") == "food") { "This JSON does not contain a food export." }
                val version = if (root.has("version")) root.get("version") else 1
                require(version is Number && version.toDouble() == 1.0) { "Unsupported legacy label version: $version." }
                root.getJSONObject("food")
            }
            else -> root
        }
        val originalId = requireText(value, "id")
        requireText(value, "name")
        requireText(value, "servingDescription")
        val kind = if (!canonical && !value.has("kind")) FoodKind.FOOD else FoodKind.valueOf(requireText(value, "kind"))
        validateNumber(value, "servingWeightGrams", kind == FoodKind.FOOD, positive = kind == FoodKind.FOOD)
        validateNumber(value, "servingVolumeMilliliters", kind == FoodKind.LIQUID, positive = kind == FoodKind.LIQUID)
        validateNumber(value, "servingsPerContainer", required = false, positive = true)
        val nutrition = value.getJSONObject("nutritionPerServing")
        val requiredNutrients = setOf("calories", "fatGrams", "carbsGrams", "proteinGrams")
        val supported = FoodJson.serializeNutrition(NutritionSnapshot.ZERO).keys()
        supported.forEach { validateNumber(nutrition, it, required = it in requiredNutrients) }
        require(!canonical || TransferJson.isUuid(originalId)) { "Invalid CCNL label UUID." }

        validateTimestamp(value, "createdAtEpochMillis")
        val catalog = if (root.isNull("catalog")) null else root.getJSONObject("catalog").let { metadata ->
            listOf("catalogId", "publisher", "sourceName").forEach { requireText(metadata, it) }
            listOf("sourceUrl", "license", "regionCode", "notes").forEach {
                require(metadata.isNull(it) || metadata.get(it) is String) { "Invalid catalog field: $it." }
            }
            validateTimestamp(metadata, "verifiedAtEpochMillis")
            FoodJson.decodeCatalog(metadata)
        }
        val normalizedId = originalId.lowercase().takeIf(TransferJson::isUuid)
        return FoodJson.parseFood(value).copy(
            id = normalizedId ?: UUID.randomUUID().toString(),
            catalog = catalog
        )
    }

    internal fun Food.identityContent(): Food =
        copy(createdAtEpochMillis = 0, catalog = catalog?.copy(verifiedAtEpochMillis = null))

    private fun requireText(json: JSONObject, key: String): String =
        (json.get(key) as? String)?.takeIf(String::isNotBlank) ?: error("Invalid label field: $key.")

    private fun validateTimestamp(json: JSONObject, key: String) {
        if (json.isNull(key)) return
        val timestamp = json.get(key)
        require((timestamp is Int || timestamp is Long) && (timestamp as Number).toLong() >= 0) {
            "Invalid label timestamp: $key."
        }
    }

    private fun validateNumber(json: JSONObject, key: String, required: Boolean, positive: Boolean = false) {
        if (!required && json.isNull(key)) return
        val value = (json.get(key) as? Number)?.toDouble()
        require(value != null && value.isFinite() && (if (positive) value > 0 else value >= 0)) {
            "Invalid numeric label field: $key."
        }
    }
}
