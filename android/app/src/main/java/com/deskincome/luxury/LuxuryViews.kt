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
                val now = System.currentTimeMillis()
                val isWorking = (status == Status.WORKING)
                if (transitionTo.isEmpty() || !isWorking) {
                    transitionFrom = value
                    transitionTo = value
                    transitionStartTime = now - durationMs
                } else {
                    transitionFrom = transitionTo
                    transitionTo = value
                    transitionStartTime = now
                }
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

    private var transitionFrom: String = ""
    private var transitionTo: String = ""
    private var transitionStartTime: Long = 0L
    private val durationMs: Long = 800L

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
        val ref = if (amountText.length >= 8) amountText else (amountText + "0")
        val textW = textPaint.measureText(ref)
        val desiredW = (textW + LuxuryTheme.dp(context, 52f)).toInt()
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
        val dotX = LuxuryTheme.dp(context, 16f)
        dotPaint.color = glowColor
        canvas.drawCircle(dotX, cy, LuxuryTheme.dp(context, 6.2f), dotPaint)
        dotPaint.color = dotColor
        canvas.drawCircle(dotX, cy, LuxuryTheme.dp(context, 3.5f), dotPaint)

        // Per-digit vertical rolling & thousandths continuous ticker (macOS parity)
        textPaint.textSize = LuxuryTheme.dp(context, 13.5f)
        val now = System.currentTimeMillis()
        val isWorking = (status == Status.WORKING)
        val progressVal = ((now - transitionStartTime).toFloat() / durationMs).coerceIn(0f, 1f)
        val eased = progressVal * progressVal * (3f - 2f * progressVal)
        val fastPhase = if (isWorking) (now % 1000000L) * 0.012 else null

        val oldStr = if (transitionFrom.isNotEmpty()) transitionFrom else amountText
        val newStr = if (transitionTo.isNotEmpty()) transitionTo else amountText
        val count = maxOf(oldStr.length, newStr.length)
        val prevChars = oldStr.padStart(count, ' ')
        val nextChars = newStr.padStart(count, ' ')

        val digitW = textPaint.measureText("0")
        val widths = FloatArray(count) { i ->
            val c1 = prevChars[i]
            val c2 = nextChars[i]
            if (c1.isDigit() || c2.isDigit()) digitW else maxOf(textPaint.measureText(c1.toString()), textPaint.measureText(c2.toString()))
        }
        val totalW = widths.sum()

        val clipLeft = LuxuryTheme.dp(context, 26f)
        val clipRight = width - LuxuryTheme.dp(context, 8f)
        val clipRect = RectF(clipLeft, LuxuryTheme.dp(context, 2f), clipRight, height - LuxuryTheme.dp(context, 2f))

        canvas.save()
        canvas.clipRect(clipRect)

        val fm = textPaint.fontMetrics
        val baseLineY = cy - (fm.ascent + fm.descent) / 2f
        val slideDist = LuxuryTheme.dp(context, 15f)

        val availW = clipRect.width()
        val startX = clipLeft + maxOf(0f, (availW - totalW) / 2f)
        var curX = startX

        for (i in 0 until count) {
            val a = prevChars[i]
            val b = nextChars[i]
            val colW = widths[i]

            if (i == count - 1 && fastPhase != null) {
                val baseDigit = kotlin.math.floor(fastPhase).toInt()
                val fraction = (fastPhase - kotlin.math.floor(fastPhase)).toFloat()
                val d1 = (baseDigit % 10).toString()
                val d2 = ((baseDigit + 1) % 10).toString()
                val w1 = textPaint.measureText(d1)
                val w2 = textPaint.measureText(d2)

                textPaint.color = Color.rgb(250, 249, 246)
                canvas.drawText(d1, curX + (colW - w1) / 2f, baseLineY - slideDist * fraction, textPaint)
                canvas.drawText(d2, curX + (colW - w2) / 2f, baseLineY + slideDist * (1f - fraction), textPaint)
            } else if (a == b) {
                if (b != ' ') {
                    textPaint.color = Color.rgb(250, 249, 246)
                    val wb = textPaint.measureText(b.toString())
                    canvas.drawText(b.toString(), curX + (colW - wb) / 2f, baseLineY, textPaint)
                }
            } else {
                if (progressVal < 1f && a != ' ') {
                    val alphaOld = (255 * (1f - eased)).toInt().coerceIn(0, 255)
                    textPaint.color = Color.argb(alphaOld, 250, 249, 246)
                    val wa = textPaint.measureText(a.toString())
                    canvas.drawText(a.toString(), curX + (colW - wa) / 2f, baseLineY - slideDist * eased, textPaint)
                }
                if (progressVal > 0f && b != ' ') {
                    val alphaNew = (255 * eased).toInt().coerceIn(0, 255)
                    textPaint.color = Color.argb(alphaNew, 250, 249, 246)
                    val wb = textPaint.measureText(b.toString())
                    canvas.drawText(b.toString(), curX + (colW - wb) / 2f, baseLineY + slideDist * (1f - eased), textPaint)
                }
            }
            curX += colW
        }
        canvas.restore()

        if (progressVal >= 1f) {
            transitionFrom = transitionTo
        }

        if (isWorking && isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }
}

/**
 * Editorial Luxury Serif amount roller for MainActivity dashboard.
 */
