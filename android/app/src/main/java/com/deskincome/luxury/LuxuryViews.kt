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

/**
 * Native macOS-style Frosted Material Popover Card with top beak arrow pointing to capsule.
 */
class LiquidGlassCardContainer(context: Context) : FrameLayout(context) {
    private val rect = RectF()
    private val shadowRect = RectF()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val cardPath = Path()
    var hasTopArrow: Boolean = true

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val isDark = LuxuryTheme.isDark(context)
        val strokeW = LuxuryTheme.dp(context, 0.9f)
        val radius = LuxuryTheme.dp(context, 18f)
        val arrowH = if (hasTopArrow) LuxuryTheme.dp(context, 8f) else 0f
        val arrowW = LuxuryTheme.dp(context, 18f)

        rect.set(strokeW, strokeW, width - strokeW, height - strokeW)

        // 1. Soft ambient drop shadow
        shadowPaint.color = if (isDark) Color.argb(80, 0, 0, 0) else Color.argb(35, 0, 0, 0)
        shadowRect.set(rect.left, rect.top + arrowH + LuxuryTheme.dp(context, 2f), rect.right, rect.bottom + LuxuryTheme.dp(context, 3f))
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        // 2. Build Card Path with Top Arrow
        cardPath.reset()
        val cardTop = rect.top + arrowH
        val arrowX = rect.centerX()

        if (hasTopArrow) {
            cardPath.moveTo(arrowX, rect.top)
            cardPath.lineTo(arrowX + arrowW / 2f, cardTop)
            cardPath.lineTo(rect.right - radius, cardTop)
            cardPath.arcTo(RectF(rect.right - radius * 2f, cardTop, rect.right, cardTop + radius * 2f), -90f, 90f)
            cardPath.lineTo(rect.right, rect.bottom - radius)
            cardPath.arcTo(RectF(rect.right - radius * 2f, rect.bottom - radius * 2f, rect.right, rect.bottom), 0f, 90f)
            cardPath.lineTo(rect.left + radius, rect.bottom)
            cardPath.arcTo(RectF(rect.left, rect.bottom - radius * 2f, rect.left + radius * 2f, rect.bottom), 90f, 90f)
            cardPath.lineTo(rect.left, cardTop + radius)
            cardPath.arcTo(RectF(rect.left, cardTop, rect.left + radius * 2f, cardTop + radius * 2f), 180f, 90f)
            cardPath.lineTo(arrowX - arrowW / 2f, cardTop)
            cardPath.close()
        } else {
            cardPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        }

        // 3. Frosted Material Fill
        val bgColor = if (isDark) Color.argb(238, 32, 32, 32) else Color.argb(236, 255, 255, 255)
        fillPaint.color = bgColor
        canvas.drawPath(cardPath, fillPaint)

        // 4. Subtle Top Light Reflection
        val save = canvas.save()
        canvas.clipPath(cardPath)
        sheenPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + LuxuryTheme.dp(context, 50f),
            if (isDark) Color.argb(25, 255, 255, 255) else Color.argb(140, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, sheenPaint)
        canvas.restoreToCount(save)

        // 5. Hairline Border
        borderPaint.strokeWidth = strokeW
        borderPaint.color = if (isDark) Color.argb(35, 255, 255, 255) else Color.argb(22, 0, 0, 0)
        canvas.drawPath(cardPath, borderPaint)

        super.onDraw(canvas)
    }
}

/**
 * Clean container for detail rows.
 */
class LiquidSubPanelContainer(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setWillNotDraw(false)
    }
}

/**
 * 3.5px slim capsule progress bar matching macOS native.
 */
class ChampagneProgressBarView(context: Context) : View(context) {
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }
    var isMillion: Boolean = false
        set(value) {
            field = value
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

        // Subtle track
        trackPaint.color = if (isDark) Color.argb(30, 255, 255, 255) else Color.argb(16, 0, 0, 0)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        if (progress > 0f) {
            val fillW = max(trackRect.height(), trackRect.width() * progress)
            fillRect.set(0f, 0f, fillW, trackRect.height())

            if (isMillion) {
                fillPaint.shader = LinearGradient(
                    fillRect.left, fillRect.top, fillRect.right, fillRect.bottom,
                    Color.parseColor("#F59E0B"),
                    Color.parseColor("#FBBF24"),
                    Shader.TileMode.CLAMP
                )
            } else {
                fillPaint.shader = null
                fillPaint.color = if (isDark) Color.rgb(229, 231, 235) else Color.rgb(55, 65, 81)
            }
            canvas.drawRoundRect(fillRect, radius, radius, fillPaint)
        }
    }
}

