package org.archuser.CalCount.data

import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.json.JSONObject
import java.io.StringReader
import java.util.UUID

internal object TransferJson {
    fun decode(bytes: ByteArray, limit: Int): JSONObject {
        require(bytes.size in 1..limit) { "JSON size exceeds the supported limit." }
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        JsonReader(StringReader(text)).use { reader ->
            reader.strictness = Strictness.STRICT
            require(reader.peek() == JsonToken.BEGIN_OBJECT) { "Expected a JSON object." }
            validate(reader, 0)
            require(reader.peek() == JsonToken.END_DOCUMENT) { "Unexpected data after the JSON object." }
        }
        return JSONObject(text)
    }

    private fun validate(reader: JsonReader, depth: Int) {
        require(depth <= 24) { "JSON nesting exceeds the supported limit." }
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> {
                val names = mutableSetOf<String>()
                reader.beginObject()
                while (reader.hasNext()) {
                    require(names.add(reader.nextName())) { "Duplicate JSON field." }
                    validate(reader, depth + 1)
                }
                reader.endObject()
            }
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                while (reader.hasNext()) validate(reader, depth + 1)
                reader.endArray()
            }
            JsonToken.NUMBER, JsonToken.STRING -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> reader.nextNull()
            else -> error("Invalid JSON value.")
        }
    }

    fun requireVersion(json: JSONObject, format: String) {
        require(json.get("format") == format) { "Expected ${format.uppercase()} data." }
        val version = json.get("schema_version")
        require(version is Number && version.toDouble() == 1.0) { "Unsupported ${format.uppercase()} version: $version." }
    }

    fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}
