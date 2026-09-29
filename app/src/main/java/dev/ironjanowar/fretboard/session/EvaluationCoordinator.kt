package dev.ironjanowar.fretboard.session

import dev.ironjanowar.fretboard.core.ChordDto
import dev.ironjanowar.fretboard.core.KeyRowDto
import dev.ironjanowar.fretboard.core.MultiKeyGroupDto
import dev.ironjanowar.fretboard.core.PageStateDto
import dev.ironjanowar.fretboard.core.keySuggestions
import dev.ironjanowar.fretboard.core.multiKeySuggestions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The engine operations the keys panel needs.
 *
 * A16 asks the suggestions to be computed off the main thread and keyed on the
 * chord input alone. Both calls are the engine's own page operations and answer
 * the rows the panel renders, already gated, scored, grouped and ordered; the
 * port exists so the asynchronous lifecycle can be pinned on the JVM, where the
 * arm64 native library cannot be loaded. The production implementation is
 * [BindingKeySuggestionEngine] and computes nothing.
 */
interface KeySuggestionEngine {
    /** The page's key rows: gated, scored, grouped and ordered by the engine. */
    suspend fun keySuggestions(state: PageStateDto): List<KeyRowDto>

    /** The page's multi-key groups, each with its full displayed membership. */
    suspend fun multiKeySuggestions(state: PageStateDto): List<MultiKeyGroupDto>
}

/**
 * The production port: the pinned bindings, called off the main thread.
 *
 * The two calls are synchronous native entry points, so they are moved to the
 * default dispatcher here; nothing about the rows is decided in Kotlin.
 */
object BindingKeySuggestionEngine : KeySuggestionEngine {

    override suspend fun keySuggestions(state: PageStateDto): List<KeyRowDto> =
        withContext(Dispatchers.Default) { keySuggestions(state) }

    override suspend fun multiKeySuggestions(state: PageStateDto): List<MultiKeyGroupDto> =
        withContext(Dispatchers.Default) { multiKeySuggestions(state) }
}

/**
 * The engine's answer slot of the keys panel, or why there is none.
 *
 * Four different things, kept apart (A16): `Absent` is the panel not applying at
 * all — fewer than two chords, which is the engine's own gate — `Pending` is a
 * calculation in flight, `Answer` is the engine's typed result (an empty list
 * included, which the panel shows as the engine's own *none*) and `Failed` is a
 * refused or unreachable call, which the panel shows as a failure with Retry.
 * None of them is turned into another.
 */
sealed interface EvaluationState<out T> {
    /** The panel does not apply: the engine's two-chord gate answered nothing. */
    data object Absent : EvaluationState<Nothing>

    /** A calculation is in flight. */
    data object Pending : EvaluationState<Nothing>

    /** The engine's typed answer, which may be empty. */
    data class Answer<T>(val value: T) : EvaluationState<T>

    /** The engine refused the call, or could not be reached for it. */
    data class Failed(val reason: String) : EvaluationState<Nothing>
}

/** The keys panel's state, as one value the composition can read. */
data class EvaluationUiState(
    val keys: EvaluationState<List<KeyRowDto>> = EvaluationState.Absent,
    val multiKeys: EvaluationState<List<MultiKeyGroupDto>> = EvaluationState.Absent,
    val expanded: Boolean = false,
)

/**
 * The keys panel's asynchronous lifecycle: request, supersede, reject, retry.
 *
 * A16's rules are all here:
 *
 * * the request is keyed on the chord input alone — [onPage] compares the page's
 *   chords with the ones the current answers belong to, so a tab, highlight or
 *   layout change sends nothing and a chord change sends a new request;
 * * every request carries a generation token and a completion re-checks it
 *   before publishing, so a *late* answer — success or failure — can never
 *   replace a newer one, and clearing the chords while a calculation is in
 *   flight leaves no stale panel;
 * * [invalidate] bumps the token for a disposed session or a dismissed draft;
 * * [Job.cancel] is best-effort: it stops *this* client waiting, not the native
 *   work, so nothing here claims the engine was interrupted — stale answers are
 *   dropped by the token, whether or not the cancellation took;
 * * the two engine calls run on the port's own dispatcher, so the caller's
 *   thread is never blocked and the main thread never computes a suggestion.
 */
