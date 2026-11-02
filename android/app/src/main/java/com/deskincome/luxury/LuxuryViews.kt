package com.deskincome.luxury

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.max

object LuxuryTheme {
    fun isDark(context: Context): Boolean {
        val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return mode == Configuration.UI_MODE_NIGHT_YES
    }

    fun dp(context: Context, dpVal: Float): Float {
        return dpVal * context.resources.displayMetrics.density
    }
}

class LiquidGlassCardContainer(context: Context) : FrameLayout(context) {
    private val rect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val clipPath = Path()

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val isDark = LuxuryTheme.isDark(context)
        val strokeW = LuxuryTheme.dp(context, 1.1f)
        val radius = LuxuryTheme.dp(context, 18f)
        rect.set(strokeW, strokeW, width - strokeW, height - strokeW)

        val bgStart = if (isDark) Color.argb(242, 28, 25, 23) else Color.argb(246, 250, 249, 246)
        val bgEnd = if (isDark) Color.argb(246, 20, 18, 16) else Color.argb(248, 245, 243, 239)
        fillPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            bgStart, bgEnd, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        // Subtle diagonal liquid glass sheen
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)
        sheenPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.top + rect.height() * 0.45f,
            intArrayOf(
                if (isDark) Color.argb(26, 212, 175, 55) else Color.argb(170, 255, 255, 255),
                if (isDark) Color.argb(8, 212, 175, 55) else Color.argb(40, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, sheenPaint)
        canvas.restoreToCount(save)

        // Champagne Gold Rim
        borderPaint.strokeWidth = strokeW
        borderPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                if (isDark) Color.argb(120, 212, 175, 55) else Color.argb(145, 197, 160, 89),
                if (isDark) Color.argb(45, 255, 255, 255) else Color.argb(220, 229, 226, 220),
                if (isDark) Color.argb(95, 197, 160, 89) else Color.argb(120, 197, 160, 89)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        super.onDraw(canvas)
    }
}

class LiquidSubPanelContainer(context: Context) : LinearLayout(context) {
    private val rect = RectF()
    private val accentRect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val isDark = LuxuryTheme.isDark(context)
        val strokeW = LuxuryTheme.dp(context, 1.0f)
        val radius = LuxuryTheme.dp(context, 12f)
        rect.set(strokeW, strokeW, width - strokeW, height - strokeW)

        val topColor = if (isDark) Color.argb(215, 38, 34, 31) else Color.argb(248, 255, 255, 255)
        val botColor = if (isDark) Color.argb(215, 30, 27, 24) else Color.argb(244, 253, 252, 250)
        fillPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            topColor, botColor, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        borderPaint.strokeWidth = strokeW
        borderPaint.color = if (isDark) Color.argb(55, 212, 175, 55) else Color.rgb(229, 229, 229)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Left Champagne Gold Editorial Accent Bar
        val padV = LuxuryTheme.dp(context, 12f)
        val barW = LuxuryTheme.dp(context, 2.6f)
        accentRect.set(rect.left, rect.top + padV, rect.left + barW, max(rect.top + padV + barW, rect.bottom - padV))
        accentPaint.color = if (isDark) Color.argb(200, 212, 175, 55) else Color.argb(215, 197, 160, 89)
        canvas.drawRoundRect(accentRect, barW / 2f, barW / 2f, accentPaint)

        super.onDraw(canvas)
    }
}

class ChampagneProgressBarView(context: Context) : View(context) {
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val trackRect = RectF()
    private val fillRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isDark = LuxuryTheme.isDark(context)
        trackRect.set(0f, 0f, width.toFloat(), height.toFloat())
        val radius = trackRect.height() / 2f

