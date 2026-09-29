package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.InstrumentDto
import dev.ironjanowar.fretboard.core.InstrumentStateDto
import dev.ironjanowar.fretboard.core.KeyRowDto
import dev.ironjanowar.fretboard.core.KeySuggestionDto
import dev.ironjanowar.fretboard.core.MultiKeyGroupDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.TabDto
import dev.ironjanowar.fretboard.core.TuningDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The keys panel's asynchronous lifecycle, as a pure state machine over a
 * scripted engine.
 *
 * A16's guarantees are about *timing*, which is exactly what a JVM test can pin
 * with virtual time: the request leaves the caller's thread, only a chord change
 * re-asks, and a late answer — success or failure — can never replace a newer
 * one. The engine is scripted, so every row and every score below is the
 * scripted answer; what is under test is the coordinator's own request,
 * supersession and rejection behaviour, never a musical value.
 *
 * The contract is stated where it matters: `Job.cancel()` stops *this* client
 * waiting and is not evidence that the native calculation stopped, so the
 * generation token — not the cancellation — is what keeps a stale answer out.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EvaluationCoordinatorTest {

    // ------------------------------------------------------------ fixtures

    private fun page(chords: List<ChordDto>, tab: TabDto = TabDto.VISUALIZER, highlight: ChordDto? = null) =
        PageStateDto(
            instrument = InstrumentStateDto.Fretted(
                instrument = InstrumentDto.GUITAR,
                tuning = TuningDto(pitches = byteArrayOf(40, 45, 50, 55, 59, 64), reference = "Standard"),
                selected = emptyList(),
            ),
            chords = chords,
            highlight = highlight,
            tab = tab,
        )

    private val c = ChordDto("C", "major")
    private val am = ChordDto("A", "minor")
    private val g = ChordDto("G", "major")
    private val twoChords = page(listOf(c, am))
    private val otherChords = page(listOf(c, g))
    private val singleChord = page(listOf(c))
    private val noChords = page(emptyList())

    private fun single(tonic: String, scale: String, score: ULong = 2u, total: ULong = 2u) =
        KeyRowDto.Single(KeySuggestionDto(tonic, scale, score, total))

    private fun group(tonic: String, scale: String) = KeyRowDto.Group(
        prominent = listOf(KeySuggestionDto(tonic, scale, 2u, 2u)),
        others = listOf(KeySuggestionDto(tonic, scale, 2u, 2u)),
    )

    /**
     * A scripted suggestion engine, immediate or parked.
     *
     * With [hold] on, each call parks on its own gate until the test releases it,
     * which is what lets a test deliver B *after* A. The parked await runs in
     * [NonCancellable], so the test can complete a gate whose request was already
     * superseded: the completion really does arrive, and only the coordinator's
     * generation token can keep it out of the panel.
     */
    private class ScriptedSuggestions : KeySuggestionEngine {
        var keyRows: List<KeyRowDto> = emptyList()
        var multiGroups: List<MultiKeyGroupDto> = emptyList()
        var keyFailure: Throwable? = null
        var multiFailure: Throwable? = null
        var hold: Boolean = false

        val keyCalls = mutableListOf<PageStateDto>()
        val multiCalls = mutableListOf<PageStateDto>()
        private val keyGates = mutableListOf<CompletableDeferred<List<KeyRowDto>>>()
        private val multiGates = mutableListOf<CompletableDeferred<List<MultiKeyGroupDto>>>()

        override suspend fun keySuggestions(state: PageStateDto): List<KeyRowDto> {
            keyCalls += state
            if (!hold) {
                keyFailure?.let { failure -> throw failure }
                return keyRows
            }
            val gate = CompletableDeferred<List<KeyRowDto>>()
            keyGates += gate
            return withContext(NonCancellable) { gate.await() }
        }

        override suspend fun multiKeySuggestions(state: PageStateDto): List<MultiKeyGroupDto> {
            multiCalls += state
            if (!hold) {
                multiFailure?.let { failure -> throw failure }
                return multiGroups
            }
            val gate = CompletableDeferred<List<MultiKeyGroupDto>>()
            multiGates += gate
            return withContext(NonCancellable) { gate.await() }
        }

        fun releaseKey(index: Int, rows: List<KeyRowDto>) {
            keyGates[index].complete(rows)
        }

        fun failKey(index: Int, error: Throwable) {
            keyGates[index].completeExceptionally(error)
        }

        fun releaseMulti(index: Int, groups: List<MultiKeyGroupDto>) {
            multiGates[index].complete(groups)
        }

        fun keyGateCount(): Int = keyGates.size
    }

    // ------------------------------------------------------- request timing

    @Test
    fun `the request leaves the caller's thread and reports pending first`() = runTest {
        val engine = ScriptedSuggestions().apply { keyRows = listOf(single("C", "major")) }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords)

        // Synchronously pending, and the engine has not been called yet: the
        // request was dispatched, not run inline on the caller's thread.
        assertEquals(EvaluationState.Pending, coordinator.keys)
        assertTrue("the engine is not called inline", engine.keyCalls.isEmpty())

        advanceUntilIdle()

        assertEquals(EvaluationState.Answer(listOf(single("C", "major"))), coordinator.keys)
        assertEquals(1, engine.keyCalls.size)
    }

    @Test
    fun `a tab or highlight change does not recalculate`() = runTest {
        val engine = ScriptedSuggestions().apply { keyRows = listOf(single("C", "major")) }
        val coordinator = EvaluationCoordinator(engine, this)
        coordinator.onPage(twoChords)
        advanceUntilIdle()
        val answered = coordinator.keys

        // Same chords, different tab and highlight: the input did not change, so
        // nothing is asked and the answer stays exactly as it was.
        coordinator.onPage(page(listOf(c, am), tab = TabDto.ANALYZER))
        coordinator.onPage(page(listOf(c, am), highlight = c))
        advanceUntilIdle()

        assertEquals("the engine is asked once", 1, engine.keyCalls.size)
        assertSame("the same answer, untouched", answered, coordinator.keys)
    }

    @Test
    fun `a chord change recalculates`() = runTest {
        val engine = ScriptedSuggestions().apply { keyRows = listOf(single("C", "major")) }
        val coordinator = EvaluationCoordinator(engine, this)
        coordinator.onPage(twoChords)
        advanceUntilIdle()

        engine.keyRows = listOf(single("G", "major"))
        coordinator.onPage(otherChords)
        advanceUntilIdle()

        assertEquals("a chord change is a new question", 2, engine.keyCalls.size)
        assertEquals(EvaluationState.Answer(listOf(single("G", "major"))), coordinator.keys)
    }

    // ------------------------------------------------------------ the gate

    @Test
    fun `below two chords the panel is absent and the engine is never asked`() = runTest {
        val engine = ScriptedSuggestions().apply { keyRows = listOf(single("C", "major")) }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(singleChord)

        assertEquals(EvaluationState.Absent, coordinator.keys)
        assertEquals(EvaluationState.Absent, coordinator.multiKeys)
        advanceUntilIdle()
        assertTrue("the engine's own gate means no call at all", engine.keyCalls.isEmpty())
    }

    @Test
    fun `two chords cross the gate and ask the engine`() = runTest {
        val engine = ScriptedSuggestions().apply { keyRows = listOf(single("C", "major")) }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords)
        advanceUntilIdle()

        assertEquals(1, engine.keyCalls.size)
    }

    // --------------------------------------------------- stale rejection

    @Test
    fun `clearing the chords while busy leaves no stale panel`() = runTest {
        val engine = ScriptedSuggestions().apply { hold = true }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords)
        advanceUntilIdle()
        assertEquals(EvaluationState.Pending, coordinator.keys)
        assertEquals(1, engine.keyGateCount())

        // The user clears the chords while the calculation is still running.
        coordinator.onPage(noChords)
        assertEquals(EvaluationState.Absent, coordinator.keys)

        // The abandoned calculation finishes late; it must not resurrect a panel.
        engine.releaseKey(0, listOf(single("C", "major")))
        advanceUntilIdle()

        assertEquals(EvaluationState.Absent, coordinator.keys)
        assertEquals(EvaluationState.Absent, coordinator.multiKeys)
    }

    @Test
    fun `a late success cannot replace a newer answer`() = runTest {
        val engine = ScriptedSuggestions().apply { hold = true }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords) // A
        advanceUntilIdle()
        coordinator.onPage(otherChords) // B
        advanceUntilIdle()
        assertEquals(2, engine.keyGateCount())

        // B lands first; A — whose request was superseded — finishes afterwards.
        engine.releaseKey(1, listOf(single("G", "major")))
        advanceUntilIdle()
        engine.releaseKey(0, listOf(single("C", "major")))
        advanceUntilIdle()

        assertEquals(
            "the newest answer is the one on screen",
            EvaluationState.Answer(listOf(single("G", "major"))),
            coordinator.keys,
        )
    }

    @Test
    fun `a late failure cannot replace a newer answer`() = runTest {
        val engine = ScriptedSuggestions().apply { hold = true }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords) // A
        advanceUntilIdle()
        coordinator.onPage(otherChords) // B
        advanceUntilIdle()

        engine.releaseKey(1, listOf(single("G", "major")))
        advanceUntilIdle()
        // The superseded request fails late; a refusal must not overwrite a newer
        // answer any more than a success may.
        engine.failKey(0, IllegalStateException("the engine gave up on the abandoned request"))
        advanceUntilIdle()

        assertEquals(EvaluationState.Answer(listOf(single("G", "major"))), coordinator.keys)
    }

    @Test
    fun `the newest failure is shown with the engine's own sentence`() = runTest {
        val engine = ScriptedSuggestions().apply { keyFailure = IllegalStateException("the engine is unreachable") }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords)
        advanceUntilIdle()

        val failed = coordinator.keys as EvaluationState.Failed
        assertTrue("the engine's own sentence: ${failed.reason}", failed.reason.contains("the engine is unreachable"))
        assertEquals("no multi-key panel under a failure", EvaluationState.Absent, coordinator.multiKeys)
    }

    @Test
    fun `invalidate rejects an answer that was already in flight`() = runTest {
        val engine = ScriptedSuggestions().apply { hold = true }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords)
        advanceUntilIdle()

        // A disposed session: nothing in flight may land afterwards.
        coordinator.invalidate()
        engine.releaseKey(0, listOf(single("C", "major")))
        advanceUntilIdle()

        assertEquals(EvaluationState.Absent, coordinator.keys)
        assertEquals(EvaluationState.Absent, coordinator.multiKeys)
        assertEquals(false, coordinator.expanded)
    }

    // ------------------------------------------------------- multi-key

    @Test
    fun `the multi-key panel appears only when the single-key rows are empty`() = runTest {
        val engine = ScriptedSuggestions().apply {
            keyRows = emptyList()
            multiGroups = listOf(MultiKeyGroupDto(key = null, chords = listOf(c, g)))
        }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords)
        advanceUntilIdle()

        assertEquals(EvaluationState.Answer(emptyList<KeyRowDto>()), coordinator.keys)
        assertEquals(1, engine.multiCalls.size)
        assertEquals(EvaluationState.Answer(engine.multiGroups), coordinator.multiKeys)
    }

    @Test
    fun `non-empty key rows leave the multi-key panel absent and unasked`() = runTest {
        val engine = ScriptedSuggestions().apply { keyRows = listOf(single("C", "major")) }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords)
        advanceUntilIdle()

        assertTrue("the engine's own rule: no groups when rows exist", engine.multiCalls.isEmpty())
        assertEquals(EvaluationState.Absent, coordinator.multiKeys)
    }

    @Test
    fun `the multi-key groups keep their full membership and a keyless group`() = runTest {
        val keyed = MultiKeyGroupDto(
            key = KeySuggestionDto("C", "major", 3u, 4u),
            chords = listOf(c, am, g, c),
        )
        val keyless = MultiKeyGroupDto(key = null, chords = listOf(ChordDto("C", "7#9")))
        val engine = ScriptedSuggestions().apply {
            keyRows = emptyList()
            multiGroups = listOf(keyed, keyless)
        }
        val coordinator = EvaluationCoordinator(engine, this)

        coordinator.onPage(twoChords)
        advanceUntilIdle()

        assertEquals(
            "every group, every member and the engine's own order, unchanged",
            EvaluationState.Answer(listOf(keyed, keyless)),
            coordinator.multiKeys,
        )
        val shown = (coordinator.multiKeys as EvaluationState.Answer).value
        assertEquals("a repeated member is not collapsed", 4, shown.first().chords.size)
        assertEquals("the keyless group is carried through", null, shown[1].key)
    }

    // -------------------------------------------------------- expansion

    @Test
    fun `expansion is shared across groups and reset by a committed chord change`() = runTest {
        val engine = ScriptedSuggestions().apply { keyRows = listOf(group("C", "major")) }
        val coordinator = EvaluationCoordinator(engine, this)
        coordinator.onPage(twoChords)
        advanceUntilIdle()

        coordinator.toggleExpanded()
        assertEquals(true, coordinator.expanded)

        // A tab change is not a committed change and keeps the expansion.
        coordinator.onPage(page(listOf(c, am), tab = TabDto.ANALYZER))
        assertEquals(true, coordinator.expanded)

        // A committed chord change resets it.
        coordinator.onPage(otherChords)
        assertEquals("a committed change resets the expansion", false, coordinator.expanded)
    }

    // ------------------------------------------------------------ retry

    @Test
    fun `retry asks the engine again for the same chords`() = runTest {
        val engine = ScriptedSuggestions().apply { keyFailure = IllegalStateException("no") }
        val coordinator = EvaluationCoordinator(engine, this)
        coordinator.onPage(twoChords)
        advanceUntilIdle()
        assertTrue(coordinator.keys is EvaluationState.Failed)

        engine.keyFailure = null
        engine.keyRows = listOf(single("C", "major"))
        coordinator.retry(twoChords)
        advanceUntilIdle()

        assertEquals(2, engine.keyCalls.size)
        assertEquals(EvaluationState.Answer(listOf(single("C", "major"))), coordinator.keys)
    }

    // ------------------------------------------------------- lifecycle

    @Test
    fun `repeated calls and the close lifecycle do not crash`() = runTest {
        val engine = ScriptedSuggestions().apply { keyRows = listOf(single("C", "major")) }
        val coordinator = EvaluationCoordinator(engine, this)

        repeat(5) {
            coordinator.onPage(twoChords)
            advanceUntilIdle()
            coordinator.onPage(noChords)
            advanceUntilIdle()
            coordinator.toggleExpanded()
        }
        coordinator.invalidate()
        coordinator.invalidate()
        advanceUntilIdle()

        assertEquals(EvaluationState.Absent, coordinator.keys)
        assertEquals("every request the engine saw was asked for", 5, engine.keyCalls.size)
    }
}
