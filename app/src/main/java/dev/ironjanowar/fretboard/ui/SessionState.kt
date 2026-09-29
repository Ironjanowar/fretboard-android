package dev.ironjanowar.fretboard.ui

import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.session.BindingSessionEngine
import dev.ironjanowar.fretboard.session.SessionEngine
import dev.ironjanowar.fretboard.session.SessionLoad
import dev.ironjanowar.fretboard.session.SessionView
import dev.ironjanowar.fretboard.session.TuningDraft
import dev.ironjanowar.fretboard.session.engineFailure
import dev.ironjanowar.fretboard.session.instrumentId
import dev.ironjanowar.fretboard.ui.tuning.PresetPickerState
import dev.ironjanowar.fretboard.ui.tuning.presetPicker
import dev.ironjanowar.fretboard.ui.tuning.presetPickerRefusal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The whole session, as one value.
 *
 * Every field is either an engine answer or a UI choice:
 *
 * * [view] is the committed page the engine answered, with its surfaces,
 *   cards, chord details and analysis; it is never assembled by the client;
 * * [error] is the engine's own sentence for a refused action, shown beside the
 *   session rather than in place of it;
 * * [busy] says an engine call is in flight;
 * * [draft] is the tuning draft the engine opened for [view], with the engine's
 *   own reading of it — a draft edit never touches [view];
 * * [presets] is the engine's own ordered preset catalog for the draft's
 *   instrument, or the engine's refusal;
 * * [root] and [quality] are the two chord-editor choices, which are wire tokens
 *   and a catalog id, not musical values the client computed.
 *
 * It is a plain value type on purpose. It holds no Android and no Compose type,
 * so the transitions below are testable on the JVM, and it survives a
 * configuration change for exactly the reason it is not kept by a composable:
 * the object that owns it does not go away when the activity does.
 */
data class SessionState(
    val view: SessionView? = null,
    val error: String? = null,
    val busy: Boolean = true,
    val draft: TuningDraft? = null,
    val presets: PresetPickerState = PresetPickerState.NotApplicable,
    val root: String = "C",
    val quality: String = "major",
)

/**
 * The session state machine, held outside the composition.
 *
 * A configuration change (rotation, and rotating back) destroys and recreates
 * the activity: anything a composable kept in `remember` is re-initialised and
 * the session is lost. So the session is kept here instead — in an object whose
 * lifetime is the activity's *task*, not a composition — and the screen reads it
 * and sends it its intentions. Nothing about the *screen* moves: every value is
 * still the engine's, arriving through [SessionEngine]; what moves is where the
 * state lives.
 *
 * The holder is deliberately free of Compose so it can be pinned by a JVM unit
 * test: it takes the coroutine [scope] it should launch engine calls on and it
 * publishes every transition to [onState], which the Compose layer mirrors into
 * a snapshot state. That mirroring direction matters — the holder is the source
 * of truth, so a new composition reads the session it already holds instead of
 * building a new one.
 *
 * The engine is only ever asked once per transition. In particular [start] is
 * idempotent while a session is loaded, because the composition calls it again
 * after every rotation: re-asking would replace the user's session with a fresh
 * one, which is the same bug wearing a different hat.
 */
