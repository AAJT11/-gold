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
    private var tvExpStatus: TextView? = null
    private var tvExpAmount: TextView? = null
    private var expProgressBar: ChampagneProgressBarView? = null
    private var tvExpTiming: TextView? = null
    private var tvExpPayday: TextView? = null

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
            handler.postDelayed(this, 200L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = ConfigRepository(this)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(null, repo.loadSettings()))
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
        val minTop = dp(48f) // Always stay well below the status bar so it is never blocked
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

        // 2. Expanded AssistiveTouch Quick Panel (hidden initially)
        val card = buildExpandedQuickPanel()
        card.visibility = View.GONE
        container.addView(
            card,
            FrameLayout.LayoutParams(
                dp(256f),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        val defaultX = dm.widthPixels - dp(128f)
        val defaultY = (dm.heightPixels * 0.34f).toInt().coerceIn(minY, maxY)
        val startX = if (repo.capsuleX >= 0) repo.capsuleX.coerceIn(dp(6f), dm.widthPixels - dp(80f)) else defaultX
        val startY = if (repo.capsuleY >= minY) repo.capsuleY.coerceIn(minY, maxY) else defaultY

        // Notice: No FLAG_LAYOUT_NO_LIMITS, so the system keeps the floating ball inside the touchable screen area!
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
        val card = LiquidGlassCardContainer(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "今日收入 · 悬浮窗"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#D4AF37"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val statusTv = TextView(this).apply {
            text = "正在赚钱"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.parseColor("#F3E5AB"))
        }
        tvExpStatus = statusTv
        topRow.addView(title)
        topRow.addView(statusTv)
        col.addView(topRow)

        val amountTv = TextView(this).apply {
            text = "¥0.00"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            typeface = Typeface.create("serif", Typeface.BOLD)
            setTextColor(Color.parseColor("#FAF9F6"))
            setPadding(0, dp(6f), 0, dp(8f))
        }
        tvExpAmount = amountTv
        col.addView(amountTv)

        val prog = ChampagneProgressBarView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6f))
        }
        expProgressBar = prog
        col.addView(prog)

        val timingTv = TextView(this).apply {
            text = "按工作时间自动计薪"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#A8A29E"))
            setPadding(0, dp(6f), 0, dp(2f))
        }
        tvExpTiming = timingTv
        col.addView(timingTv)

        val paydayTv = TextView(this).apply {
            text = "次月15日发薪"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.parseColor("#D4AF37"))
            setPadding(0, 0, 0, dp(10f))
        }
        tvExpPayday = paydayTv
        col.addView(paydayTv)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnMain = Button(this).apply {
            text = "主面板"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(Color.parseColor("#1C1917"))
            background = GradientDrawable().apply {
                cornerRadius = dp(6f).toFloat()
                setColor(Color.parseColor("#D4AF37"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(32f), 1f).apply {
                rightMargin = dp(6f)
            }
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                toggleExpanded(false)
                val openIntent = Intent(this@FloatingCapsuleService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(openIntent)
            }
        }
        val btnCollapse = Button(this).apply {
            text = "收起"
            isAllCaps = false
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setTextColor(Color.parseColor("#FAF9F6"))
            background = GradientDrawable().apply {
                cornerRadius = dp(6f).toFloat()
                setColor(Color.parseColor("#292524"))
                setStroke(dp(1f), Color.parseColor("#66D4AF37"))
            }
            layoutParams = LinearLayout.LayoutParams(0, dp(32f), 1f)
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                toggleExpanded(false)
            }
        }
        btnRow.addView(btnMain)
        btnRow.addView(btnCollapse)
        col.addView(btnRow)

        card.addView(col)
        return card
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
            val cardW = dp(256f)
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
        expandedCard?.setOnTouchListener(touchHandler)
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
        val settings = repo.loadSettings()
        val now = ZonedDateTime.now()
        val snap = IncomeMath.snapshot(settings, now)
        val formattedMoney = settings.money(snap.earned, digits = 2)

        pillView?.let { view ->
            view.status = snap.status
            view.progress = snap.progress.toFloat()
            view.amountText = formattedMoney
        }

        if (isExpanded) {
            val pct = (snap.progress * 100.0).roundToInt()
            tvExpStatus?.text = "${snap.statusLabel} · $pct%"
            tvExpAmount?.text = formattedMoney
            expProgressBar?.progress = snap.progress.toFloat()
            tvExpTiming?.text = snap.timingLabel(now)
            tvExpPayday?.text = "午休 ${settings.lunchLabel} · ${settings.paydayLabel(now)}"
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
        val title = "今日收入 ${settings.money(snap.earned, 2)} · ${snap.statusLabel} ($pct%)"
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