class LuxuryRollerAmountView(context: Context) : View(context) {
    var amountText: String = "¥0.00"
        set(value) {
            if (field != value) {
                val now = System.currentTimeMillis()
                if (transitionTo.isEmpty() || !isWorking) {
                    transitionFrom = value
                    transitionTo = value
                    transitionStartTime = now - durationMs
                } else {
                    transitionFrom = transitionTo
                    transitionTo = value
                    transitionStartTime = now
                }
                field = value
                requestLayout()
                invalidate()
            }
        }

    var isWorking: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var transitionFrom: String = ""
    private var transitionTo: String = ""
    private var transitionStartTime: Long = 0L
    private val durationMs: Long = 800L

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.BOLD)
        letterSpacing = 0.01f
    }
    private val milliPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("serif", Typeface.BOLD)
        letterSpacing = 0.01f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        textPaint.textSize = LuxuryTheme.dp(context, 38f)
        val textW = textPaint.measureText(if (amountText.length >= 8) amountText else (amountText + "0"))
        val desiredH = LuxuryTheme.dp(context, 48f).toInt()
        val desiredW = textW.toInt() + LuxuryTheme.dp(context, 16f).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isDark = LuxuryTheme.isDark(context)
        val fgPri = if (isDark) Color.parseColor("#FAF9F6") else Color.parseColor("#1A1A1A")
        val goldAccent = if (isDark) Color.parseColor("#D4AF37") else Color.parseColor("#A16207")

        textPaint.textSize = LuxuryTheme.dp(context, 38f)
        textPaint.color = fgPri
        milliPaint.textSize = LuxuryTheme.dp(context, 38f)
        milliPaint.color = goldAccent

        val now = System.currentTimeMillis()
        val progress = ((now - transitionStartTime).toFloat() / durationMs).coerceIn(0f, 1f)
        val eased = progress * progress * (3f - 2f * progress)
        val fastPhase = if (isWorking) (now % 1000000L) * 0.012 else null

        val oldStr = if (transitionFrom.isNotEmpty()) transitionFrom else amountText
        val newStr = if (transitionTo.isNotEmpty()) transitionTo else amountText
        val count = maxOf(oldStr.length, newStr.length)
        val prevChars = oldStr.padStart(count, ' ')
        val nextChars = newStr.padStart(count, ' ')

        val digitW = textPaint.measureText("0")
        val widths = FloatArray(count) { i ->
            val c1 = prevChars[i]
            val c2 = nextChars[i]
            if (c1.isDigit() || c2.isDigit()) digitW else maxOf(textPaint.measureText(c1.toString()), textPaint.measureText(c2.toString()))
        }

        val clipRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
        canvas.save()
        canvas.clipRect(clipRect)

        val fm = textPaint.fontMetrics
        val baseLineY = height / 2f - (fm.ascent + fm.descent) / 2f
        val slideDist = LuxuryTheme.dp(context, 32f)

        var curX = 0f
        for (i in 0 until count) {
            val a = prevChars[i]
            val b = nextChars[i]
            val colW = widths[i]
            val isMilli = (i == count - 1 && isWorking)
            val p = if (isMilli) milliPaint else textPaint

            if (i == count - 1 && fastPhase != null) {
                val baseDigit = kotlin.math.floor(fastPhase).toInt()
                val fraction = (fastPhase - kotlin.math.floor(fastPhase)).toFloat()
                val d1 = (baseDigit % 10).toString()
                val d2 = ((baseDigit + 1) % 10).toString()
                val w1 = p.measureText(d1)
                val w2 = p.measureText(d2)

                p.color = goldAccent
                canvas.drawText(d1, curX + (colW - w1) / 2f, baseLineY - slideDist * fraction, p)
                canvas.drawText(d2, curX + (colW - w2) / 2f, baseLineY + slideDist * (1f - fraction), p)
            } else if (a == b) {
                if (b != ' ') {
                    p.color = if (isMilli) goldAccent else fgPri
                    val wb = p.measureText(b.toString())
                    canvas.drawText(b.toString(), curX + (colW - wb) / 2f, baseLineY, p)
                }
            } else {
                val baseCol = if (isMilli) goldAccent else fgPri
                val r = Color.red(baseCol)
                val g = Color.green(baseCol)
                val bl = Color.blue(baseCol)
                if (progress < 1f && a != ' ') {
                    val alphaOld = (255 * (1f - eased)).toInt().coerceIn(0, 255)
                    p.color = Color.argb(alphaOld, r, g, bl)
                    val wa = p.measureText(a.toString())
                    canvas.drawText(a.toString(), curX + (colW - wa) / 2f, baseLineY - slideDist * eased, p)
                }
                if (progress > 0f && b != ' ') {
                    val alphaNew = (255 * eased).toInt().coerceIn(0, 255)
                    p.color = Color.argb(alphaNew, r, g, bl)
                    val wb = p.measureText(b.toString())
                    canvas.drawText(b.toString(), curX + (colW - wb) / 2f, baseLineY + slideDist * (1f - eased), p)
                }
            }
            curX += colW
        }
        canvas.restore()

        if (progress >= 1f) {
            transitionFrom = transitionTo
        }

        if (isWorking && isAttachedToWindow) {
            postInvalidateOnAnimation()
        }
    }
}
