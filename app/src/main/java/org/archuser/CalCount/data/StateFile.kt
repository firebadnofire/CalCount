package org.archuser.CalCount.data

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal class StateFile(directory: File) {
    private val current = File(directory, "app-state.json")
    private val previous = File(directory, "app-state.previous.json")

    fun <T> load(legacy: () -> String?, decode: (String) -> T): Loaded<T>? {
        var failure: Exception? = null
        for (file in listOf(current, previous)) {
            if (!file.exists()) continue
            try {
                val raw = readUtf8(file)
                return Loaded(raw, decode(raw), recovered = file == previous)
            } catch (error: Exception) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
        val raw = legacy() ?: return null
        return Loaded(raw, decode(raw), recovered = false)
    }

    fun save(raw: String, lastValid: String?) {
        if (lastValid != null) replace(previous, lastValid)
        replace(current, raw)
    }

    private fun replace(target: File, contents: String) {
        val pending = File(target.parentFile, "${target.name}.pending")
        FileOutputStream(pending).use { stream ->
            stream.write(contents.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        check(readUtf8(pending) == contents) { "The saved data could not be verified." }
        Files.move(pending.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun readUtf8(file: File): String = file.readBytes().decodeToString(throwOnInvalidSequence = true)

    data class Loaded<T>(val raw: String, val value: T, val recovered: Boolean)
}
