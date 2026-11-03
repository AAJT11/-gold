package com.deskincome.luxury

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

class FloatingCapsuleService : Service() {
    companion object {
        const val CHANNEL_ID = "deskincome_live_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.deskincome.luxury.ACTION_STOP"
        const val ACTION_REFRESH = "com.deskincome.luxury.ACTION_REFRESH"
    }

    private lateinit var repo: ConfigRepository
    private lateinit var windowManager: WindowManager

    private var rootContainer: FrameLayout? = null
    private var pillView: FloatingCapsulePillView? = null
    private var expandedCard: LiquidGlassCardContainer? = null

    // Expanded Quick Panel UI References
    private var tvExpTitle: TextView? = null
    private var tvExpStatus: TextView? = null
    private var expRoller: LuxuryRollerAmountView? = null
    private var tvExpTiming: TextView? = null
    private var tvExpPercent: TextView? = null
    private var expProgressBar: ChampagneProgressBarView? = null
    private var valHours: TextView? = null
    private var valLunch: TextView? = null
    private var lblMonthly: TextView? = null
    private var valMonthly: TextView? = null
    private var valDaily: TextView? = null
    private var valHourly: TextView? = null
    private var btnExpMillion: Button? = null
    private var lblMillionCaption: TextView? = null
    private var btnSettings: TextView? = null
    private var btnCollapse: TextView? = null
    private val detailPairs = mutableListOf<Pair<TextView, TextView>>()

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isExpanded = false

    private val handler = Handler(Looper.getMainLooper())
    private var lastNotificationSec: Long = 0L

    private val idleAlphaRunnable = Runnable {
        if (!isExpanded) {
            rootContainer?.animate()?.alpha(0.76f)?.setDuration(300L)?.start()
        }
    }

    private val tickerRunnable = object : Runnable {
        override fun run() {
            updateLiveState()
            handler.postDelayed(this, 100L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = ConfigRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(null, repo.loadEffectiveSettings()))
        attachOverlayIfPermitted()
        handler.post(tickerRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                repo.isFloatingCapsuleEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                attachOverlayIfPermitted()
                updateLiveState(forceNotification = true)
            }
        }
        return START_STICKY
    }

    private fun dp(value: Float): Int = LuxuryTheme.dp(this, value).roundToInt()

    private fun getSafeBounds(): IntArray {
        val dm = resources.displayMetrics
        val minTop = dp(48f) // Always stay well below status bar
        val maxBottom = dm.heightPixels - dp(64f)
        return intArrayOf(minTop, maxBottom)
    }

    private fun attachOverlayIfPermitted() {
        if (!Settings.canDrawOverlays(this)) {
            removeOverlay()
            return
        }
        if (rootContainer != null) return

        val dm = resources.displayMetrics
        val (minY, maxY) = getSafeBounds()

        val container = FrameLayout(this)

        // 1. Collapsed AssistiveTouch Capsule Pill
        val pill = FloatingCapsulePillView(this)
        container.addView(
            pill,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // 2. Expanded AssistiveTouch Quick Panel (macOS Native Popover Style)
        val card = buildExpandedQuickPanel()
        card.visibility = View.GONE
        container.addView(
            card,
            FrameLayout.LayoutParams(
                dp(280f),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val defaultX = dm.widthPixels - dp(135f)
        val defaultY = (dm.heightPixels * 0.35f).toInt().coerceIn(minY, maxY)
        val startX = if (repo.capsuleX >= 0) repo.capsuleX.coerceIn(dp(6f), dm.widthPixels - dp(90f)) else defaultX
        val startY = if (repo.capsuleY >= minY) repo.capsuleY.coerceIn(minY, maxY) else defaultY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        attachAssistiveTouchListener(container, pill, params)

        try {
            windowManager.addView(container, params)
            rootContainer = container
            pillView = pill
            expandedCard = card
            layoutParams = params
            scheduleIdleFade()
        } catch (_: Exception) {
        }
    }

    private fun buildExpandedQuickPanel(): LiquidGlassCardContainer {
        val isDark = LuxuryTheme.isDark(this)
        val card = LiquidGlassCardContainer(this).apply {
            hasTopArrow = true
        }

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Extra top padding for the top beak arrow
            setPadding(dp(18f), dp(20f), dp(18f), dp(16f))
        }

        // 1. Header (Title + Status Pill)
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = if (repo.isMillionMode) "百万年薪体验" else "今日收入"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.parseColor(if (isDark) "#FAF9F6" else "#111827"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvExpTitle = titleTv

        val statusTv = TextView(this).apply {
            text = "正在赚钱"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor(if (isDark) "#10B981" else "#059669"))
        }
        tvExpStatus = statusTv
        headerRow.addView(titleTv)
        headerRow.addView(statusTv)
        col.addView(headerRow)

        // 2. Big Money Amount Roller
        val roller = LuxuryRollerAmountView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48f)
            ).apply {
                setMargins(0, dp(4f), 0, dp(4f))
            }
        }
        expRoller = roller
        col.addView(roller)

