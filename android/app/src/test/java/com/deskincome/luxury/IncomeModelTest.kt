package com.deskincome.luxury

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

class IncomeModelTest {
    private val shanghaiZone = ZoneId.of("Asia/Shanghai")

    private fun dt(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, shanghaiZone)

    @Test
    fun testLunchBreakAndSundayRestAndPayday() {
        val settings = IncomeSettings(
            currency = "CNY",
            monthlySalary = 26000.0,
            workStart = 9 * 60,
            workEnd = 18 * 60,
            hasLunchBreak = true,
            lunchStart = 12 * 60 + 30,
            lunchEnd = 14 * 60,
            monthlyWorkdays = 26.0,
            workdays = setOf(1, 2, 3, 4, 5, 6),
            payday = 15
        )

        assertEquals(450, settings.effectiveWorkMinutes)

        // Oct 26, 2026 is Monday
        val before = IncomeMath.snapshot(settings, dt(26, 8, 59))
        assertEquals(Status.BEFORE, before.status)
        assertEquals(0.0, before.earned, 1e-6)

        val lunchStartSnap = IncomeMath.snapshot(settings, dt(26, 12, 30))
        val lunchMidSnap = IncomeMath.snapshot(settings, dt(26, 13, 30))
        assertEquals(Status.LUNCH, lunchStartSnap.status)
        assertEquals(Status.LUNCH, lunchMidSnap.status)
        assertTrue(abs(lunchStartSnap.earned - lunchMidSnap.earned) < 1e-8)
        assertEquals(1000.0 * 210.0 / 450.0, lunchMidSnap.earned, 1e-5)

        val afternoonStart = IncomeMath.snapshot(settings, dt(26, 14, 0))
        assertEquals(Status.WORKING, afternoonStart.status)
        assertEquals(1000.0 * 210.0 / 450.0, afternoonStart.earned, 1e-5)

        val finished = IncomeMath.snapshot(settings, dt(26, 18, 30))
        assertEquals(Status.FINISHED, finished.status)
        assertEquals(1000.0, finished.earned, 1e-5)

        // Oct 25, 2026 is Sunday -> REST
        val sunday = IncomeMath.snapshot(settings, dt(25, 13, 0))
        assertEquals(Status.REST, sunday.status)
        assertEquals(0.0, sunday.earned, 1e-6)

        // Payday label
        assertTrue(settings.paydayLabel(dt(26, 10)).contains("次月15日"))
    }
}
