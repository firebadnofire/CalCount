package org.archuser.CalCount.data

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarUtils
import org.archuser.CalCount.data.model.Food
import org.archuser.CalCount.data.model.Recipe
import org.archuser.CalCount.domain.RecipeCalculator
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest

object RecipeArchive {
    const val MAX_COMPRESSED = 2 * 1024 * 1024
    private const val MAX_DECOMPRESSED = 8 * 1024 * 1024
    private const val MAX_LABELS = 256
    private const val BLOCK = 512
    private val labelPath = Regex("labels/[0-9a-f-]{36}\\.ccnl")

    data class Bundle(val recipe: Recipe, val foods: List<Food>)

    fun encode(recipe: Recipe, foods: List<Food>): ByteArray {
        RecipeCalculator.calculate(recipe, foods)
        require(TransferJson.isUuid(recipe.id)) { "Invalid recipe UUID." }
        val ids = recipe.ingredients.map { it.foodId }.distinct()
        require(ids.size <= MAX_LABELS) { "This recipe has too many distinct labels." }
        val foodsById = foods.associateBy(Food::id)
        var entryBytes = 2 * BLOCK
        fun reserve(size: Int) {
            entryBytes += BLOCK + ((size + BLOCK - 1) / BLOCK) * BLOCK
            require(entryBytes <= MAX_DECOMPRESSED) { "Recipe archive exceeds the expanded size limit." }
        }
        val labels = ids.associateWith { LabelCodec.encode(foodsById.getValue(it)).also { bytes -> reserve(bytes.size) } }
        val manifest = RecipeJson.encode(recipe)
            .put("format", "ccr")
            .put("schema_version", 1)
            .put("labels", JSONArray().apply {
                labels.forEach { (id, bytes) -> put(JSONObject().put("id", id).put("sha256", digest(bytes))) }
            }).toString(2).encodeToByteArray()
        require(manifest.size <= LabelCodec.MAX_BYTES) { "Recipe manifest exceeds 64 KiB." }
        reserve(manifest.size)
        val tarBytes = ByteArrayOutputStream(entryBytes)
        TarArchiveOutputStream(tarBytes).use { tar ->
            fun write(path: String, bytes: ByteArray) {
                val entry = TarArchiveEntry(path)
                entry.size = bytes.size.toLong()
                entry.setModTime(0L)
                tar.putArchiveEntry(entry)
                tar.write(bytes)
                tar.closeArchiveEntry()
            }
            write("manifest.json", manifest)
            labels.forEach { (id, bytes) -> write("labels/$id.ccnl", bytes) }
        }
        require(tarBytes.size() <= MAX_DECOMPRESSED) { "Recipe archive exceeds the expanded size limit." }
        val compressed = ByteArrayOutputStream()
        ZstdOutputStream(compressed).use { it.write(tarBytes.toByteArray()) }
        return compressed.toByteArray().also {
            require(it.size <= MAX_COMPRESSED) { "Recipe archive exceeds the compressed size limit." }
        }
    }

    fun decode(bytes: ByteArray): Bundle {
        require(bytes.size in 1..MAX_COMPRESSED && hasMagic(bytes)) { "Invalid CCR compression or size." }
        val expanded = ZstdInputStream(bytes.inputStream()).use {
            it.setLongMax(23)
            it.readBounded(MAX_DECOMPRESSED)
        }
        validateHeaders(expanded)
        val files = linkedMapOf<String, ByteArray>()
        TarArchiveInputStream(expanded.inputStream()).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                require(entry.isCheckSumOK && !entry.isSparse) { "Invalid TAR entry." }
                if (!entry.isDirectory) files[entry.name] = tar.readBounded(LabelCodec.MAX_BYTES)
            }
        }

        val manifest = TransferJson.decode(
            requireNotNull(files.remove("manifest.json")) { "CCR manifest is missing." },
            LabelCodec.MAX_BYTES
        )
        TransferJson.requireVersion(manifest, "ccr")
        val recipe = RecipeJson.decode(manifest)
        require(TransferJson.isUuid(recipe.id)) { "Invalid recipe UUID." }
        val index = manifest.getJSONArray("labels")
        require(index.length() in 1..MAX_LABELS && index.length() == files.size) { "CCR label index does not match the archive." }
        val foods = List(index.length()) { position ->
            val item = index.getJSONObject(position)
            val id = item.get("id") as? String ?: error("Invalid CCR label UUID.")
            require(TransferJson.isUuid(id)) { "Invalid CCR label UUID." }
            val label = files.remove("labels/$id.ccnl") ?: error("Missing or repeated label $id.")
            require(item.get("sha256") == digest(label)) { "Integrity check failed for label $id." }
            LabelCodec.decode(label, allowLegacy = false).also {
                require(it.id == id) { "Label identity does not match the manifest." }
            }
        }
        require(recipe.ingredients.map { it.foodId }.toSet() == foods.map { it.id }.toSet()) {
            "CCR ingredients and indexed labels do not match."
        }
        RecipeCalculator.calculate(recipe, foods)
        return Bundle(recipe, foods)
    }

    fun hasMagic(bytes: ByteArray): Boolean = bytes.size >= 4 &&
        bytes[0] == 0x28.toByte() && bytes[1] == 0xb5.toByte() &&
        bytes[2] == 0x2f.toByte() && bytes[3] == 0xfd.toByte()

    private fun validateHeaders(tar: ByteArray) {
        val names = mutableSetOf<String>()
        var offset = 0
        while (offset + BLOCK <= tar.size) {
            val header = tar.copyOfRange(offset, offset + BLOCK)
            if (header.all { it == 0.toByte() }) {
                require(tar.size - offset >= 2 * BLOCK && (offset until tar.size).all { tar[it] == 0.toByte() }) {
                    "Invalid TAR terminator."
                }
                return
            }

            require(header.copyOfRange(257, 265).contentEquals(("ustar\u0000" + "00").encodeToByteArray())) {
                "CCR requires a USTAR archive."
            }
            require(TarUtils.verifyCheckSum(header)) { "Invalid TAR header checksum." }
            require((345 until 500).all { header[it] == 0.toByte() }) { "Unsupported TAR path prefix." }
            val name = TarUtils.parseName(header, 0, 100)
            val type = header[156].toInt()
            val size = TarUtils.parseOctal(header, 124, 12)
            when (type) {
                0, '0'.code -> require(name == "manifest.json" || labelPath.matches(name)) { "Invalid CCR path: $name." }
                '5'.code -> require(name == "labels/" && size == 0L) { "Invalid CCR directory." }
                else -> error("Unsupported TAR entry type.")
            }
            require(names.add(name) && names.size <= MAX_LABELS + 2) { "Duplicate or excessive CCR entries." }
            require(size in 0..LabelCodec.MAX_BYTES.toLong()) { "CCR entry exceeds the size limit." }
            offset += BLOCK + ((size.toInt() + BLOCK - 1) / BLOCK) * BLOCK
        }
        error("CCR archive has no complete TAR terminator.")
    }
}

internal fun digest(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun InputStream.readBounded(limit: Int): ByteArray {
    val result = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val read = read(buffer)
        if (read == -1) return result.toByteArray()
        require(read <= limit - result.size()) { "Transfer exceeds the supported size limit." }
        result.write(buffer, 0, read)
    }
}
