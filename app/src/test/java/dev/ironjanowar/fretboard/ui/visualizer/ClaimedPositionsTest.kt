package dev.ironjanowar.fretboard.ui.visualizer

import dev.ironjanowar.fretboard.core.FrettedSurfaceDto
import dev.ironjanowar.fretboard.core.NoteFillDto
import dev.ironjanowar.fretboard.core.SurfaceCellDto
import dev.ironjanowar.fretboard.core.SurfaceRowDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How many positions a slot claims, read off the engine's own memberships.
 *
 * The card shows this count, and it is the engine's membership lists that decide
 * it: a note claimed by two occurrences of the same chord counts once per
 * occurrence, exactly as `memberships` keeps its repeats.
 */
class ClaimedPositionsTest {

    private fun cell(fret: Int, memberships: List<ULong>, fill: NoteFillDto) = SurfaceCellDto(
        fret = fret.toUByte(),
        note = "n$fret",
        memberships = memberships,
        fill = fill,
    )

    private val surface = FrettedSurfaceDto(
        rows = listOf(
            SurfaceRowDto(
                cells = listOf(
                    cell(0, listOf(0uL), NoteFillDto.Slot(0uL)),
                    cell(1, listOf(0uL, 1uL), NoteFillDto.Overlap),
                    cell(2, emptyList(), NoteFillDto.Overlap),
                ),
            ),
            SurfaceRowDto(
                cells = listOf(
                    cell(0, listOf(0uL, 0uL), NoteFillDto.Slot(0uL)),
                    cell(1, listOf(1uL), NoteFillDto.Slot(1uL)),
                    cell(2, emptyList(), NoteFillDto.Overlap),
                ),
            ),
        ),
    )

    @Test
    fun `a slot that claims three positions is counted three times`() {
        assertEquals(3, claimedPositions(surface, 0uL))
    }

    @Test
    fun `a slot claimed on two different strings counts both positions`() {
        // Slot 1 is claimed at fret 1 of both rows.
        assertEquals(2, claimedPositions(surface, 1uL))
    }

    @Test
    fun `a slot no note carries claims no position`() {
        assertEquals(0, claimedPositions(surface, 7uL))
    }

    @Test
    fun `an empty surface claims nothing`() {
        assertEquals(0, claimedPositions(FrettedSurfaceDto(rows = emptyList()), 0uL))
    }
}
