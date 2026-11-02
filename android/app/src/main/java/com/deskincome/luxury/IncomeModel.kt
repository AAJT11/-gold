package com.deskincome.luxury

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Status {
    BEFORE,
    WORKING,
    LUNCH,
    FINISHED,
    REST
}

class SettingsError(override val message: String) : Exception(message)

data class IncomeSettings(
    val currency: String = "CNY",
    val monthlySalary: Double = 5000.0,
    val workStart: Int = 9 * 60,           // 09:00
    val workEnd: Int = 18 * 60,            // 18:00
    val hasLunchBreak: Boolean = true,
    val lunchStart: Int = 12 * 60 + 30,    // 12:30
    val lunchEnd: Int = 14 * 60,           // 14:00
    val monthlyWorkdays: Double = 26.0,
    val workdays: Set<Int> = setOf(1, 2, 3, 4, 5, 6), // 1=Mon .. 6=Sat, 0=Sun
    val payday: Int = 15                   // 15th of next month
) {
    companion object {
        val CURRENCIES = listOf("CNY", "USD", "HKD", "EUR", "GBP", "JPY")
        val CURRENCY_NAMES = mapOf(
            "CNY" to "人民币",
            "USD" to "美元",
            "HKD" to "港币",
            "EUR" to "欧元",
            "GBP" to "英镑",
            "JPY" to "日元"
        )
        val CURRENCY_SYMBOLS = mapOf(
            "CNY" to "¥",
            "USD" to "$",
            "HKD" to "HK$",
            "EUR" to "€",
            "GBP" to "£",
            "JPY" to "¥"
        )

        fun clock(minutes: Int): String {
            val m = ((minutes % 1440) + 1440) % 1440
            return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
        }
    }

    val dailySalary: Double
        get() = if (monthlyWorkdays > 0) monthlySalary / monthlyWorkdays else 0.0

    val workMinutes: Int
        get() {
            if (workEnd > workStart) return workEnd - workStart
            if (workEnd < workStart) return (1440 - workStart) + workEnd
            return 0
        }

    val lunchDurationMinutes: Int
        get() {
            if (!hasLunchBreak) return 0
            val totalSpan = (workEnd - workStart + 1440) % 1440
            val lStart = (lunchStart - workStart + 1440) % 1440
            val lEnd = (lunchEnd - workStart + 1440) % 1440
            if (0 < lStart && lStart < lEnd && lEnd < totalSpan) {
                return lEnd - lStart
            }
            return 0
        }

    val effectiveWorkMinutes: Int
        get() = max(1, workMinutes - lunchDurationMinutes)

    val hourlySalary: Double
        get() {
            val eff = effectiveWorkMinutes
            return if (eff > 0) dailySalary / (eff / 60.0) else 0.0
        }

    val hoursLabel: String
        get() {
            val suffix = if (workEnd < workStart) "（次日）" else ""
            return "${clock(workStart)}–${clock(workEnd)}$suffix"
        }

    val lunchLabel: String
        get() = if (!hasLunchBreak || lunchDurationMinutes <= 0) "无午休" else "${clock(lunchStart)}–${clock(lunchEnd)}"

    fun paydayLabel(now: ZonedDateTime = ZonedDateTime.now()): String {
        val today: LocalDate = now.toLocalDate()
        val pDay = payday.coerceIn(1, 31)
        val thisYm = YearMonth.of(today.year, today.month)
        val thisPayday = LocalDate.of(today.year, today.month, min(pDay, thisYm.lengthOfMonth()))
        return when {
            today.isEqual(thisPayday) -> "次月${pDay}日 · 今日发薪"
            today.isBefore(thisPayday) -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, thisPayday)
                "次月${pDay}日 · 距发薪 $days 天"
            }
            else -> {
                val nextYm = thisYm.plusMonths(1)
                val nextPayday = LocalDate.of(nextYm.year, nextYm.month, min(pDay, nextYm.lengthOfMonth()))
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, nextPayday)
                "次月${pDay}日 · 距发薪 $days 天"
            }
        }
    }

    fun money(amount: Double, digits: Int = 2): String {
        val sym = CURRENCY_SYMBOLS[currency] ?: currency
        return String.format(Locale.US, "%s%,.${digits}f", sym, amount)
    }

    fun validated(): IncomeSettings {
        if (currency !in CURRENCIES) {
            throw SettingsError("请选择有效币种。")
        }
        if (!monthlySalary.isFinite() || monthlySalary < 0 || monthlySalary > 10_000_000) {
            throw SettingsError("月薪须介于 0 和 10,000,000 之间。")
        }
        if (workStart !in 0 until 1440 || workEnd !in 0 until 1440 || workStart == workEnd) {
            throw SettingsError("上下班时间无效，且不能相同。")
        }
        if (hasLunchBreak) {
            val totalSpan = (workEnd - workStart + 1440) % 1440
            val lStart = (lunchStart - workStart + 1440) % 1440
            val lEnd = (lunchEnd - workStart + 1440) % 1440
            if (!(0 < lStart && lStart < lEnd && lEnd < totalSpan)) {
                throw SettingsError("午休时间须处于上班与下班时间之间。")
            }
        }
        if (!monthlyWorkdays.isFinite() || monthlyWorkdays <= 0 || monthlyWorkdays > 31) {
            throw SettingsError("每月计薪天数须大于 0，且不超过 31。")
        }
        if (workdays.isEmpty() || workdays.any { it !in 0..6 }) {
            throw SettingsError("请至少选择一个工作日。")
        }
        if (payday !in 1..31) {
            throw SettingsError("发薪日须介于 1 到 31 日之间。")
        }
        return this.copy(workdays = workdays.toSortedSet())
    }
}

