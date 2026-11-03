package com.deskincome.luxury

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

class MainActivity : Activity() {

    private lateinit var repo: ConfigRepository
    private var settings: IncomeSettings = IncomeSettings()

    private lateinit var rootScroll: ScrollView
    private lateinit var tvKicker: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var heroAmountRoller: LuxuryRollerAmountView
    private lateinit var progressBar: ChampagneProgressBarView
    private lateinit var tvTiming: TextView
    private lateinit var tvPercent: TextView

    private lateinit var valHours: TextView
    private lateinit var valLunch: TextView
    private lateinit var valPayday: TextView
    private lateinit var lblMonthly: TextView
    private lateinit var valMonthly: TextView
    private lateinit var valDaily: TextView
    private lateinit var valHourly: TextView

    private lateinit var btnMillionHero: Button
    private lateinit var lblMillionCaption: TextView
    private lateinit var btnToggleCapsule: Button
    private lateinit var btnOpenSettings: Button
    private lateinit var tvCapsuleHint: TextView

    private val detailRowPairs = mutableListOf<Pair<TextView, TextView>>()
    private val handler = Handler(Looper.getMainLooper())
    private var pendingOverlayStart = false

    private val ticker = object : Runnable {
        override fun run() {
            refreshDashboard()
            handler.postDelayed(this, 100L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = ConfigRepository(this)
        settings = repo.loadSettings()

        buildLuxuryDashboardUi()
        applySystemBarsStyle()
        refreshDashboard()

        if (repo.isFloatingCapsuleEnabled) {
            startCapsuleService()
        }
    }

    override fun onResume() {
        super.onResume()
        settings = repo.loadSettings()
        if (pendingOverlayStart && Settings.canDrawOverlays(this)) {
            pendingOverlayStart = false
            repo.isFloatingCapsuleEnabled = true
            startCapsuleService()
            Toast.makeText(this, "屏幕悬浮触控胶囊已开启，可在屏幕任意位置自由拖动", Toast.LENGTH_SHORT).show()
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        handler.removeCallbacks(ticker)
        super.onPause()
    }

    private fun dp(value: Float): Int = LuxuryTheme.dp(this, value).roundToInt()

    private fun applySystemBarsStyle() {
        val isDark = LuxuryTheme.isDark(this)
        window.statusBarColor = if (isDark) Color.parseColor("#141210") else Color.parseColor("#F5F3EF")
        window.navigationBarColor = if (isDark) Color.parseColor("#141210") else Color.parseColor("#F5F3EF")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController
            if (!isDark) {
                controller?.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                controller?.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        }
    }

    private fun buildLuxuryDashboardUi() {
        val isDark = LuxuryTheme.isDark(this)
        rootScroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(if (isDark) Color.parseColor("#141210") else Color.parseColor("#F5F3EF"))
        }

        val outerCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18f), dp(22f), dp(18f), dp(28f))
        }