class SessionStateHolder(
    private val scope: CoroutineScope,
    private val engine: SessionEngine = BindingSessionEngine,
) {

    /** The current session. Read-only outside: every write is a transition. */
    var state: SessionState = SessionState()
        private set

    /** Where every transition is published. The Compose layer installs its mirror. */
    var onState: ((SessionState) -> Unit)? = null

    /**
     * Ask the engine for the session, unless one is already held.
     *
     * This runs after every composition (and therefore after every rotation), so
     * a loaded session short-circuits: the engine is not called a second time and
     * the session the user was looking at is the one that comes back.
     */
    fun start() {
        if (state.view != null) return
        reload()
    }

    /** Ask the engine for a fresh session, whatever is held. The retry action. */
    fun reload() {
        publish(state.copy(busy = true))
        scope.launch { publish(loaded(engine.start())) }
    }

    /** Send one page event; the engine's reducer decides what it does. */
    fun applyEvent(event: PageEventDto) {
        val page = state.view ?: return
        publish(state.copy(busy = true))
        scope.launch { publish(loaded(engine.apply(page, event))) }
    }

    /**
     * Switch to another catalog instrument.
     *
     * A draft belongs to the instrument it was opened for, so it is dropped
     * here, together with the preset list that was read for that instrument —
     * the same rule the screen applied when the picker was its own state.
     */
    fun selectInstrument(definition: InstrumentDefinitionDto) {
        val page = state.view ?: return
        publish(
            state.copy(
                busy = true,
                draft = null,
                presets = PresetPickerState.NotApplicable,
            ),
        )
        scope.launch { publish(loaded(engine.switch(page, definition))) }
    }

    /**
     * Open the tuning draft together with the engine's own preset list.
     *
     * The draft and the picker's names both come from the engine; a refused
     * list stays a refusal beside a draft that opened fine.
     */
    fun openTuning() {
        val page = state.view ?: return
        publish(state.copy(busy = true))
        scope.launch {
            try {
                val opened = engine.openDraft(page)
                val names = try {
                    presetPicker(engine.presetNames(page.state.instrumentId()))
                } catch (failure: Throwable) {
                    presetPickerRefusal(failure)
                }
                publish(
                    state.copy(draft = opened, presets = names, error = null, busy = false),
                )
            } catch (failure: Throwable) {
                publish(state.copy(error = engineFailure(failure), busy = false))
            }
        }
    }

    /** Select a preset: the engine answers the whole draft, which is kept as it is. */
    fun selectPreset(name: String) {
        val open = state.draft ?: return
        answer({ engine.selectPreset(open, name) }) { current, edited ->
            current.copy(draft = edited)
        }
    }

    /**
     * Edit one string of the draft.
     *
     * The edited draft is held here and nowhere else: the committed page is not
     * touched until [applyDraft], so cancelling, dismissing or — as this change
     * makes true — rotating the phone leaves the committed tuning exactly as the
     * engine last answered it.
     */
    fun changeString(stringIndex: Int, note: String) {
        val open = state.draft ?: return
        answer({ engine.changeString(open, stringIndex, note) }) { current, edited ->
            current.copy(draft = edited)
        }
    }

    /**
     * Commit the draft through the engine's own event.
     *
     * A stale draft (the instrument changed under it) is dropped with the
     * engine's sentence shown, never written into the wrong instrument.
     */
    fun applyDraft() {
        val page = state.view ?: return
        val open = state.draft ?: return
        publish(state.copy(busy = true))
        scope.launch {
            publish(
                when (val result = engine.commit(page, open)) {
                    is SessionLoad.Ready -> loaded(result).copy(draft = null)
                    is SessionLoad.Failed -> state.copy(
                        error = result.reason,
                        draft = null,
                        busy = false,
                    )
                },
            )
        }
    }

    /** Cancel or dismiss the sheet: the draft is dropped, the session is untouched. */
    fun cancelDraft() {
        publish(state.copy(draft = null))
    }

    /** The chord editor's root choice. A wire token, not a computed pitch. */
    fun setRoot(root: String) {
        publish(state.copy(root = root))
    }

    /** The chord editor's quality choice: an id from the engine's own catalog. */
    fun setQuality(quality: String) {
        publish(state.copy(quality = quality))
    }

    /** One engine call whose answer is not a whole session. */
    private fun <T> answer(
        block: suspend () -> T,
        apply: (SessionState, T) -> SessionState,
    ) {
        publish(state.copy(busy = true))
        scope.launch {
            try {
                val value = block()
                publish(apply(state, value).copy(error = null, busy = false))
            } catch (failure: Throwable) {
                publish(state.copy(error = engineFailure(failure), busy = false))
            }
        }
    }

    /**
     * Fold one engine answer into the state.
     *
     * A failure never clears the session: the last valid [SessionState.view]
     * stays on screen with the engine's sentence beside it, which is what the
     * design's transition table asks for.
     */
    private fun loaded(load: SessionLoad): SessionState = when (load) {
        is SessionLoad.Ready -> knownQuality(
            state.copy(view = load.view, error = null, busy = false),
        )

        is SessionLoad.Failed -> state.copy(error = load.reason, busy = false)
    }

    /**
     * Keep the quality choice inside the engine's own catalog.
     *
     * The default quality is a starting value, never a claim: if the engine
     * groups its qualities differently, the first quality it sends is used
     * instead of a client guess. Once the choice is one the engine knows — from
     * this rule or from the user — it is left alone, so a rotation cannot move a
     * picker the user set.
     */
    private fun knownQuality(next: SessionState): SessionState {
        val groups = next.view?.qualityGroups ?: return next
        val known = groups.flatMap { group -> group.qualities.map { quality -> quality.quality } }
        if (next.quality in known) return next
        return known.firstOrNull()?.let { first -> next.copy(quality = first) } ?: next
    }

    private fun publish(next: SessionState) {
        state = next
        onState?.invoke(next)
    }
}
