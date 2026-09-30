package dev.ironjanowar.fretboard

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import dev.ironjanowar.fretboard.core.ChordModeDto
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.KeySuggestionDto
import dev.ironjanowar.fretboard.core.PageEventDto
import dev.ironjanowar.fretboard.links.ShareLauncher
import dev.ironjanowar.fretboard.links.ShareOutcome
import dev.ironjanowar.fretboard.links.DeliveredText
import dev.ironjanowar.fretboard.session.pageQuery
import dev.ironjanowar.fretboard.session.BindingKeySuggestionEngine
import dev.ironjanowar.fretboard.session.EvaluationCoordinator
import dev.ironjanowar.fretboard.session.EvaluationUiState
import dev.ironjanowar.fretboard.storage.lastSessionStore
import dev.ironjanowar.fretboard.ui.SessionState
import dev.ironjanowar.fretboard.ui.SessionStateHolder

/**
 * The session's owner: the thing that outlives a configuration change.
 *
 * Rotating the phone destroys and recreates `MainActivity`, and with it the
 * composition — so a session kept in `remember` is gone and the screen comes back
 * empty. A [ViewModel] is deliberately *not* recreated with the activity: the
 * activity's `ViewModelStore` is retained across the recreation, so the session
 * this holds is the session the user was looking at when they turned the phone.
 *
 * It owns nothing but the holder, the keys panel's own asynchronous lifecycle and
 * a Compose mirror, so the whole session — instrument, tuning, tab, the marked
 * positions and keys, the stored chords, the highlight, the root and quality
 * pickers, an open tuning draft with its edits, the key and progression drafts
 * with the engine's previews, and the suggestion rows with their expansion —
 * survives rotation and rotating back — and, since A17, survives the process as
 * well: every accepted transition is written to the application's own file through
 * the holder's store, and the next launch reopens the page it holds. URL import and
 * sharing remain a separate, later task by decision.
 *
 * No musical value is computed here or anywhere below it: `SessionStateHolder`
 * asks the engine through its port and keeps the answer, and the
 * [EvaluationCoordinator] only decides *when* to ask — never what a key is.
 */
class FretboardViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * The session, with the last one written down beside it (A17).
     *
     * The store is the application's own private file, and the holder is what it
     * is handed: every accepted transition is persisted through it, and the next
     * launch reopens the page it holds. Nothing about the session's own values
     * changes — the store carries the engine's opaque snapshot, never a second
     * copy of the music.
     */
    private val holder = SessionStateHolder(
        viewModelScope,
        store = lastSessionStore(getApplication<Application>().filesDir),
    )

    /**
     * The keys panel's asynchronous lifecycle.
     *
     * A16 puts the request off the main thread and keys it on the chord input
     * alone: the coordinator is told about every committed page and decides for
     * itself whether the chords changed, so a tab or highlight tap sends nothing
     * while an added, removed or cleared chord asks the engine again.
     */
    private val evaluation = EvaluationCoordinator(BindingKeySuggestionEngine, viewModelScope)

    /**
     * The session, observable by the composition.
     *
     * The holder is the source of truth and pushes every transition here, so
     * there is exactly one state to read and no way for the composition to hold
     * a second, divergent copy.
     */
    var state: SessionState by mutableStateOf(holder.state)
        private set

    /**
     * The keys panel's state, observable by the composition.
     *
     * The coordinator is its source of truth; this is the Compose mirror of it,
     * refreshed after every transition.
     */
    var evaluationState: EvaluationUiState by mutableStateOf(evaluation.snapshot())
        private set

    init {
        holder.onState = { next ->
            state = next
            next.view?.let { view -> evaluation.onPage(view.state) }
            evaluationState = evaluation.snapshot()
        }
        evaluation.onState = { evaluationState = evaluation.snapshot() }
    }

    /** Load the session once; a session already held is never re-asked for. */
    fun start() = holder.start()

    /** Ask the engine for the session again. The retry action. */
    fun reload() = holder.reload()

    /**
     * Hand one delivered link in, and let it win (task `A19`).
     *
     * The activity reads its intent — a launch that carried a link, or one that arrived
     * while a session was on screen — and hands the *delivery* over here; whether there
     * is a session in it, and what it means, is the parser's and the engine's business.
     */
    fun deliver(delivered: DeliveredText) = holder.deliver(delivered)

    /**
     * The link for the session on screen, handed to [onLink] when there is one (task `A20`).
     *
     * The engine writes the query and the approved origin is joined to it by `ShareLauncher`;
     * while sharing is off — which it is not, now that the origin is approved — the caller is
     * simply not called, and the reason is on screen instead.
     */
    fun shareLink(onLink: (String) -> Unit) {
        val page = state.view?.state ?: return
        viewModelScope.launch {
            val outcome = ShareLauncher().linkFor(pageQuery(page))
            (outcome as? ShareOutcome.Link)?.let { link -> onLink(link.url) }
        }
    }

    fun applyEvent(event: PageEventDto) = holder.applyEvent(event)

    fun selectInstrument(definition: InstrumentDefinitionDto) =
        holder.selectInstrument(definition)

    fun openTuning() = holder.openTuning()

    fun selectPreset(name: String) = holder.selectPreset(name)

    fun changeString(stringIndex: Int, note: String) = holder.changeString(stringIndex, note)

    fun applyDraft() = holder.applyDraft()

    fun cancelDraft() = holder.cancelDraft()

    fun setRoot(root: String) = holder.setRoot(root)

    fun setQuality(quality: String) = holder.setQuality(quality)

    // --------------------------------------------- key and progression (A14)

    fun openKey() = holder.openKey()

    fun changeKeyTonic(tonic: String) = holder.changeKeyTonic(tonic)

    fun changeKeyScale(scale: String) = holder.changeKeyScale(scale)

    fun changeKeyMode(mode: ChordModeDto) = holder.changeKeyMode(mode)

    fun applyKeyDraft() = holder.applyKeyDraft()

    fun cancelKeyDraft() = holder.cancelKeyDraft()

    fun openProgression() = holder.openProgression()

    fun selectProgression(progression: String) = holder.selectProgression(progression)

    fun changeProgressionTonic(tonic: String) = holder.changeProgressionTonic(tonic)

    fun applyProgressionDraft() = holder.applyProgressionDraft()

    fun cancelProgressionDraft() = holder.cancelProgressionDraft()

    /** Apply a suggested key: the engine infers the mode from the page's chords. */
    fun applySuggestedKey(suggestion: KeySuggestionDto) =
        holder.applySuggestedKey(suggestion)

    // --------------------------------------------------- suggestions (A15/A16)

    /** Show or hide the collapsed groups' other modes. Shared across groups. */
    fun toggleKeyExpansion() = evaluation.toggleExpanded()

    /** The panel's Retry: ask the engine again for the current chords. */
    fun retryKeyEvaluation() {
        state.view?.let { view -> evaluation.retry(view.state) }
    }

    /**
     * The ViewModel is going away: nothing in flight may land afterwards.
     *
     * A disposed session invalidates the suggestion tokens, so a late answer
     * cannot resurrect a panel that no longer has an owner.
     */
    override fun onCleared() {
        evaluation.invalidate()
        super.onCleared()
    }
}
