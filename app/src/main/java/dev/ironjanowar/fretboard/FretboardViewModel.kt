package dev.ironjanowar.fretboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ironjanowar.fretboard.core.InstrumentDefinitionDto
import dev.ironjanowar.fretboard.core.PageEventDto
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
 * It owns nothing but the holder and a Compose mirror, so the whole session —
 * instrument, tuning, tab, the marked positions and keys, the stored chords, the
 * highlight, the root and quality pickers, an open draft with its edits — survives
 * rotation and rotating back, without a byte of it being written to disk. Disk
 * and URL persistence are a separate, later task by decision.
 *
 * No musical value is computed here or anywhere below it: `SessionStateHolder`
 * asks the engine through its port and keeps the answer.
 */
class FretboardViewModel : ViewModel() {

    private val holder = SessionStateHolder(viewModelScope)

    /**
     * The session, observable by the composition.
     *
     * The holder is the source of truth and pushes every transition here, so
     * there is exactly one state to read and no way for the composition to hold
     * a second, divergent copy.
     */
    var state: SessionState by mutableStateOf(holder.state)
        private set

    init {
        holder.onState = { next -> state = next }
    }

    /** Load the session once; a session already held is never re-asked for. */
    fun start() = holder.start()

    /** Ask the engine for the session again. The retry action. */
    fun reload() = holder.reload()

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
}
