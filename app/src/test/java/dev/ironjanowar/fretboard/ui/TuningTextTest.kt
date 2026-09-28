package dev.ironjanowar.fretboard.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The application's only piece of logic: the display text of a tuning state.
 *
 * The generated binding carries pitches as a signed `ByteArray`, so this test
 * pins the unsigned reading — a value above 127 must not come out negative.
 */
class TuningTextTest {

    @Test
    fun `pitches are read as unsigned bytes in physical string order`() {
        val guitarStandard = byteArrayOf(40, 45, 50, 55, 59, 64)
        assertEquals("Standard 40-45-50-55-59-64", describeTuning("Standard", guitarStandard))
    }

    @Test
    fun `the highest open pitch does not come out negative`() {
        val highest = byteArrayOf(127.toByte())
        assertEquals("High 127", describeTuning("High", highest))
    }

    @Test
    fun `a reentrant tuning keeps the physical order`() {
        // The ukulele's Standard tuning starts on its highest string.
        val ukelele = byteArrayOf(67, 60, 64, 69)
        assertEquals("Standard 67-60-64-69", describeTuning("Standard", ukelele))
    }

    @Test
    fun `an empty selection is still described`() {
        assertEquals("Standard ", describeTuning("Standard", byteArrayOf()))
    }
}
