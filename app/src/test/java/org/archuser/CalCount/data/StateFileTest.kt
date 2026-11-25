package org.archuser.CalCount.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StateFileTest {
    @get:Rule val folder = TemporaryFolder()

    private fun decode(raw: String): Int = raw.toInt()

    @Test fun readsLegacyAndRecoversPreviousAfterMalformedSave() {
        val store = StateFile(folder.root)
        assertEquals(1, store.load({ "1" }, ::decode)?.value)
        store.save("2", "1")
        assertEquals(2, store.load({ "1" }, ::decode)?.value)
        folder.newFile("app-state.json.pending").writeText("3")
        assertEquals(2, store.load({ "1" }, ::decode)?.value)
        File(folder.root, "app-state.json").writeText("malformed")
        val recovered = store.load({ "0" }, ::decode)
        assertEquals(1, recovered?.value)
        assertTrue(recovered?.recovered == true)
    }

    @Test fun failedReplacementLeavesCurrentAndPreviousReadable() {
        val store = StateFile(folder.root)
        store.save("2", "1")
        folder.newFolder("app-state.json.pending")
        assertThrows(java.io.IOException::class.java) { store.save("3", "2") }
        assertEquals(2, store.load({ null }, ::decode)?.value)
        File(folder.root, "app-state.json").writeText("malformed")
        assertEquals(2, store.load({ null }, ::decode)?.value)
    }

    @Test fun invalidUtf8RecoversThePreviousCopy() {
        val store = StateFile(folder.root)
        store.save("new", "previous")
        File(folder.root, "app-state.json").writeBytes(byteArrayOf(0xc3.toByte(), 0x28))
        val result = store.load({ null }) { it }
        assertEquals("previous", result?.value)
        assertTrue(result?.recovered == true)
    }

    @Test fun malformedCurrentAndBackupSurfaceFailure() {
        folder.newFile("app-state.json").writeText("malformed")
        folder.newFile("app-state.previous.json").writeText("malformed")
        assertThrows(NumberFormatException::class.java) {
            StateFile(folder.root).load({ "1" }, ::decode)
        }
    }
}