data class WorkShift(
    val start: ZonedDateTime,
    val end: ZonedDateTime,
    val lunchStart: ZonedDateTime? = null,
    val lunchEnd: ZonedDateTime? = null
)

data class IncomeSnapshot(
    val status: Status,
    val earned: Double,
    val dailySalary: Double,
    val progress: Double,
    val shift: WorkShift? = null,
    val nextStart: ZonedDateTime? = null
) {
    val statusLabel: String
        get() = when (status) {
            Status.BEFORE -> "等待上班"
            Status.WORKING -> "正在赚钱"
            Status.LUNCH -> "午休时间"
            Status.FINISHED -> "今日已下班"
            Status.REST -> "今日休息"
        }

    fun timingLabel(now: ZonedDateTime): String {
        val nowEpoch = now.toInstant().epochSecond
        return when (status) {
            Status.WORKING -> {
                if (shift != null) {
                    val leftWork = max(0L, shift.end.toInstant().epochSecond - nowEpoch)
                    if (shift.lunchStart != null && shift.lunchEnd != null) {
                        val lsEpoch = shift.lunchStart.toInstant().epochSecond
                        val leEpoch = shift.lunchEnd.toInstant().epochSecond
                        if (nowEpoch < lsEpoch) {
                            val lunchWait = max(0L, lsEpoch - nowEpoch)
                            val netWorkLeft = max(0L, leftWork - max(0L, leEpoch - lsEpoch))
                            return "距午休 ${duration(lunchWait.toDouble())} · 距下班 ${duration(netWorkLeft.toDouble())}"
                        }
                    }
                    "距下班 ${duration(leftWork.toDouble())}"
                } else {
                    "按工作时间自动计薪"
                }
            }
            Status.LUNCH -> {
                if (shift?.lunchEnd != null) {
                    val leftLunch = max(0L, shift.lunchEnd.toInstant().epochSecond - nowEpoch)
                    "午休中 · 距下午开工 ${duration(leftLunch.toDouble())}"
                } else {
                    "午休暂停计薪中"
                }
            }
            Status.BEFORE, Status.FINISHED, Status.REST -> {
                if (nextStart != null) {
                    val left = max(0L, nextStart.toInstant().epochSecond - nowEpoch)
                    "距下次上班 ${duration(left.toDouble())}"
                } else {
                    "按工作时间自动计薪"
                }
            }
        }
    }

    companion object {
        fun duration(seconds: Double): String {
            val totalMinutes = ceil(max(0.0, seconds) / 60.0).roundToInt()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            return if (hours > 0) "${hours}小时 ${minutes}分钟" else "${minutes}分钟"
        }
    }
}

object IncomeMath {
    private fun weekdaySundayZero(date: LocalDate): Int {
        // DayOfWeek.MONDAY=1 .. SUNDAY=7 -> convert SUNDAY to 0
        return date.dayOfWeek.value % 7
    }

    private fun isWorkday(date: LocalDate, settings: IncomeSettings): Boolean {
        return weekdaySundayZero(date) in settings.workdays
    }

    private fun makeDateTime(date: LocalDate, minutes: Int, zone: ZoneId): ZonedDateTime {
        val clamped = max(0, min(1439, minutes))
        return date.atTime(clamped / 60, clamped % 60, 0).atZone(zone)
    }

    private fun makeShift(startDay: LocalDate, settings: IncomeSettings, zone: ZoneId): WorkShift? {
        if (!isWorkday(startDay, settings)) return null
        val start = makeDateTime(startDay, settings.workStart, zone)
        val endDay = if (settings.workEnd <= settings.workStart) startDay.plusDays(1) else startDay
        val end = makeDateTime(endDay, settings.workEnd, zone)
        if (!end.isAfter(start)) return null

        var lStartDt: ZonedDateTime? = null
        var lEndDt: ZonedDateTime? = null
        if (settings.hasLunchBreak && settings.lunchDurationMinutes > 0) {
            val lsDay = if (settings.lunchStart < settings.workStart) startDay.plusDays(1) else startDay
            val leDay = if (settings.lunchEnd < settings.workStart) startDay.plusDays(1) else startDay
            val candidateLs = makeDateTime(lsDay, settings.lunchStart, zone)
            val candidateLe = makeDateTime(leDay, settings.lunchEnd, zone)
            if (candidateLs.isAfter(start) && candidateLe.isAfter(candidateLs) && candidateLe.isBefore(end)) {
                lStartDt = candidateLs
                lEndDt = candidateLe
            }
        }
        return WorkShift(start = start, end = end, lunchStart = lStartDt, lunchEnd = lEndDt)
    }