        // 3. Timing and Percent Sub-line
        val progSubRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(6f))
        }
        val timingTv = TextView(this).apply {
            text = "按工作时间自动计薪"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor(if (isDark) "#9CA3AF" else "#6B7280"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        tvExpTiming = timingTv

        val pctTv = TextView(this).apply {
            text = "0%"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor(if (isDark) "#E5E7EB" else "#374151"))
        }
        tvExpPercent = pctTv
        progSubRow.addView(timingTv)
        progSubRow.addView(pctTv)
        col.addView(progSubRow)

        // 4. Slim Progress Bar (3.5dp high)
        val prog = ChampagneProgressBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4f))
        }
        expProgressBar = prog
        col.addView(prog)

        // 5. Divider
        val divider1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(0.8f)).apply {
                setMargins(0, dp(12f), 0, dp(10f))
            }
            setBackgroundColor(Color.parseColor(if (isDark) "#26FFFFFF" else "#14000000"))
        }
        col.addView(divider1)

        // 6. Clean Details Rows
        detailPairs.clear()
        valHours = addDetailRow(col, "工作时间", "09:00–18:00")
        valLunch = addDetailRow(col, "午休", "12:30–14:00 · 不计薪")
        val (monthlyLbl, monthlyVal) = addDetailRowWithLabel(col, if (repo.isMillionMode) "体验月薪" else "月薪", "¥5,000.00")
        lblMonthly = monthlyLbl
        valMonthly = monthlyVal
        valDaily = addDetailRow(col, "日薪估算", "¥192.31")
        valHourly = addDetailRow(col, "每小时", "¥21.37")

        // 7. Golden Gradient Pill Button 「百万年薪体验」
        val millionBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12f), 0, dp(4f))
        }

        val btnMillion = Button(this).apply {
            text = if (repo.isMillionMode) "↶ 退出百万年薪体验" else "✨ 体验百万年薪"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.parseColor("#1F2937"))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#F59E0B"), Color.parseColor("#FBBF24"))
            ).apply {
                cornerRadius = dp(19f).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38f))
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                repo.isMillionMode = !repo.isMillionMode
                updateLiveState(forceNotification = true)
            }
        }
        btnExpMillion = btnMillion
        millionBox.addView(btnMillion)

        val captionTv = TextView(this).apply {
            text = "体验金额 · 年薪 100 万 ÷ 12，退出后恢复真实工资"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f)
            setTextColor(Color.parseColor(if (isDark) "#9CA3AF" else "#6B7280"))
            gravity = Gravity.CENTER
            setPadding(0, dp(5f), 0, 0)
        }
        lblMillionCaption = captionTv
        millionBox.addView(captionTv)
        col.addView(millionBox)

        // 8. Divider 2
        val divider2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(0.8f)).apply {
                setMargins(0, dp(10f), 0, dp(8f))
            }
            setBackgroundColor(Color.parseColor(if (isDark) "#26FFFFFF" else "#14000000"))
        }
        col.addView(divider2)

        // 9. Footer: Settings & Collapse
        val footerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2f), 0, 0)
        }

        val settingsBtn = TextView(this).apply {
            text = "修改设置"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(Color.parseColor(if (isDark) "#9CA3AF" else "#6B7280"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(2f), dp(4f), dp(8f), dp(4f))
            setOnClickListener {
                toggleExpanded(false)
                val openIntent = Intent(this@FloatingCapsuleService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(openIntent)
            }
        }
        btnSettings = settingsBtn

        val collapseBtn = TextView(this).apply {
            text = "••• ⌵"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor(if (isDark) "#9CA3AF" else "#6B7280"))
            setPadding(dp(8f), dp(4f), dp(2f), dp(4f))
            setOnClickListener {
                toggleExpanded(false)
            }
        }
        btnCollapse = collapseBtn

        footerRow.addView(settingsBtn)
        footerRow.addView(collapseBtn)
        col.addView(footerRow)

        card.addView(col)
        return card
    }

    private fun addDetailRow(container: LinearLayout, label: String, defaultVal: String): TextView {
        val (_, valView) = addDetailRowWithLabel(container, label, defaultVal)
        return valView
    }

    private fun addDetailRowWithLabel(container: LinearLayout, label: String, defaultVal: String): Pair<TextView, TextView> {
        val isDark = LuxuryTheme.isDark(this)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3f), 0, dp(3f))
        }
        val lblView = TextView(this).apply {
            text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            setTextColor(Color.parseColor(if (isDark) "#9CA3AF" else "#6B7280"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val valView = TextView(this).apply {
            text = defaultVal
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.parseColor(if (isDark) "#FAF9F6" else "#111827"))
        }
        row.addView(lblView)
        row.addView(valView)
        container.addView(row)
        detailPairs.add(lblView to valView)
        return lblView to valView
    }

    private fun toggleExpanded(expand: Boolean) {
        val container = rootContainer ?: return
        val params = layoutParams ?: return
        isExpanded = expand
        wakeUpAlpha()
        if (expand) {
            pillView?.visibility = View.GONE
            expandedCard?.visibility = View.VISIBLE
            val dm = resources.displayMetrics
            val cardW = dp(280f)
            params.x = params.x.coerceIn(dp(8f), maxOf(dp(8f), dm.widthPixels - cardW - dp(8f)))
        } else {
            expandedCard?.visibility = View.GONE
            pillView?.visibility = View.VISIBLE
            snapToNearestEdgeIfClose(params)
            scheduleIdleFade()
        }
        try {
            windowManager.updateViewLayout(container, params)
        } catch (_: Exception) {
        }
    }

    private fun attachAssistiveTouchListener(
        container: FrameLayout,
        pill: FloatingCapsulePillView,
        params: WindowManager.LayoutParams
    ) {
        var downRawX = 0f
        var downRawY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false

        val touchHandler = View.OnTouchListener { _, event ->
            val dm = resources.displayMetrics
            val (minY, maxY) = getSafeBounds()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wakeUpAlpha()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (dragging || abs(dx) > dp(4f) || abs(dy) > dp(4f)) {
                        dragging = true
                        val w = if (container.width > 0) container.width else dp(110f)
                        val h = if (container.height > 0) container.height else dp(40f)
                        params.x = (initialX + dx).coerceIn(dp(4f), maxOf(dp(4f), dm.widthPixels - w - dp(4f)))
                        params.y = (initialY + dy).coerceIn(minY, maxOf(minY, maxY - h))
                        try {
                            windowManager.updateViewLayout(container, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (!dragging) {
                        toggleExpanded(!isExpanded)
                    } else {
                        if (!isExpanded) {
                            snapToNearestEdgeIfClose(params)
                        }
                        repo.capsuleX = params.x
                        repo.capsuleY = params.y
                        scheduleIdleFade()
                    }
                    true
                }
                else -> false
            }
        }

        pill.setOnTouchListener(touchHandler)
    }

    private fun snapToNearestEdgeIfClose(params: WindowManager.LayoutParams) {
        val container = rootContainer ?: return
        val dm = resources.displayMetrics
        val w = if (container.width > 0) container.width else dp(115f)
        val edgeMargin = dp(8f)
        val snapThreshold = dp(36f)

        val targetX = when {
            params.x < snapThreshold -> edgeMargin
            params.x + w > dm.widthPixels - snapThreshold -> dm.widthPixels - w - edgeMargin
            else -> params.x
        }

        if (targetX != params.x) {
            val startX = params.x
            ValueAnimator.ofInt(startX, targetX).apply {
                duration = 180L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    params.x = anim.animatedValue as Int
                    try {
                        windowManager.updateViewLayout(container, params)
                    } catch (_: Exception) {
                    }
                }
                start()
            }
            repo.capsuleX = targetX
        }
    }

    private fun wakeUpAlpha() {
        handler.removeCallbacks(idleAlphaRunnable)
        rootContainer?.animate()?.cancel()
        rootContainer?.alpha = 1.0f
    }

    private fun scheduleIdleFade() {
        handler.removeCallbacks(idleAlphaRunnable)
        if (!isExpanded) {
            handler.postDelayed(idleAlphaRunnable, 3500L)
        }
    }

    private fun removeOverlay() {
        handler.removeCallbacks(idleAlphaRunnable)
        rootContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        rootContainer = null
        pillView = null
        expandedCard = null
    }

    private fun updateLiveState(forceNotification: Boolean = false) {
        val settings = repo.loadEffectiveSettings()
        val now = ZonedDateTime.now()
        val snap = IncomeMath.snapshot(settings, now)
        val isWorking = (snap.status == Status.WORKING)
        val isMillion = repo.isMillionMode
        val formattedMoney = settings.money(snap.earned, digits = if (isWorking) 3 else 2)

        pillView?.let { view ->
            view.status = snap.status
            view.progress = snap.progress.toFloat()
            view.isMillion = isMillion
            view.amountText = formattedMoney
        }

        if (isExpanded) {
            val isDark = LuxuryTheme.isDark(this)
            tvExpTitle?.text = if (isMillion) "百万年薪体验" else "今日收入"
            tvExpStatus?.text = snap.statusLabel

            // Status color matching
            val statusColor = when (snap.status) {
                Status.WORKING -> if (isDark) Color.parseColor("#10B981") else Color.parseColor("#059669")
                Status.LUNCH -> Color.parseColor("#F59E0B")
                Status.BEFORE -> Color.parseColor("#D97706")
                Status.FINISHED, Status.REST -> Color.parseColor("#9CA3AF")
            }
            tvExpStatus?.setTextColor(statusColor)

            expRoller?.let { roller ->
                roller.isWorking = isWorking
                roller.isMillion = isMillion
                roller.amountText = formattedMoney
            }

            tvExpTiming?.text = snap.timingLabel(now)
            val pct = (snap.progress * 100.0).roundToInt()
            tvExpPercent?.text = "$pct%"

            expProgressBar?.let { bar ->
                bar.isMillion = isMillion
                bar.progress = snap.progress.toFloat()
            }

            valHours?.text = settings.hoursLabel
            valLunch?.text = if (settings.hasLunchBreak) "${settings.lunchLabel} · 不计薪" else "无"
            lblMonthly?.text = if (isMillion) "体验月薪" else "月薪"
            valMonthly?.text = settings.money(settings.monthlySalary, 2)
            valDaily?.text = settings.money(settings.dailySalary, 2)
            valHourly?.text = settings.money(settings.hourlySalary, 2)

            btnExpMillion?.text = if (isMillion) "↶ 退出百万年薪体验" else "✨ 体验百万年薪"
        }

        val nowSec = System.currentTimeMillis() / 1000L
        if (forceNotification || nowSec != lastNotificationSec) {
            lastNotificationSec = nowSec
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(snap, settings, now))
            IncomeAppWidgetProvider.updateAllWidgets(this)
        }
    }

    private fun createNotificationChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "今日收入 · 悬浮触控胶囊与实时通知",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在屏幕提供可自由拖动的辅助触控式收入胶囊，并在通知栏实时更新"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(
        snapshot: IncomeSnapshot?,
        settings: IncomeSettings,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Notification {
        val snap = snapshot ?: IncomeMath.snapshot(settings, now)
        val pct = (snap.progress * 100.0).roundToInt()
        val isMillion = repo.isMillionMode
        val prefix = if (isMillion) "【百万年薪体验】" else "今日收入"
        val title = "$prefix ${settings.money(snap.earned, 2)} · ${snap.statusLabel} ($pct%)"
        val text = "${snap.timingLabel(now)} · ${settings.paydayLabel(now)}"

        val openPending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPending = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingCapsuleService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setSmallIcon(R.drawable.ic_launcher_gold)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "关闭悬浮胶囊",
                    stopPending
                ).build()
            )
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tickerRunnable)
        removeOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
