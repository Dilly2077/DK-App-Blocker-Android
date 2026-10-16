package com.dk.appblocker

import org.junit.Assert.*
import org.junit.Test

class FocusSessionTest {
    private fun session() = FocusSession.create(setOf("example.app"), 300_000, 100_000, 10_000, 2)!!

    @Test fun customDurationsIncludeShortLongAndNormalisedMinutes() {
        listOf("0" to "5", "" to "15", "1" to "30", "4" to "0", "25" to "0", "0" to "1500")
            .zip(listOf(5L, 15L, 90L, 240L, 1500L, 1500L)).forEach { (input, expected) ->
                assertEquals(expected * 60_000, focusDurationMillis(input.first, input.second))
            }
    }
    @Test fun invalidZeroNegativeFractionalAndOverflowInputsAreRejected() {
        listOf("" to "", "0" to "0", "-1" to "5", "0" to "-5", "1.5" to "0", "+1" to "0",
            "abc" to "5", Long.MAX_VALUE.toString() to "0", "0" to Long.MAX_VALUE.toString(),
            "9999999999999999999999999" to "0").forEach { assertNull(focusDurationMillis(it.first, it.second)) }
    }
    @Test fun countdownRoundsUpAndIncludesDaysWithoutOverflow() {
        assertEquals("00:00:00", formatFocusCountdown(-1))
        assertEquals("00:00:01", formatFocusCountdown(1))
        assertEquals("00:00:01", formatFocusCountdown(1000))
        assertEquals("00:00:02", formatFocusCountdown(1001))
        assertEquals("01:30:00", formatFocusCountdown(90 * 60_000))
        assertEquals("1d 01:00:00", formatFocusCountdown(25 * 60 * 60_000L))
        assertTrue(formatFocusCountdown(Long.MAX_VALUE).contains("d "))
    }
    @Test fun countdownAdvancesEverySecondWithoutCountingUiTicks() {
        val session = session()
        assertEquals("00:05:00", formatFocusCountdown(session.remaining(10_000, 2)))
        assertEquals("00:04:59", formatFocusCountdown(session.remaining(11_000, 2)))
        assertEquals("00:04:58", formatFocusCountdown(session.remaining(12_000, 2)))
        assertEquals("00:01:00", formatFocusCountdown(session.remaining(250_000, 2))) // Background gap.
    }
    @Test fun progressStartsAtZeroEndsAtOneAndUsesRealFraction() {
        val session = session()
        assertEquals(0f, session.progress(10_000, 2), 0f)
        assertEquals(.5f, session.progress(160_000, 2), .00001f)
        assertEquals(1f, session.progress(310_000, 2), 0f)
        assertEquals(1f, session.progress(999_999, 2), 0f)
    }
    @Test fun rebootExpiresAndWallClockMetadataDoesNotControlRemainingTime() {
        assertEquals(0, session().remaining(11_000, 3))
        val changedWall = session().copy(deadline = session().deadline.copy(wallEnd = 1))
        assertEquals(session().remaining(20_000, 2), changedWall.remaining(20_000, 2))
    }
    @Test fun sessionCreationChecksBothDeadlineAdditionsAndCopiesSelection() {
        assertNull(FocusSession.create(setOf("a"), Long.MAX_VALUE, 1, 1, 0))
        assertNull(FocusSession.create(setOf("a"), 2, Long.MAX_VALUE - 1, 1, 0))
        assertNull(FocusSession.create(setOf("a"), 2, 1, Long.MAX_VALUE - 1, 0))
        assertNull(FocusSession.create(emptySet(), 2, 1, 1, 0))
        assertNull(FocusSession.create(setOf("a"), 0, 1, 1, 0))
        val selection = mutableSetOf("a")
        val created = FocusSession.create(selection, 2, 1, 1, 0)!!
        selection += "b"
        assertEquals(setOf("a"), created.packages)
    }
}