    private fun findNextStart(after: ZonedDateTime, settings: IncomeSettings): ZonedDateTime? {
        val baseDay = after.toLocalDate()
        val zone = after.zone
        for (offset in 0..7) {
            val day = baseDay.plusDays(offset.toLong())
            val shift = makeShift(day, settings, zone)
            if (shift != null && shift.start.isAfter(after)) {
                return shift.start
            }
        }
        return null
    }

    fun snapshot(settings: IncomeSettings, now: ZonedDateTime = ZonedDateTime.now()): IncomeSnapshot {
        val daily = settings.dailySalary
        if (settings.workdays.isEmpty() || settings.workMinutes <= 0) {
            return IncomeSnapshot(Status.REST, 0.0, daily, 0.0)
        }

        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)
        val zone = now.zone
        val nowMs = now.toInstant().toEpochMilli()

        val prevShift = makeShift(yesterday, settings, zone)
        val todayShift = makeShift(today, settings, zone)

        val activeShift = when {
            prevShift != null && nowMs >= prevShift.start.toInstant().toEpochMilli() && nowMs < prevShift.end.toInstant().toEpochMilli() -> prevShift
            todayShift != null && nowMs >= todayShift.start.toInstant().toEpochMilli() && nowMs < todayShift.end.toInstant().toEpochMilli() -> todayShift
            else -> null
        }

        if (activeShift != null) {
            val startMs = activeShift.start.toInstant().toEpochMilli()
            val endMs = activeShift.end.toInstant().toEpochMilli()
            val totalSpanSec = max(1.0, (endMs - startMs) / 1000.0)

            return if (activeShift.lunchStart != null && activeShift.lunchEnd != null) {
                val lsMs = activeShift.lunchStart.toInstant().toEpochMilli()
                val leMs = activeShift.lunchEnd.toInstant().toEpochMilli()
                val lunchDurSec = max(0.0, (leMs - lsMs) / 1000.0)
                val effDurationSec = max(1.0, totalSpanSec - lunchDurSec)

                val (elapsedWorkSec, currentStatus) = when {
                    nowMs < lsMs -> ((nowMs - startMs) / 1000.0) to Status.WORKING
                    nowMs < leMs -> ((lsMs - startMs) / 1000.0) to Status.LUNCH
                    else -> (((lsMs - startMs) + (nowMs - leMs)) / 1000.0) to Status.WORKING
                }
                val progress = (elapsedWorkSec / effDurationSec).coerceIn(0.0, 1.0)
                IncomeSnapshot(
                    status = currentStatus,
                    earned = daily * progress,
                    dailySalary = daily,
                    progress = progress,
                    shift = activeShift,
                    nextStart = null
                )
            } else {
                val elapsedSec = (nowMs - startMs) / 1000.0
                val progress = (elapsedSec / totalSpanSec).coerceIn(0.0, 1.0)
                IncomeSnapshot(
                    status = Status.WORKING,
                    earned = daily * progress,
                    dailySalary = daily,
                    progress = progress,
                    shift = activeShift,
                    nextStart = null
                )
            }
        }

        if (todayShift != null) {
            if (nowMs < todayShift.start.toInstant().toEpochMilli()) {
                return IncomeSnapshot(
                    status = Status.BEFORE,
                    earned = 0.0,
                    dailySalary = daily,
                    progress = 0.0,
                    shift = todayShift,
                    nextStart = todayShift.start
                )
            }
            return IncomeSnapshot(
                status = Status.FINISHED,
                earned = daily,
                dailySalary = daily,
                progress = 1.0,
                shift = todayShift,
                nextStart = findNextStart(now, settings)
            )
        }

        if (prevShift != null && prevShift.end.toLocalDate() == today && nowMs >= prevShift.end.toInstant().toEpochMilli()) {
            return IncomeSnapshot(
                status = Status.FINISHED,
                earned = daily,
                dailySalary = daily,
                progress = 1.0,
                shift = prevShift,
                nextStart = findNextStart(now, settings)
            )
        }

        return IncomeSnapshot(
            status = Status.REST,
            earned = 0.0,
            dailySalary = daily,
            progress = 0.0,
            shift = null,
            nextStart = findNextStart(now, settings)
        )
    }
}
