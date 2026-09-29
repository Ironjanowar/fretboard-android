package dev.ironjanowar.fretboard.ui.analyzer

import dev.ironjanowar.fretboard.core.AdapterException
import dev.ironjanowar.fretboard.core.AnalysisDto
import dev.ironjanowar.fretboard.core.InterpretationDto
import dev.ironjanowar.fretboard.session.SessionAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the analyzer renders, given the engine's answer.
 *
 * The three states A11 insists on being distinct are pinned here: an *absent*
 * analysis (the engine answers none for this page — the visualizer tab), a
 * computed *empty* one, and a *pending* capability. None of them is rendered as
 * another, and none of them invents a chord: `notes` and `intervals` are paired
 * in the engine's own two orders (`Contract.D01`, the approved baseline zip) and
 * a tone the engine reports as missing stays visible and says it is missing.
 */
class AnalysisModelTest {

    @Test
    fun `an absent analysis is not an empty one`() {
        assertEquals(AnalysisView.Absent, analysisView(null))
        assertEquals(AnalysisView.Empty, analysisView(AnalysisDto.Empty))
        assertNotEquals(
            "the visualizer tab's absent analysis must not read as 'nothing selected'",
            analysisView(null),
            analysisView(AnalysisDto.Empty),
        )
    }

    @Test
    fun `one pitch class is a single note`() {
        assertEquals(
            AnalysisView.Single("E"),
            analysisView(AnalysisDto.Single(note = "E")),
        )
    }

    @Test
    fun `two classes are the interval the engine names`() {
        assertEquals(
            AnalysisView.Interval(low = "G#", high = "A", label = "Minor 2nd"),
            analysisView(
                AnalysisDto.Interval(low = "G#", high = "A", label = "Minor 2nd"),
            ),
        )
    }

    @Test
    fun `an octave doubling is still an interval, spelled by the engine`() {
        assertEquals(
            AnalysisView.Interval(low = "C", high = "C", label = "Octave"),
            analysisView(AnalysisDto.Interval(low = "C", high = "C", label = "Octave")),
        )
    }

    @Test
    fun `three or more classes with no identification are their own no-match answer`() {
        assertEquals(
            AnalysisView.NoMatch,
            analysisView(
                AnalysisDto.Chords(
                    notes = listOf("C", "C#", "D"),
                    bass = "C",
                    interpretations = emptyList(),
                ),
            ),
        )
        assertNotEquals(
            "a no-match selection is not an empty selection",
            analysisView(
                AnalysisDto.Chords(notes = listOf("C", "C#", "D"), bass = "C", interpretations = emptyList()),
            ),
            analysisView(AnalysisDto.Empty),
        )
    }

    @Test
    fun `the identifications keep the engine's order`() {
        val view = analysisView(
            AnalysisDto.Chords(
                notes = listOf("C", "E", "G"),
                bass = "C",
                interpretations = listOf(
                    interpretation(label = "Cmaj", exact = true),
                    interpretation(label = "Amin/C", incomplete = true),
                    interpretation(label = "Emin#5/C"),
                ),
            ),
        )

        assertEquals(
            listOf("Cmaj", "Amin/C", "Emin#5/C"),
            (view as AnalysisView.Results).cards.map { card -> card.label },
        )
    }

    @Test
    fun `the badge reads the engine's two flags in the baseline's order`() {
        assertEquals("exact", badgeText(interpretation(exact = true)))
        assertEquals(
            "an incomplete identification is incomplete even when it is not exact",
            "incomplete",
            badgeText(interpretation(incomplete = true)),
        )
        assertEquals("partial", badgeText(interpretation()))
        // A chord cannot be exact and incomplete at once, but if the engine ever
        // said so, exact wins — the baseline's own order of checks.
        assertEquals("exact", badgeText(interpretation(exact = true, incomplete = true)))
    }

    @Test
    fun `notes and intervals are paired in the engine's two own orders`() {
        // `C9` as the contract's own example: the notes are in formula order and
        // the labels in their own order, deliberately not re-sorted.
        val interp = interpretation(
            notes = listOf("C", "D", "E", "G", "A#"),
            intervals = listOf("Root", "Major 3rd", "Perfect 5th", "Minor 7th", "Major 9th"),
        )

        assertEquals(
            listOf(
                "C" to "Root",
                "D" to "Major 3rd",
                "E" to "Perfect 5th",
                "G" to "Minor 7th",
                "A#" to "Major 9th",
            ),
            pairedNotes(interp).map { pair -> pair.note to pair.interval },
        )
    }

