package dev.ironjanowar.fretboard.ui

import dev.ironjanowar.fretboard.core.KeyboardKeyDto
import dev.ironjanowar.fretboard.core.KeyboardSurfaceDto
import dev.ironjanowar.fretboard.core.NoteFillDto

/**
 * The frozen keyboard the engine answers.
 *
 * These are test inputs, not a client musical table: the engine's own keyboard
 * surface is 36 keys from pitch 48 to 83, one per pitch of its frozen range, in
 * ascending pitch order, and its note names are the sharp-only spellings of the
 * chromatic scale (the core's `note.rs` produces no other spelling). The tests
 * hand the geometry exactly that shape, so a wrong layout cannot pass by being
 * fed a shape the engine never sends.
 */
internal val KEYBOARD_RANGE: IntRange = 48..83

/** The twelve sharp note names the core's `PitchClass::name` answers, in order. */
internal val SHARP_NOTE_NAMES: List<String> =
    listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** One key of the frozen keyboard; the note is the engine's spelling for the pitch. */
internal fun keyboardKey(
    pitch: Int,
    memberships: List<ULong> = emptyList(),
    fill: NoteFillDto = NoteFillDto.Overlap,
): KeyboardKeyDto = KeyboardKeyDto(
    pitch = pitch.toUByte(),
    note = SHARP_NOTE_NAMES[(pitch - KEYBOARD_RANGE.first) % SHARP_NOTE_NAMES.size],
    memberships = memberships,
    fill = fill,
)

/** The whole 36-key frozen keyboard the engine answers for a piano page. */
internal fun frozenKeyboard(): KeyboardSurfaceDto =
    KeyboardSurfaceDto(keys = KEYBOARD_RANGE.map { pitch -> keyboardKey(pitch) })
