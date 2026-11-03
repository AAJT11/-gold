package com.deskincome.luxury

import android.content.Context
import android.content.SharedPreferences

class ConfigRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("deskincome_luxury_prefs", Context.MODE_PRIVATE)

    fun loadSettings(): IncomeSettings {
        val currency = prefs.getString("currency", "CNY") ?: "CNY"
        val monthlySalary = prefs.getFloat("monthly_salary", 5000.0f).toDouble()
        val workStart = prefs.getInt("work_start", 9 * 60)
        val workEnd = prefs.getInt("work_end", 18 * 60)
        val hasLunchBreak = prefs.getBoolean("has_lunch_break", true)
        val lunchStart = prefs.getInt("lunch_start", 12 * 60 + 30)
        val lunchEnd = prefs.getInt("lunch_end", 14 * 60)
        val monthlyWorkdays = prefs.getFloat("monthly_workdays", 26.0f).toDouble()
        val workdaysStr = prefs.getString("workdays", "1,2,3,4,5,6") ?: "1,2,3,4,5,6"
        val workdays = workdaysStr.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 0..6 }
            .toSet()
            .ifEmpty { setOf(1, 2, 3, 4, 5, 6) }
        val payday = prefs.getInt("payday", 15).coerceIn(1, 31)

        val candidate = IncomeSettings(
            currency = currency,
            monthlySalary = monthlySalary,
            workStart = workStart,
            workEnd = workEnd,
            hasLunchBreak = hasLunchBreak,
            lunchStart = lunchStart,
            lunchEnd = lunchEnd,
            monthlyWorkdays = monthlyWorkdays,
            workdays = workdays,
            payday = payday
        )
        return try {
            candidate.validated()
        } catch (_: Exception) {
            IncomeSettings()
        }
    }

    fun saveSettings(settings: IncomeSettings) {
        val valid = settings.validated()
        prefs.edit()
            .putString("currency", valid.currency)
            .putFloat("monthly_salary", valid.monthlySalary.toFloat())
            .putInt("work_start", valid.workStart)
            .putInt("work_end", valid.workEnd)
            .putBoolean("has_lunch_break", valid.hasLunchBreak)
            .putInt("lunch_start", valid.lunchStart)
            .putInt("lunch_end", valid.lunchEnd)
            .putFloat("monthly_workdays", valid.monthlyWorkdays.toFloat())
            .putString("workdays", valid.workdays.joinToString(","))
            .putInt("payday", valid.payday)
            .apply()
    }

    var isFloatingCapsuleEnabled: Boolean
        get() = prefs.getBoolean("floating_capsule_enabled", false)
        set(value) = prefs.edit().putBoolean("floating_capsule_enabled", value).apply()

    var capsuleX: Int
        get() = prefs.getInt("assistive_x", -1)
        set(value) = prefs.edit().putInt("assistive_x", value).apply()

    var capsuleY: Int
        get() = prefs.getInt("assistive_y", -1)
        set(value) = prefs.edit().putInt("assistive_y", value).apply()

    var isMillionMode: Boolean
        get() = prefs.getBoolean("million_mode", false)
        set(value) = prefs.edit().putBoolean("million_mode", value).apply()

    fun loadEffectiveSettings(): IncomeSettings {
        val s = loadSettings()
        return if (isMillionMode) {
            s.copy(monthlySalary = 1_000_000.0 / 12.0)
        } else {
            s
        }
    }
}