    @Test
    fun `an interval the engine reports as missing is shown as missing, not filled in`() {
        // The engine names the chord's own members in formula order and, for an
        // incomplete match, the labels of the formula tones the input lacks.
        val interp = interpretation(
            notes = listOf("C", "E", "G"),
            intervals = listOf("Root", "Major 3rd", "Perfect 5th"),
            incomplete = true,
            missingIntervals = listOf("Perfect 5th"),
        )

        val pairs = pairedNotes(interp)

        assertEquals("nothing is added and nothing is dropped", 3, pairs.size)
        assertEquals(
            "the pair the engine's own label marks is the one shown as missing",
            listOf(false, false, true),
            pairs.map { pair -> pair.missing },
        )
        assertEquals("Perfect 5th", pairs.last().interval)
        assertEquals("the note is the engine's own member name", "G", pairs.last().note)
        assertTrue(isMissing(interp, "Perfect 5th"))
        assertFalse(isMissing(interp, "Root"))
        assertFalse("a label the engine did not send is never marked", isMissing(interp, "Minor 7th"))
    }

    @Test
    fun `a complete identification marks nothing missing even if the engine sent the list`() {
        val interp = interpretation(
            notes = listOf("C", "E", "G"),
            intervals = listOf("Root", "Major 3rd", "Perfect 5th"),
            exact = true,
            missingIntervals = listOf("Minor 7th"),
        )

        assertTrue(pairedNotes(interp).none { pair -> pair.missing })
    }

    @Test
    fun `the card carries the slash label, the bass and the engine's inversion wording`() {
        val card = card(
            interpretation(
                label = "Cmaj/E",
                bass = "E",
                inversion = 1u,
                exact = true,
            ),
        )

        assertEquals("Cmaj/E", card.label)
        assertEquals("Bass: the engine's note", "E", card.bass)
        assertEquals("1st inversion", card.inversion)
        assertEquals("exact", card.badge)
    }

    @Test
    fun `the inversion wording is the baseline's, and an unknown one is absent`() {
        assertEquals("Root position", inversionLabel(0u))
        assertEquals("1st inversion", inversionLabel(1u))
        assertEquals("2nd inversion", inversionLabel(2u))
        assertEquals("3rd inversion", inversionLabel(3u))
        assertEquals("4th inversion", inversionLabel(4u))
        assertEquals("5th inversion", inversionLabel(5u))
        assertEquals("6th inversion", inversionLabel(6u))
        assertNull("a value outside the baseline's range gets no invented name", inversionLabel(7u))
        assertNull("no inversion is absent, not 'Root position'", inversionLabel(null))
    }

    @Test
    fun `a pending capability is its own state, not an empty analysis`() {
        val pending = analysisFailure(
            AdapterException.UnsupportedCapability(
                sentence = "the keyboard analyzer is not implemented yet",
                field = "analyzer",
            ),
        )

        assertEquals(
            AnalysisView.Pending("the keyboard analyzer is not implemented yet"),
            pending,
        )
        assertNotEquals(AnalysisView.Empty, pending)
        assertNotEquals(AnalysisView.Absent, pending)
    }

    @Test
    fun `a refusal is shown as the failure it is`() {
        val refused = analysisFailure(
            AdapterException.InvalidState(sentence = "the selection is not valid", field = "selection"),
        )

        assertEquals(AnalysisView.Unavailable("the selection is not valid"), refused)
    }

    @Test
    fun `the session's analysis slot maps to the three distinct states`() {
        // The engine answering no analysis for the page is not the engine
        // answering an empty one, and neither is a refused call.
        assertEquals(
            AnalysisView.Absent,
            SessionAnalysis.Absent.asView(),
        )
        assertEquals(
            AnalysisView.Empty,
            SessionAnalysis.Answer(AnalysisDto.Empty).asView(),
        )
        assertEquals(
            AnalysisView.Single("E"),
            SessionAnalysis.Answer(AnalysisDto.Single(note = "E")).asView(),
        )
        assertEquals(
            AnalysisView.Pending("not implemented in this build"),
            SessionAnalysis.Failed(
                AdapterException.UnsupportedCapability(
                    sentence = "not implemented in this build",
                    field = null,
                ),
            ).asView(),
        )
        assertEquals(
            AnalysisView.Unavailable("the selection is not valid"),
            SessionAnalysis.Failed(
                AdapterException.InvalidState(
                    sentence = "the selection is not valid",
                    field = "selection",
                ),
            ).asView(),
        )
    }

    /** The engine's identification record, with only the fields a test is about. */
    private fun interpretation(
        notes: List<String> = listOf("C", "E", "G"),
        intervals: List<String> = listOf("Root", "Major 3rd", "Perfect 5th"),
        exact: Boolean = false,
        incomplete: Boolean = false,
        missingIntervals: List<String> = emptyList(),
        label: String = "Cmaj",
        bass: String = "C",
        inversion: UByte? = null,
    ) = InterpretationDto(
        root = "C",
        quality = "major",
        exact = exact,
        incomplete = incomplete,
        notes = notes,
        intervals = intervals,
        missingIntervals = missingIntervals,
        bass = bass,
        inversion = inversion,
        slashLabel = label,
    )
}