/**
 * Native Frosted Capsule Pill View for AssistiveTouch.
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
    var isMillion: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var transitionFrom: String = ""
    private var transitionTo: String = ""
    private var transitionStartTime: Long = 0L
    private val durationMs: Long = 800L

    private val rect = RectF()
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        textPaint.textSize = LuxuryTheme.dp(context, 14f)
        val ref = if (amountText.length >= 8) amountText else (amountText + "0")
        val textW = textPaint.measureText(ref)
        val desiredW = (textW + LuxuryTheme.dp(context, 48f)).toInt()
        val desiredH = LuxuryTheme.dp(context, 38f).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isDark = LuxuryTheme.isDark(context)
        val pad = LuxuryTheme.dp(context, 2f)
        rect.set(pad, pad, width - pad, height - pad)
        val radius = rect.height() / 2f

        // Soft ambient drop shadow
        shadowPaint.color = if (isDark) Color.argb(70, 0, 0, 0) else Color.argb(25, 0, 0, 0)
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + LuxuryTheme.dp(context, 1.2f), rect.right, rect.bottom + LuxuryTheme.dp(context, 1.8f)),
            radius, radius, shadowPaint
        )

        // Frosted acrylic pill fill
        val bgCol = if (isDark) Color.argb(235, 36, 36, 36) else Color.argb(238, 255, 255, 255)
        bgPaint.color = bgCol
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // Hairline border
        borderPaint.strokeWidth = LuxuryTheme.dp(context, 0.9f)
        borderPaint.color = if (isDark) Color.argb(32, 255, 255, 255) else Color.argb(20, 0, 0, 0)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Jewel Dot
        val (dotColor, glowColor) = if (isMillion) {
            Color.parseColor("#F59E0B") to Color.argb(110, 245, 158, 11)
        } else {
            when (status) {
                Status.WORKING -> Color.parseColor("#10B981") to Color.argb(90, 16, 185, 129)
                Status.LUNCH -> Color.parseColor("#F59E0B") to Color.argb(90, 245, 158, 11)
                Status.BEFORE -> Color.parseColor("#D97706") to Color.argb(75, 217, 119, 6)
                Status.FINISHED -> Color.parseColor("#6B7280") to Color.argb(60, 107, 114, 128)
                Status.REST -> Color.parseColor("#9CA3AF") to Color.argb(50, 156, 163, 175)
            }
        }
        val cy = height / 2f
        val dotX = LuxuryTheme.dp(context, 15f)
        dotPaint.color = glowColor
        canvas.drawCircle(dotX, cy, LuxuryTheme.dp(context, 5.5f), dotPaint)
        dotPaint.color = dotColor
        canvas.drawCircle(dotX, cy, LuxuryTheme.dp(context, 3.2f), dotPaint)

        // Text rendering with smooth rolling
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

        val clipLeft = LuxuryTheme.dp(context, 24f)
        val clipRight = width - LuxuryTheme.dp(context, 8f)
        val clipRect = RectF(clipLeft, LuxuryTheme.dp(context, 2f), clipRight, height - LuxuryTheme.dp(context, 2f))

        canvas.save()
        canvas.clipRect(clipRect)

        val fm = textPaint.fontMetrics
        val baseLineY = cy - (fm.ascent + fm.descent) / 2f
        val slideDist = LuxuryTheme.dp(context, 14f)

        val availW = clipRect.width()
        val startX = clipLeft + maxOf(0f, (availW - totalW) / 2f)
        var curX = startX

        val baseColor = if (isDark) Color.parseColor("#F9FAFB") else Color.parseColor("#111827")
        val tickerColor = if (isMillion) Color.parseColor("#F59E0B") else baseColor

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

                textPaint.color = tickerColor
                canvas.drawText(d1, curX + (colW - w1) / 2f, baseLineY - slideDist * fraction, textPaint)
                canvas.drawText(d2, curX + (colW - w2) / 2f, baseLineY + slideDist * (1f - fraction), textPaint)
            } else if (a == b) {
                if (b != ' ') {
                    textPaint.color = baseColor
                    val wb = textPaint.measureText(b.toString())
                    canvas.drawText(b.toString(), curX + (colW - wb) / 2f, baseLineY, textPaint)
                }
            } else {
                if (progressVal < 1f && a != ' ') {
                    val alphaOld = (255 * (1f - eased)).toInt().coerceIn(0, 255)
                    textPaint.color = Color.argb(alphaOld, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
                    val wa = textPaint.measureText(a.toString())
                    canvas.drawText(a.toString(), curX + (colW - wa) / 2f, baseLineY - slideDist * eased, textPaint)
                }
                if (progressVal > 0f && b != ' ') {
                    val alphaNew = (255 * eased).toInt().coerceIn(0, 255)
                    textPaint.color = Color.argb(alphaNew, Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor))
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
 * Clean system-font bold amount roller for dashboard.
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

    var isMillion: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private var transitionFrom: String = ""
    private var transitionTo: String = ""
    private var transitionStartTime: Long = 0L
    private val durationMs: Long = 800L

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = -0.02f
    }
    private val milliPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = -0.02f
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        textPaint.textSize = LuxuryTheme.dp(context, 38f)
        val textW = textPaint.measureText(if (amountText.length >= 8) amountText else (amountText + "0"))
        val desiredH = LuxuryTheme.dp(context, 50f).toInt()
        val desiredW = textW.toInt() + LuxuryTheme.dp(context, 16f).toInt()
        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isDark = LuxuryTheme.isDark(context)
        val fgPri = if (isDark) Color.parseColor("#F9FAFB") else Color.parseColor("#111827")
        val tickerColor = if (isMillion) Color.parseColor("#F59E0B") else fgPri

        textPaint.textSize = LuxuryTheme.dp(context, 38f)
        textPaint.color = fgPri
        milliPaint.textSize = LuxuryTheme.dp(context, 38f)
        milliPaint.color = tickerColor

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

                p.color = tickerColor
                canvas.drawText(d1, curX + (colW - w1) / 2f, baseLineY - slideDist * fraction, p)
                canvas.drawText(d2, curX + (colW - w2) / 2f, baseLineY + slideDist * (1f - fraction), p)
            } else if (a == b) {
                if (b != ' ') {
                    p.color = if (isMilli) tickerColor else fgPri
                    val wb = p.measureText(b.toString())
                    canvas.drawText(b.toString(), curX + (colW - wb) / 2f, baseLineY, p)
                }
            } else {
                val baseCol = if (isMilli) tickerColor else fgPri
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