class EvaluationCoordinator(
    private val engine: KeySuggestionEngine,
    private val scope: CoroutineScope,
) {

    /** The engine's key rows, or why there are none. */
    var keys: EvaluationState<List<KeyRowDto>> = EvaluationState.Absent
        private set

    /** The engine's multi-key groups, or why there are none. */
    var multiKeys: EvaluationState<List<MultiKeyGroupDto>> = EvaluationState.Absent
        private set

    /** Whether the collapsed groups' other modes are shown. Shared across groups. */
    var expanded: Boolean = false
        private set

    /** Called after every transition, so the composition can mirror the panel. */
    var onState: (() -> Unit)? = null

    /** The chord input the current answers belong to; `null` before the first page. */
    private var input: List<ChordDto>? = null

    /** The generation of the newest request. Only it may publish. */
    private var generation: Long = 0

    private val inFlight: MutableList<Job> = mutableListOf()

    /**
     * Evaluate the page's suggestions — but only when its chord input changed.
     *
     * This is the A16 key: the request depends on the chords and nothing else, so
     * a tab switch or a highlight tap leaves the panel exactly as it is, while an
     * added, removed or cleared chord asks the engine again.
     */
    fun onPage(state: PageStateDto) {
        if (input == state.chords) return
        input = state.chords
        start(state)
    }

    /** Ask the engine again for the current input. The panel's Retry action. */
    fun retry(state: PageStateDto) {
        input = state.chords
        start(state)
    }

    /** Show or hide the collapsed groups' other modes. Shared across groups. */
    fun toggleExpanded() {
        expanded = !expanded
        onState?.invoke()
    }

    /**
     * Nothing in flight may land: a disposed session, a dismissed draft or a
     * replaced page.
     *
     * The generation is bumped so every outstanding completion is rejected, the
     * waiters are cancelled as best effort and the panel goes back to absent.
     */
    fun invalidate() {
        generation += 1
        cancelInFlight()
        input = null
        keys = EvaluationState.Absent
        multiKeys = EvaluationState.Absent
        expanded = false
        onState?.invoke()
    }

    /** The panel's current value, as one snapshot. */
    fun snapshot(): EvaluationUiState =
        EvaluationUiState(keys = keys, multiKeys = multiKeys, expanded = expanded)

    private fun start(state: PageStateDto) {
        // A committed chord change is the one thing that resets the expansion.
        generation += 1
        val token = generation
        cancelInFlight()
        expanded = false

        if (state.chords.size < TWO_CHORD_GATE) {
            // The engine's own gate already answers no rows; the panel says so
            // rather than asking a question whose answer is known to be empty.
            keys = EvaluationState.Absent
            multiKeys = EvaluationState.Absent
            onState?.invoke()
            return
        }

        keys = EvaluationState.Pending
        multiKeys = EvaluationState.Pending
        onState?.invoke()

        inFlight += scope.launch {
            try {
                val rows = engine.keySuggestions(state)
                if (token != generation) return@launch
                keys = EvaluationState.Answer(rows)
            } catch (failure: Throwable) {
                // A cancellation is not an engine failure: the waiter was
                // superseded or the scope went away. Never dress it up as one.
                if (failure is CancellationException) return@launch
                if (token != generation) return@launch
                keys = EvaluationState.Failed(engineFailure(failure))
                multiKeys = EvaluationState.Absent
                onState?.invoke()
                return@launch
            }

            // The multi-key panel exists only when the single-key operation
            // answered nothing, which is the engine's own page rule.
            val rows = (keys as? EvaluationState.Answer)?.value.orEmpty()
            if (rows.isNotEmpty()) {
                multiKeys = EvaluationState.Absent
                onState?.invoke()
                return@launch
            }

            try {
                val groups = engine.multiKeySuggestions(state)
                if (token != generation) return@launch
                multiKeys = EvaluationState.Answer(groups)
            } catch (failure: Throwable) {
                if (failure is CancellationException) return@launch
                if (token != generation) return@launch
                multiKeys = EvaluationState.Failed(engineFailure(failure))
            }
            onState?.invoke()
        }
    }

    /**
     * Cancel the outstanding waiters.
     *
     * Deliberately not evidence about the native work: the engine's calculations
     * may run to completion anyway, so the generation token — not this call — is
     * what keeps a late answer out of the panel.
     */
    private fun cancelInFlight() {
        inFlight.forEach { job -> job.cancel() }
        inFlight.clear()
    }

    private companion object {
        /** The engine's own page gate: fewer than two chords answer no rows. */
        const val TWO_CHORD_GATE = 2
    }
}
