package dev.ironjanowar.fretboard.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.defaultState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The last session against the pinned engine and the real file system (A17).
 *
 * The JVM suite (`app/src/test/.../storage/SessionStoreTest.kt`) pins the store's own
 * rules against a *scripted* codec, because the arm64 engine cannot be loaded on the
 * host those tests run on. This is the other half, on the machine where the library
 * does load: the real `encodeSnapshot`/`decodeSnapshot` of the pinned artifact, the
 * real container bytes on disk, and the engine's own envelope coming back identically.
 *
 * The pages are compared through the engine's canonical envelope, never with `==`:
 * a page carries its tuning and marks as `ByteArray`s, so two pages that are the same
 * music are two different Kotlin values. The envelope is what the engine says about
 * them, and it is what identity means here.
 *
 * **It has not been run.** The build environment has no device and no emulator
 * (`/dev/kvm` is absent), so this test is written, compiled and left for a machine
 * with a device — the same standing as `RotationStateTest` and `KeyProgressionUiTest`.
 */
@RunWith(AndroidJUnit4::class)
class LastSessionDeviceTest {

    private val directory: File
        get() = File(
            ApplicationProvider.getApplicationContext<Context>().filesDir,
            "last-session-device-test",
        )

    @Test
    fun `the pinned engine's snapshot round-trips through the store`() = runBlocking {
        directory.mkdirs()
        File(directory, LAST_SESSION_FILE).delete()
        File(directory, REJECTED_SESSION_FILE).delete()
        val store = lastSessionStore(directory)
        val page = defaultState()

        assertEquals(StoreOutcome.Written, store.write(1, page))

        val stored = store.read() as? StoredSession.Restored
        assertNotNull("the session the engine just wrote is readable", stored)
        assertEquals(
            "the engine's own envelope is carried verbatim",
            BindingSnapshotCodec.encode(page),
            stored!!.envelope,
        )
        assertEquals(
            "and it decodes back to that same envelope: nothing is lost on the way",
            stored.envelope,
            BindingSnapshotCodec.encode(stored.page),
        )
        assertEquals("the tab survives the round trip", page.tab, stored.page.tab)
        assertEquals(
            "so does the fretted tuning, read as its own reference",
            (page.instrument as InstrumentStateDto.Fretted).tuning.reference,
            (stored.page.instrument as InstrumentStateDto.Fretted).tuning.reference,
        )

        assertEquals(
            "storing the same session again is not a disk write",
            StoreOutcome.Unchanged(1),
            store.write(2, page),
        )
        assertEquals(
            "a session the engine wrote survives the read that follows it",
            stored.envelope,
            (store.read() as StoredSession.Restored).envelope,
        )

        File(directory, LAST_SESSION_FILE).delete()
    }
}