        trackPaint.color = if (isDark) Color.argb(38, 255, 255, 255) else Color.rgb(229, 226, 220)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        if (progress > 0f) {
            val fillW = max(trackRect.height(), trackRect.width() * progress)
            fillRect.set(0f, 0f, fillW, trackRect.height())
            fillPaint.shader = LinearGradient(
                fillRect.left, fillRect.top, fillRect.right, fillRect.bottom,
                intArrayOf(
                    Color.parseColor("#A16207"),
                    Color.parseColor("#D4AF37"),
                    Color.parseColor("#E5C158"),
                    Color.parseColor("#B8860B")
                ),
                floatArrayOf(0f, 0.45f, 0.75f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
        }
    }
}

/**
 * AssistiveTouch-style floating luxury capsule pill that can be freely dragged anywhere on screen.
 */
class FloatingCapsulePillView(context: Context) : View(context) {
    var amountText: String = "¥0.00"
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }
    var status: Status = Status.WORKING
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }
    var progress: Float = 0f
        set(value) {
            val clamped = value.coerceIn(0f, 1f)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    private val rect = RectF()
    private val progRect = RectF()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        textPaint.textSize = LuxuryTheme.dp(context, 14f)
        val textW = textPaint.measureText(amountText)
        val desiredW = (textW + LuxuryTheme.dp(context, 48f)).toInt()
        val desiredH = LuxuryTheme.dp(context, 40f).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isDark = LuxuryTheme.isDark(context)
        val pad = LuxuryTheme.dp(context, 2.5f)
        rect.set(pad, pad, width - pad, height - pad)
        val radius = rect.height() / 2f

        // Soft outer halo / shadow for AssistiveTouch floating depth
        shadowPaint.color = Color.argb(65, 18, 16, 14)
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + LuxuryTheme.dp(context, 1.5f), rect.right, rect.bottom + LuxuryTheme.dp(context, 1.5f)),
            radius,
            radius,
            shadowPaint
        )

        // Rich Obsidian Black / Warm Alabaster Pill Surface
        val topBg = if (isDark) Color.argb(242, 28, 25, 23) else Color.argb(246, 28, 25, 23)
        val botBg = if (isDark) Color.argb(248, 18, 16, 14) else Color.argb(248, 20, 18, 16)
        bgPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            topBg, botBg, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // Top Specular AssistiveTouch Glass Highlight
        sheenPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.5f,
            Color.argb(48, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, sheenPaint)

        // Subtle Bottom Champagne Gold Progress Line inside the Pill
        if (progress > 0f) {
            val barMargin = LuxuryTheme.dp(context, 12f)
            val maxBarW = max(0f, rect.width() - barMargin * 2f)
            val fillW = max(LuxuryTheme.dp(context, 4f), maxBarW * progress)
            val barH = LuxuryTheme.dp(context, 2.2f)
            progRect.set(
                rect.left + barMargin,
                rect.bottom - barH - LuxuryTheme.dp(context, 2.2f),
                rect.left + barMargin + fillW,
                rect.bottom - LuxuryTheme.dp(context, 2.2f)
            )
            progPaint.shader = LinearGradient(
                progRect.left, progRect.top, progRect.right, progRect.bottom,
                Color.parseColor("#D4AF37"),
                Color.parseColor("#F3E5AB"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(progRect, barH / 2f, barH / 2f, progPaint)
        }

        // Champagne Gold Rim
        borderPaint.strokeWidth = LuxuryTheme.dp(context, 1.2f)
        borderPaint.color = Color.argb(175, 212, 175, 55)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // AssistiveTouch-style Concentric Jewel Indicator Dot
        val (dotColor, glowColor) = when (status) {
            Status.WORKING -> Color.rgb(212, 175, 55) to Color.argb(95, 212, 175, 55)
            Status.LUNCH -> Color.rgb(245, 158, 11) to Color.argb(90, 245, 158, 11)
            Status.BEFORE -> Color.rgb(217, 119, 6) to Color.argb(75, 217, 119, 6)
            Status.FINISHED -> Color.rgb(245, 243, 239) to Color.argb(80, 197, 160, 89)
            Status.REST -> Color.rgb(168, 162, 158) to Color.argb(60, 168, 162, 158)
        }
        val cy = height / 2f
        val dotX = LuxuryTheme.dp(context, 17f)
        dotPaint.color = glowColor
        canvas.drawCircle(dotX, cy, LuxuryTheme.dp(context, 6.2f), dotPaint)
        dotPaint.color = dotColor
        canvas.drawCircle(dotX, cy, LuxuryTheme.dp(context, 3.5f), dotPaint)

        // Crisp Alabaster/Gold Money Text
        textPaint.textSize = LuxuryTheme.dp(context, 14f)
        textPaint.color = Color.rgb(250, 249, 246)
        val fm = textPaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) / 2f
        canvas.drawText(amountText, LuxuryTheme.dp(context, 28f), baseline, textPaint)
    }
}