        // Top Brand Editorial Banner
        val brandRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4f), 0, dp(4f), dp(14f))
        }
        val brandTag = TextView(this).apply {
            text = "MAISON · DESKINCOME LUXURY EDITION"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.16f
            setTextColor(Color.parseColor(if (isDark) "#D4AF37" else "#A16207"))
        }
        brandRow.addView(brandTag)
        outerCol.addView(brandRow)

        // Main Liquid Glass Hero Card
        val heroCard = LiquidGlassCardContainer(this)
        val cardContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22f), dp(20f), dp(22f), dp(22f))
        }

        // Header Row (Kicker + Title + Status Pill)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvKicker = TextView(this).apply {
            text = "PRIVATE WEALTH · EXCLUSIVE"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            letterSpacing = 0.14f
        }
        tvTitle = TextView(this).apply {
            text = "今日收入"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(0, dp(2f), 0, 0)
        }
        titleCol.addView(tvKicker)
        titleCol.addView(tvTitle)

        tvStatusBadge = TextView(this).apply {
            text = "正在赚钱"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(12f), dp(5f), dp(12f), dp(5f))
        }

        headerRow.addView(titleCol)
        headerRow.addView(tvStatusBadge)
        cardContent.addView(headerRow)

        // Hero Serif Money Roller (macOS main-branch per-digit rolling & thousandths ticker)
        heroAmountRoller = LuxuryRollerAmountView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52f)
            ).apply {
                setMargins(0, dp(10f), 0, dp(10f))
            }
        }
        cardContent.addView(heroAmountRoller)

        // Champagne Gold Progress Bar
        progressBar = ChampagneProgressBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8f)
            )
        }
        cardContent.addView(progressBar)

        // Timing & Percent Sub-row
        val timingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(9f), 0, dp(16f))
        }
        tvTiming = TextView(this).apply {
            text = "按工作时间自动计薪"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvPercent = TextView(this).apply {
            text = "0%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        timingRow.addView(tvTiming)
        timingRow.addView(tvPercent)
        cardContent.addView(timingRow)

        // Sub-Card for Curated Details
        val subPanel = LiquidSubPanelContainer(this).apply {
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }
        detailRowPairs.clear()
        valHours = addDetailRow(subPanel, "工作时间", "09:00–18:00")
        valLunch = addDetailRow(subPanel, "午休时间", "12:30–14:00")
        valPayday = addDetailRow(subPanel, "发薪日", "次月15日")
        val (lblM, valM) = addDetailRowWithLabel(subPanel, if (repo.isMillionMode) "体验月薪" else "月薪", "¥5,000.00")
        lblMonthly = lblM
        valMonthly = valM
        valDaily = addDetailRow(subPanel, "日薪估算", "¥192.31")
        valHourly = addDetailRow(subPanel, "每小时", "¥25.64", isLast = true)
        cardContent.addView(subPanel)

        // 「百万年薪体验」Gold Gradient Pill Button
        btnMillionHero = Button(this).apply {
            text = if (repo.isMillionMode) "↶ 退出百万年薪体验" else "✨ 体验百万年薪"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.parseColor("#1F2937"))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#FBBF24"))
            ).apply {
                cornerRadius = dp(20f).toFloat()
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42f)
            ).apply {
                topMargin = dp(14f)
            }
            layoutParams = lp
            setOnClickListener {
                repo.isMillionMode = !repo.isMillionMode
                refreshDashboard()
                if (repo.isFloatingCapsuleEnabled) {
                    startCapsuleService()
                }
            }
        }
        cardContent.addView(btnMillionHero)

        lblMillionCaption = TextView(this).apply {
            text = "体验金额 · 年薪 100 万 ÷ 12，退出后恢复真实工资"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            setPadding(0, dp(6f), 0, dp(4f))
        }
        cardContent.addView(lblMillionCaption)

        // Action Buttons Row
        val btnCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(18f), 0, 0)
        }

        btnToggleCapsule = Button(this).apply {
            text = "开启屏幕悬浮触控胶囊"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            setOnClickListener { onToggleCapsuleClicked() }
        }
        btnCol.addView(btnToggleCapsule)

        btnOpenSettings = Button(this).apply {
            text = "修改工资与作息设置"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10f)
            }
            layoutParams = lp
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            setOnClickListener { showBespokeSettingsDialog() }
        }
        btnCol.addView(btnOpenSettings)

        tvCapsuleHint = TextView(this).apply {
            text = "类似辅助触控 (AssistiveTouch)，悬浮于屏幕内可随意拖动、轻触展开详情。"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(dp(4f), dp(12f), dp(4f), 0)
        }
        btnCol.addView(tvCapsuleHint)

        cardContent.addView(btnCol)
        heroCard.addView(cardContent)
        outerCol.addView(heroCard)

        rootScroll.addView(outerCol)
        setContentView(rootScroll)
    }

    private fun addDetailRow(
        container: LinearLayout,
        label: String,
        defaultVal: String,
        isLast: Boolean = false
    ): TextView {
        val (_, valView) = addDetailRowWithLabel(container, label, defaultVal, isLast)
        return valView
    }

    private fun addDetailRowWithLabel(
        container: LinearLayout,
        label: String,
        defaultVal: String,
        isLast: Boolean = false
    ): Pair<TextView, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4f), 0, if (isLast) dp(4f) else dp(8f))
        }
        val lblView = TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valView = TextView(this).apply {
            text = defaultVal
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        row.addView(lblView)
        row.addView(valView)
        container.addView(row)
        detailRowPairs.add(lblView to valView)
        return lblView to valView
    }

    private fun refreshDashboard() {
        val effSettings = repo.loadEffectiveSettings()
        val now = ZonedDateTime.now()
        val snap = IncomeMath.snapshot(effSettings, now)
        val isDark = LuxuryTheme.isDark(this)
        val isMillion = repo.isMillionMode

        val fgPri = Color.parseColor(if (isDark) "#FAF9F6" else "#1A1A1A")
        val fgSec = Color.parseColor(if (isDark) "#A8A29E" else "#57534E")
        val goldAccent = Color.parseColor(if (isDark) "#D4AF37" else "#A16207")

        tvKicker.setTextColor(goldAccent)
        tvTitle.text = if (isMillion) "百万年薪体验" else "今日收入"
        tvTitle.setTextColor(fgPri)
        tvTiming.setTextColor(fgSec)
        tvPercent.setTextColor(goldAccent)
        tvCapsuleHint.setTextColor(fgSec)
        lblMillionCaption.setTextColor(fgSec)

        val isWorking = (snap.status == Status.WORKING)
        heroAmountRoller.isWorking = isWorking
        heroAmountRoller.isMillion = isMillion
        heroAmountRoller.amountText = effSettings.money(snap.earned, digits = if (isWorking) 3 else 2)

        tvStatusBadge.text = snap.statusLabel
        styleStatusBadge(snap.status, isDark)

        progressBar.isMillion = isMillion
        progressBar.progress = snap.progress.toFloat()
        tvTiming.text = snap.timingLabel(now)
        tvPercent.text = "${(snap.progress * 100.0).roundToInt()}%"

        valHours.text = effSettings.hoursLabel
        valLunch.text = if (effSettings.hasLunchBreak) "${effSettings.lunchLabel} · 不计薪" else "无"
        valPayday.text = effSettings.paydayLabel(now)
        lblMonthly.text = if (isMillion) "体验月薪" else "月薪"
        valMonthly.text = effSettings.money(effSettings.monthlySalary, 2)
        valDaily.text = effSettings.money(effSettings.dailySalary, 2)
        valHourly.text = effSettings.money(effSettings.hourlySalary, 2)

        btnMillionHero.text = if (isMillion) "↶ 退出百万年薪体验" else "✨ 体验百万年薪"

        for ((lbl, value) in detailRowPairs) {
            lbl.setTextColor(fgSec)
            value.setTextColor(fgPri)
        }

        styleButtons(isDark)
    }

    private fun styleStatusBadge(status: Status, isDark: Boolean) {
        val (bgHex, fgHex, borderHex) = when (status) {
            Status.WORKING -> if (isDark) Triple("#2ED4AF37", "#F3E5AB", "#C5A059") else Triple("#1C1917", "#E5C158", "#C5A059")
            Status.LUNCH -> if (isDark) Triple("#38D4AF37", "#FDE68A", "#D4AF37") else Triple("#FEF3C7", "#92400E", "#D4AF37")
            Status.BEFORE -> if (isDark) Triple("#33D97706", "#FBBF24", "#D97706") else Triple("#FEF3C7", "#92400E", "#D97706")
            Status.FINISHED -> if (isDark) Triple("#1AFFFFFF", "#E7E5E4", "#C5A059") else Triple("#F5F3EF", "#1C1917", "#C5A059")
            Status.REST -> if (isDark) Triple("#14FFFFFF", "#A8A29E", "#57534E") else Triple("#F5F5F4", "#57534E", "#D6D3D1")
        }
        val gd = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(6f).toFloat()
            setColor(Color.parseColor(bgHex))
            setStroke(dp(1f), Color.parseColor(borderHex))
        }
        tvStatusBadge.background = gd
        tvStatusBadge.setTextColor(Color.parseColor(fgHex))
    }

    private fun styleButtons(isDark: Boolean) {
        val capsuleActive = repo.isFloatingCapsuleEnabled
        btnToggleCapsule.text = if (capsuleActive) {
            "屏幕悬浮触控胶囊已开启 (点击关闭)"
        } else {
            "开启屏幕悬浮触控胶囊"
        }

        // Primary CTA (.btn-primary: Obsidian Black #1C1917 + Champagne Gold border)
        val priBg = Color.parseColor(if (isDark) "#D4AF37" else "#1C1917")
        val priFg = Color.parseColor(if (isDark) "#1C1917" else "#FAF9F6")
        val priBorder = Color.parseColor(if (isDark) "#E5C158" else "#C5A059")

        val priDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8f).toFloat()
            setColor(priBg)
            setStroke(dp(1.2f), priBorder)
        }
        btnToggleCapsule.background = priDrawable
        btnToggleCapsule.setTextColor(priFg)

        // Outline Secondary CTA (.btn-outline)
        val outBg = Color.parseColor(if (isDark) "#1F292524" else "#FFFFFF")
        val outFg = Color.parseColor(if (isDark) "#FAF9F6" else "#1A1A1A")
        val outBorder = Color.parseColor(if (isDark) "#66D4AF37" else "#C5A059")

        val outDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8f).toFloat()
            setColor(outBg)
            setStroke(dp(1.2f), outBorder)
        }
        btnOpenSettings.background = outDrawable
        btnOpenSettings.setTextColor(outFg)
    }

    private fun onToggleCapsuleClicked() {
        if (repo.isFloatingCapsuleEnabled) {
            repo.isFloatingCapsuleEnabled = false
            stopService(Intent(this, FloatingCapsuleService::class.java))
            refreshDashboard()
            Toast.makeText(this, "已关闭状态栏悬浮胶囊", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
            }
        }

        if (!Settings.canDrawOverlays(this)) {
            pendingOverlayStart = true
            Toast.makeText(this, "请授予「显示在其他应用的上层」权限以开启状态栏灵动胶囊", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        repo.isFloatingCapsuleEnabled = true
        startCapsuleService()
        refreshDashboard()
        Toast.makeText(this, "屏幕悬浮触控胶囊已开启，可在屏幕任意位置自由拖动", Toast.LENGTH_SHORT).show()
    }

    private fun startCapsuleService() {
        val serviceIntent = Intent(this, FloatingCapsuleService::class.java).apply {
            action = FloatingCapsuleService.ACTION_REFRESH
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun showBespokeSettingsDialog() {
        val scroll = ScrollView(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(16f), dp(20f), dp(12f))
        }

        // 1. Currency Spinner
        val lblCurrency = TextView(this).apply {
            text = "币种"
            typeface = Typeface.DEFAULT_BOLD
        }
        val spinnerCurrency = Spinner(this)
        val currencyLabels = IncomeSettings.CURRENCIES.map { code ->
            "$code · ${IncomeSettings.CURRENCY_NAMES[code] ?: code}"
        }
        spinnerCurrency.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currencyLabels)
        val curIdx = IncomeSettings.CURRENCIES.indexOf(settings.currency)
        if (curIdx >= 0) spinnerCurrency.setSelection(curIdx)
        container.addView(lblCurrency)
        container.addView(spinnerCurrency)

        // 2. Monthly Salary
        val lblSalary = TextView(this).apply {
            text = "月薪金额"
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10f), 0, 0)
        }
        val editSalary = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(String.format(Locale.US, "%.2f", settings.monthlySalary))
        }
        container.addView(lblSalary)
        container.addView(editSalary)

        // 3. Work Start & End Time Pickers
        var selectedWorkStart = settings.workStart
        var selectedWorkEnd = settings.workEnd
        val lblWorkHours = TextView(this).apply {
            text = "上下班时间"
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10f), 0, dp(4f))
        }
        container.addView(lblWorkHours)

        val rowWorkTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnStart = Button(this).apply {
            text = "上班: ${IncomeSettings.clock(selectedWorkStart)}"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                TimePickerDialog(
                    this@MainActivity,
                    { _, h, m ->
                        selectedWorkStart = h * 60 + m
                        text = "上班: ${IncomeSettings.clock(selectedWorkStart)}"
                    },
                    selectedWorkStart / 60,
                    selectedWorkStart % 60,
                    true
                ).show()
            }
        }
        val btnEnd = Button(this).apply {
            text = "下班: ${IncomeSettings.clock(selectedWorkEnd)}"
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                TimePickerDialog(
                    this@MainActivity,
                    { _, h, m ->
                        selectedWorkEnd = h * 60 + m
                        text = "下班: ${IncomeSettings.clock(selectedWorkEnd)}"
                    },
                    selectedWorkEnd / 60,
                    selectedWorkEnd % 60,
                    true
                ).show()
            }
        }
        rowWorkTime.addView(btnStart)
        rowWorkTime.addView(btnEnd)
        container.addView(rowWorkTime)

        // 4. Lunch Break Toggle & Start/End Time Pickers
        var selectedLunchStart = settings.lunchStart
        var selectedLunchEnd = settings.lunchEnd
        val chkLunch = CheckBox(this).apply {
            text = "启用午休不计薪 (午休期间暂停累计)"
            isChecked = settings.hasLunchBreak
            setPadding(0, dp(10f), 0, dp(4f))
        }
        container.addView(chkLunch)

        val rowLunchTime = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnLunchStart = Button(this).apply {
            text = "午休起: ${IncomeSettings.clock(selectedLunchStart)}"
            isEnabled = settings.hasLunchBreak
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                TimePickerDialog(
                    this@MainActivity,
                    { _, h, m ->
                        selectedLunchStart = h * 60 + m
                        text = "午休起: ${IncomeSettings.clock(selectedLunchStart)}"
                    },
                    selectedLunchStart / 60,
                    selectedLunchStart % 60,
                    true
                ).show()
            }
        }
        val btnLunchEnd = Button(this).apply {
            text = "午休止: ${IncomeSettings.clock(selectedLunchEnd)}"
            isEnabled = settings.hasLunchBreak
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                TimePickerDialog(
                    this@MainActivity,
                    { _, h, m ->
                        selectedLunchEnd = h * 60 + m
                        text = "午休止: ${IncomeSettings.clock(selectedLunchEnd)}"
                    },
                    selectedLunchEnd / 60,
                    selectedLunchEnd % 60,
                    true
                ).show()
            }
        }
        chkLunch.setOnCheckedChangeListener { _, checked ->
            btnLunchStart.isEnabled = checked
            btnLunchEnd.isEnabled = checked
        }
        rowLunchTime.addView(btnLunchStart)
        rowLunchTime.addView(btnLunchEnd)
        container.addView(rowLunchTime)

        // 5. Monthly Workdays & Next-Month Payday
        val rowDaysAndPayday = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10f), 0, 0)
        }
        val colWorkdays = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val lblWorkdays = TextView(this).apply {
            text = "每月计薪天数"
            typeface = Typeface.DEFAULT_BOLD
        }
        val editWorkdays = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(settings.monthlyWorkdays.toString())
        }
        colWorkdays.addView(lblWorkdays)
        colWorkdays.addView(editWorkdays)

        val colPayday = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12f), 0, 0, 0)
        }
        val lblPayday = TextView(this).apply {
            text = "次月发薪日 (1–31)"
            typeface = Typeface.DEFAULT_BOLD
        }
        val editPayday = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(settings.payday.toString())
        }
        colPayday.addView(lblPayday)
        colPayday.addView(editPayday)

        rowDaysAndPayday.addView(colWorkdays)
        rowDaysAndPayday.addView(colPayday)
        container.addView(rowDaysAndPayday)

        // 6. Workdays Checkboxes (Mon..Sun)
        val lblWeekdays = TextView(this).apply {
            text = "工作日 (未勾选视为休息日)"
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(10f), 0, dp(4f))
        }
        container.addView(lblWeekdays)

        val weekdayChecks = mutableMapOf<Int, CheckBox>()
        val rowWeekdays1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val rowWeekdays2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val dayDefs = listOf(
            1 to "周一", 2 to "周二", 3 to "周三", 4 to "周四",
            5 to "周五", 6 to "周六", 0 to "周日"
        )
        dayDefs.forEachIndexed { index, (dayCode, dayLabel) ->
            val cb = CheckBox(this).apply {
                text = dayLabel
                isChecked = dayCode in settings.workdays
            }
            weekdayChecks[dayCode] = cb
            if (index < 4) rowWeekdays1.addView(cb) else rowWeekdays2.addView(cb)
        }
        container.addView(rowWeekdays1)
        container.addView(rowWeekdays2)

        scroll.addView(container)

        AlertDialog.Builder(this)
            .setTitle("工资与作息 · 专属定制")
            .setView(scroll)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存修改") { _, _ ->
                try {
                    val currency = IncomeSettings.CURRENCIES[spinnerCurrency.selectedItemPosition]
                    val salary = editSalary.text.toString().trim().toDoubleOrNull()
                        ?: throw SettingsError("请填写有效的月薪金额。")
                    val monthlyDays = editWorkdays.text.toString().trim().toDoubleOrNull()
                        ?: throw SettingsError("请填写有效的每月计薪天数。")
                    val paydayVal = editPayday.text.toString().trim().toIntOrNull()
                        ?: throw SettingsError("请填写有效的发薪日 (1–31)。")
                    val chosenWorkdays = weekdayChecks.filterValues { it.isChecked }.keys

                    val newSettings = IncomeSettings(
                        currency = currency,
                        monthlySalary = salary,
                        workStart = selectedWorkStart,
                        workEnd = selectedWorkEnd,
                        hasLunchBreak = chkLunch.isChecked,
                        lunchStart = selectedLunchStart,
                        lunchEnd = selectedLunchEnd,
                        monthlyWorkdays = monthlyDays,
                        workdays = chosenWorkdays,
                        payday = paydayVal
                    ).validated()

                    repo.saveSettings(newSettings)
                    settings = newSettings
                    refreshDashboard()
                    IncomeAppWidgetProvider.updateAllWidgets(this)
                    if (repo.isFloatingCapsuleEnabled) {
                        startCapsuleService()
                    }
                    Toast.makeText(this, "设置已保存并立即生效", Toast.LENGTH_SHORT).show()
                } catch (e: SettingsError) {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
