package dev.ironjanowar.fretboard.ui.analyzer

import dev.ironjanowar.fretboard.core.AdapterException
import dev.ironjanowar.fretboard.core.AnalysisDto
import dev.ironjanowar.fretboard.core.InterpretationDto
import dev.ironjanowar.fretboard.session.adapterSentence
import dev.ironjanowar.fretboard.session.SessionAnalysis
import dev.ironjanowar.fretboard.ui.visualizer.noteIntervals

/**
 * What the analyzer tab shows.
 *
 * The four answers are deliberately distinct states, because they mean different
 * things and a single grey placeholder would claim they are the same:
 *
 * * [Absent] — the engine has no analysis for this page at all (the visualizer
 *   tab). Nothing is wrong and nothing is selected.
 * * [Empty] — a computed empty analysis: the analyzer tab with an empty
 *   selection.
 * * [Pending] — the engine names a capability this build does not implement.
 * * [Unavailable] — the engine refused or could not be reached; the reason is
 *   the binding's own sentence.
 */
sealed interface AnalysisView {
    /** The engine answers no analysis for this page: the visualizer tab. */
    data object Absent : AnalysisView

    /** A computed empty analysis: nothing is selected on the analyzer tab. */
    data object Empty : AnalysisView

    /** The selection is three or more pitch classes and matches no chord. */
    data object NoMatch : AnalysisView

    /** One pitch class at one height. */
    data class Single(val note: String) : AnalysisView

    /** One class at distinct heights, or two classes seen from their lowest heights. */
    data class Interval(val low: String, val high: String, val label: String) : AnalysisView

    /** The identifications, in the engine's own order. */
    data class Results(val cards: List<AnalysisCardModel>) : AnalysisView

    /** The engine reports the capability as not implemented yet. */
    data class Pending(val reason: String) : AnalysisView

    /** The engine refused or could not be reached; nothing is guessed. */
    data class Unavailable(val reason: String) : AnalysisView
}

/** One identification card. */
data class AnalysisCardModel(
    /** The engine's slash label: the plain label, or `label/bass` outside root position. */
    val label: String,
    /** `exact`, `incomplete` or `partial`, the engine's own two flags read in its order. */
    val badge: String,
    /** The engine's note/interval pairs, in the engine's two own orders. */
    val notes: List<AnalysisNoteModel>,
    /** The engine's interval labels, in their own order. */
    val intervals: List<String>,
    /** The engine's inversion, spelled the way the baseline spells it; absent when none. */
    val inversion: String?,
    /** The bass note the engine saw the answer from. */
    val bass: String,
)

/** One of the engine's notes, with the interval label it was paired with. */
data class AnalysisNoteModel(
    val note: String,
    val interval: String,
    /** True when the engine reports this interval as absent from the input. */
    val missing: Boolean,
)

/**
 * Read the engine's answer.
 *
 * `null` is a real answer — the analysis is *absent* on the visualizer tab — and
 * is not turned into an empty one. `Chords` with no interpretations is the
 * engine's no-match answer, not an empty selection.
 */
fun analysisView(analysis: AnalysisDto?): AnalysisView = when (analysis) {
    null -> AnalysisView.Absent
    is AnalysisDto.Empty -> AnalysisView.Empty
    is AnalysisDto.Single -> AnalysisView.Single(analysis.note)
    is AnalysisDto.Interval -> AnalysisView.Interval(
        low = analysis.low,
        high = analysis.high,
        label = analysis.label,
    )
    is AnalysisDto.Chords ->
        if (analysis.interpretations.isEmpty()) {
            AnalysisView.NoMatch
        } else {
            AnalysisView.Results(analysis.interpretations.map { interpretation -> card(interpretation) })
        }
}

/**
 * Classify a failed analysis call.
 *
 * The engine reports a capability it has not implemented as
 * `UnsupportedCapability`; that is a pending state, not a broken screen and not
 * an empty result. Everything else is shown as the failure it is.
 */
fun analysisFailure(error: Throwable): AnalysisView = when (error) {
    is AdapterException.UnsupportedCapability -> AnalysisView.Pending(adapterSentence(error))
    is AdapterException -> AnalysisView.Unavailable(adapterSentence(error))
    else -> AnalysisView.Unavailable(error.message ?: error.toString())
}

/**
 * The analyzer's reading of a session's analysis slot.
 *
 * This is where the three states the design insists on actually meet the
 * screen: the engine answering *no analysis* for the page, its typed answer
 * (an empty one included) and a refused call each become their own view.
 */
fun SessionAnalysis.asView(): AnalysisView = when (this) {
    SessionAnalysis.Absent -> AnalysisView.Absent
    is SessionAnalysis.Answer -> analysisView(analysis)
    is SessionAnalysis.Failed -> analysisFailure(error)
}

/** One identification, as the card renders it. */
fun card(interpretation: InterpretationDto): AnalysisCardModel = AnalysisCardModel(
    label = interpretation.slashLabel,
    badge = badgeText(interpretation),
    notes = pairedNotes(interpretation),
    intervals = interpretation.intervals,
    inversion = inversionLabel(interpretation.inversion),
    bass = interpretation.bass,
)

/**
 * The engine's two lists, zipped in the engine's own orders.
 *
 * This is `Contract.D01` as approved: the engine exposes the notes in formula
 * order and the interval labels in their own order and deliberately does not zip
 * them, and a consumer that zips reproduces the baseline's own pairs — the same
 * pairing the visualizer's cards already use ([noteIntervals]).
 */
fun pairedNotes(interpretation: InterpretationDto): List<AnalysisNoteModel> =
    noteIntervals(interpretation.notes, interpretation.intervals).map { pair ->
        AnalysisNoteModel(
            note = pair.note,
            interval = pair.interval,
            missing = isMissing(interpretation, pair.interval),
        )
    }

/**
 * Whether the engine reports this interval as absent from the input.
 *
 * The baseline's own rule (`missing?/2`): only an incomplete identification has
 * missing tones, and the label is the engine's. The client never decides which
 * member of a chord is absent.
 */
fun isMissing(interpretation: InterpretationDto, interval: String): Boolean =
    interpretation.incomplete && interval in interpretation.missingIntervals

/** The badge text of an identification, in the baseline's own order of checks. */
fun badgeText(interpretation: InterpretationDto): String = when {
    interpretation.exact -> "exact"
    interpretation.incomplete -> "incomplete"
    else -> "partial"
}

/**
 * The engine's inversion number, spelled as the baseline spells it.
 *
 * The baseline maps `0..=6` and nothing else; a value outside that range is
 * shown as absent rather than given an invented name.
 */
fun inversionLabel(inversion: UByte?): String? = when (inversion?.toInt()) {
    0 -> "Root position"
    1 -> "1st inversion"
    2 -> "2nd inversion"
    3 -> "3rd inversion"
    4 -> "4th inversion"
    5 -> "5th inversion"
    6 -> "6th inversion"
    else -> null
}
