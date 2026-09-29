package dev.ironjanowar.fretboard.ui

import dev.ironjanowar.fretboard.core.ChordModeDto
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.KeySuggestionDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.session.BindingKeyProgressionEngine
import dev.ironjanowar.fretboard.session.BindingSessionEngine
import dev.ironjanowar.fretboard.session.KeyDraft
import dev.ironjanowar.fretboard.session.KeyProgressionDrafts
import dev.ironjanowar.fretboard.session.KeyProgressionEngine
import dev.ironjanowar.fretboard.session.ProgressionCatalogState
import dev.ironjanowar.fretboard.session.ProgressionDraft
import dev.ironjanowar.fretboard.session.SessionEngine
import dev.ironjanowar.fretboard.session.SessionLoad
import dev.ironjanowar.fretboard.session.SessionView
import dev.ironjanowar.fretboard.session.TuningDraft
import dev.ironjanowar.fretboard.session.engineFailure
import dev.ironjanowar.fretboard.session.freshKeyDraft
import dev.ironjanowar.fretboard.session.freshProgressionDraft
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
 *   and a catalog id, not musical values the client computed;
 * * [keyDraft] and [progressionDraft] are the A14 drafts, each carrying the
 *   engine's own preview for its exact fields — a preview or a cancel never
 *   touches [view], and only the engine's own commit event replaces it;
 * * [progressionCatalog] is the engine's own grouped catalog, or its refusal.
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
    /** The open key draft, with the engine's own preview of its three fields. */
    val keyDraft: KeyDraft? = null,
    /** True while the key draft's preview is still on its way from the engine. */
    val keyPreviewPending: Boolean = false,
    /** The open progression draft, with the engine's own chord list for it. */
    val progressionDraft: ProgressionDraft? = null,
    /** True while the progression draft's preview is still on its way. */
    val progressionPreviewPending: Boolean = false,
    /** The engine's own grouped progression catalog, or its refusal. */
    val progressionCatalog: ProgressionCatalogState = ProgressionCatalogState.NotApplicable,
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
    private val keyEngine: KeyProgressionEngine = BindingKeyProgressionEngine,
) {

    /** The current session. Read-only outside: every write is a transition. */
    var state: SessionState = SessionState()
        private set

    /** Where every transition is published. The Compose layer installs its mirror. */
    var onState: ((SessionState) -> Unit)? = null

    /**
     * The key and progression drafts, with every musical value from the engine.
     *
     * A14's drafts are pure values: opening or editing one asks this coordinator
     * for the engine's own preview and never touches the committed page.
     */
    private val drafts = KeyProgressionDrafts(keyEngine)

    /**
     * The generation of the newest draft request.
     *
     * A preview is a value that can arrive late; a draft that was cancelled or
     * replaced must not be reopened by an answer to a question nobody is asking
     * any more. Every request carries this token and an answer re-checks it, so a
     * dismissed draft invalidates the work in flight.
     */
    private var draftGeneration: Long = 0

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

    // --------------------------------------------- key and progression (A14)

    /**
     * Open the key sheet as the plan's fresh C major triad and ask the engine for
     * its preview.
     *
     * The draft's three fields are known at once, so the sheet opens
     * immediately; [SessionState.keyPreviewPending] says the engine's chord list
     * is still on its way, so an empty preview is never confused with the
     * engine having answered nothing.
     */
    fun openKey() {
        val token = nextDraftGeneration()
        publish(state.copy(keyDraft = freshKeyDraft(), keyPreviewPending = true, error = null))
        scope.launch {
            try {
                val draft = drafts.openKey()
                if (token != draftGeneration) return@launch
                publish(state.copy(keyDraft = draft, keyPreviewPending = false))
            } catch (failure: Throwable) {
                if (token != draftGeneration) return@launch
                publish(state.copy(error = engineFailure(failure), keyPreviewPending = false))
            }
        }
    }

    /** Change the key draft's tonic: the engine answers a new preview for it. */
    fun changeKeyTonic(tonic: String) {
        val open = state.keyDraft ?: return
        previewKey(open.copy(tonic = tonic))
    }

    /** Change the key draft's scale: the engine answers a new preview for it. */
    fun changeKeyScale(scale: String) {
        val open = state.keyDraft ?: return
        previewKey(open.copy(scale = scale))
    }

    /** Change the key draft's mode: the engine answers a new preview for it. */
    fun changeKeyMode(mode: ChordModeDto) {
        val open = state.keyDraft ?: return
        previewKey(open.copy(mode = mode))
    }

    /**
     * Apply the key draft through the engine's own `CommitKeys` event.
     *
     * The engine replaces the page's chords rather than appending them, clears
     * the highlight and keeps the instrument, its tuning, the selection and the
     * tab — the client sends the event and returns the page it answers with, and
     * never assembles a page of its own.
     */
    fun applyKeyDraft() {
        val open = state.keyDraft ?: return
        publish(state.copy(keyDraft = null, keyPreviewPending = false))
        applyEvent(drafts.keyEvent(open))
    }

    /**
     * Cancel or dismiss the key sheet: the draft is dropped and nothing in flight
     * may reopen it.
     */
    fun cancelKeyDraft() {
        nextDraftGeneration()
        publish(state.copy(keyDraft = null, keyPreviewPending = false))
    }

    /**
     * Open the progression sheet as the plan's fresh `pop_i_v_vi_iv` in C, with
     * the engine's own grouped catalog and its preview.
     */
    fun openProgression() {
        val token = nextDraftGeneration()
        publish(
            state.copy(
                progressionDraft = freshProgressionDraft(),
                progressionPreviewPending = true,
                error = null,
            ),
        )
        scope.launch {
            try {
                val catalog = drafts.catalog()
                val draft = drafts.openProgression()
                if (token != draftGeneration) return@launch
                publish(
                    state.copy(
                        progressionCatalog = catalog,
                        progressionDraft = draft,
                        progressionPreviewPending = false,
                    ),
                )
            } catch (failure: Throwable) {
                if (token != draftGeneration) return@launch
                publish(state.copy(error = engineFailure(failure), progressionPreviewPending = false))
            }
        }
    }

    /** Select a progression from the engine's catalog: the engine answers its chords. */
    fun selectProgression(progression: String) {
        val open = state.progressionDraft ?: return
        val token = nextDraftGeneration()
        publish(state.copy(progressionPreviewPending = true))
        scope.launch {
            try {
                val draft = drafts.previewProgression(open.tonic, progression)
                if (token != draftGeneration) return@launch
                publish(state.copy(progressionDraft = draft, progressionPreviewPending = false))
            } catch (failure: Throwable) {
                if (token != draftGeneration) return@launch
                publish(state.copy(error = engineFailure(failure), progressionPreviewPending = false))
            }
        }
    }

    /** Change the progression draft's tonic: the engine answers its chords for it. */
    fun changeProgressionTonic(tonic: String) {
        val open = state.progressionDraft ?: return
        val token = nextDraftGeneration()
        publish(state.copy(progressionDraft = open.copy(tonic = tonic), progressionPreviewPending = true))
        scope.launch {
            try {
                val draft = drafts.previewProgression(tonic, open.progression)
                if (token != draftGeneration) return@launch
                publish(state.copy(progressionDraft = draft, progressionPreviewPending = false))
            } catch (failure: Throwable) {
                if (token != draftGeneration) return@launch
                publish(state.copy(error = engineFailure(failure), progressionPreviewPending = false))
            }
        }
    }

    /**
     * Apply the progression draft through the engine's own `CommitProgression`
     * event.
     *
     * The engine replaces the chords with the progression's own list, repeats and
     * all, and clears the highlight; nothing is merged or de-duplicated here.
     */
    fun applyProgressionDraft() {
        val open = state.progressionDraft ?: return
        publish(state.copy(progressionDraft = null, progressionPreviewPending = false))
        applyEvent(drafts.progressionEvent(open))
    }

    /** Cancel or dismiss the progression sheet: the draft is dropped. */
    fun cancelProgressionDraft() {
        nextDraftGeneration()
        publish(state.copy(progressionDraft = null, progressionPreviewPending = false))
    }

    /**
     * Apply a suggested key from the keys panel.
     *
     * The event is the mode-free `CommitSuggestedKeys`, so the engine infers the
     * mode from the chords already on the page: the key sheet's previous mode is
     * never carried into a suggestion apply.
     */
    fun applySuggestedKey(suggestion: KeySuggestionDto) {
        applyEvent(drafts.suggestedKeyEvent(suggestion))
    }

    /** The chord editor's root choice. A wire token, not a computed pitch. */
    fun setRoot(root: String) {
        publish(state.copy(root = root))
    }

    /** The chord editor's quality choice: an id from the engine's own catalog. */
    fun setQuality(quality: String) {
        publish(state.copy(quality = quality))
    }

    // ------------------------------------------------------------- internals

    /** Re-read the key draft's preview from the engine for its current fields. */
    private fun previewKey(next: KeyDraft) {
        val token = nextDraftGeneration()
        publish(state.copy(keyDraft = next.copy(preview = emptyList()), keyPreviewPending = true))
        scope.launch {
            try {
                val preview = drafts.previewKey(next.tonic, next.scale, next.mode)
                if (token != draftGeneration) return@launch
                publish(state.copy(keyDraft = preview, keyPreviewPending = false))
            } catch (failure: Throwable) {
                if (token != draftGeneration) return@launch
                publish(state.copy(error = engineFailure(failure), keyPreviewPending = false))
            }
        }
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

    /** The next draft generation; every request takes one and only it may publish. */
    private fun nextDraftGeneration(): Long {
        draftGeneration += 1
        return draftGeneration
    }

    private fun publish(next: SessionState) {
        state = next
        onState?.invoke(next)
    }
}
