package com.deskincome.luxury

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
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
    private var pillView: FloatingCapsulePillView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastNotificationSec: Long = 0L

    private val tickerRunnable = object : Runnable {
        override fun run() {
            updateLiveState()
            handler.postDelayed(this, 250L)
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

    private fun attachOverlayIfPermitted() {
        if (!Settings.canDrawOverlays(this)) {
            removeOverlay()
            return
        }
        if (pillView != null) return

        val view = FloatingCapsulePillView(this)
        val dm = resources.displayMetrics
        val defaultX = (dm.widthPixels - LuxuryTheme.dp(this, 108f).toInt()) / 2
        val startX = if (repo.capsuleX >= 0) repo.capsuleX else defaultX
        val startY = repo.capsuleY

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        var downRawX = 0f
        var downRawY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
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
                    if (dragging || abs(dx) > 8 || abs(dy) > 8) {
                        dragging = true
                        params.x = (initialX + dx).coerceIn(0, dm.widthPixels - view.width)
                        params.y = (initialY + dy).coerceIn(0, dm.heightPixels - view.height)
                        try {
                            windowManager.updateViewLayout(view, params)
                        } catch (_: Exception) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        val openIntent = Intent(this, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        }
                        startActivity(openIntent)
                    } else {
                        repo.capsuleX = params.x
                        repo.capsuleY = params.y
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(view, params)
            pillView = view
            layoutParams = params
        } catch (_: Exception) {
        }
    }

    private fun removeOverlay() {
        pillView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        pillView = null
    }

    private fun updateLiveState(forceNotification: Boolean = false) {
        val settings = repo.loadSettings()
        val now = ZonedDateTime.now()
        val snap = IncomeMath.snapshot(settings, now)

        pillView?.let { view ->
            view.status = snap.status
            view.amountText = settings.money(snap.earned, digits = 2)
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
            "今日收入 · 状态栏实时计薪",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "在状态栏与通知栏实时展示今日累计收入与午休/下班倒计时"
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
                    "关闭状态栏胶囊",
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
