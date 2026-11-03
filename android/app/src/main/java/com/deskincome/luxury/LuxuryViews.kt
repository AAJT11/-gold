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

    // Educational Bubbly Claymorphism Tokens (Option C: Mint Green & Honey Amber)
    const val COLOR_MINT_PRIMARY = 0xFF10B981.toInt()
    const val COLOR_MINT_DEEP = 0xFF064E3B.toInt()
    const val COLOR_MINT_LIGHT = 0xFFECFDF5.toInt()
    const val COLOR_BORDER_MINT = 0xFFA7F3D0.toInt()
    const val COLOR_AMBER_HONEY = 0xFFF59E0B.toInt()
    const val COLOR_AMBER_LIGHT = 0xFFFDE68A.toInt()
    const val COLOR_DARK_BG = 0xFF0A211A.toInt()
    const val COLOR_DARK_CARD = 0xFF133A2E.toInt()
    const val COLOR_DARK_BORDER = 0xFF34D399.toInt()
}

/**
 * Bubbly 3D Claymorphism Card Container (Mint & Honey Amber).
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
    private val clipPath = Path()

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        val isDark = LuxuryTheme.isDark(context)
        val strokeW = LuxuryTheme.dp(context, 2.2f)
        val radius = LuxuryTheme.dp(context, 22f)
        rect.set(strokeW, strokeW, width - strokeW, height - strokeW)

        // 1. Soft diffused green-tinted clay drop shadow
        shadowPaint.color = if (isDark) Color.argb(85, 0, 0, 0) else Color.argb(40, 6, 78, 59)
        shadowRect.set(rect.left, rect.top + LuxuryTheme.dp(context, 4f), rect.right, rect.bottom + LuxuryTheme.dp(context, 5f))
        canvas.drawRoundRect(shadowRect, radius, radius, shadowPaint)

        // 2. Puffy Clay Marshmallow Base Gradient
        val bgStart = if (isDark) Color.argb(255, 25, 75, 60) else Color.argb(255, 255, 255, 255)
        val bgEnd = if (isDark) Color.argb(255, 14, 42, 33) else Color.argb(255, 240, 253, 244)
        fillPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            bgStart, bgEnd, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        // 3. Top-left 3D Inset Marshmallow Highlight Sheen
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)
        sheenPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.42f,
            intArrayOf(
                if (isDark) Color.argb(55, 52, 211, 153) else Color.argb(215, 255, 255, 255),
                if (isDark) Color.argb(15, 52, 211, 153) else Color.argb(45, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(rect, sheenPaint)

        // Decorative Clay Top Accent Bar (Honey Amber & Fresh Mint)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val barMargin = LuxuryTheme.dp(context, 26f)
        val barH = LuxuryTheme.dp(context, 3.2f)
        val barRect = RectF(rect.left + barMargin, rect.top + strokeW / 2f, rect.right - barMargin, rect.top + strokeW / 2f + barH)
        barPaint.shader = LinearGradient(
            barRect.left, barRect.top, barRect.right, barRect.top,
            intArrayOf(
                Color.argb(0, 245, 158, 11),
                Color.argb(220, 245, 158, 11),
                Color.argb(240, 16, 185, 129),
                Color.argb(220, 245, 158, 11),
                Color.argb(0, 245, 158, 11)
            ),
            floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(barRect, barH / 2f, barH / 2f, barPaint)

        canvas.restoreToCount(save)

        // 4. Chunky Tactile Clay Border (2.2dp)
        borderPaint.strokeWidth = strokeW
        borderPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                if (isDark) Color.argb(180, 52, 211, 153) else Color.argb(255, 167, 243, 208),
                if (isDark) Color.argb(130, 16, 185, 129) else Color.argb(240, 110, 231, 183),
                if (isDark) Color.argb(110, 5, 150, 105) else Color.argb(220, 52, 211, 153)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, borderPaint)
        super.onDraw(canvas)
    }
}

/**
 * Claymorphism Sub-Panel Container for curated info.
 */
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
        val strokeW = LuxuryTheme.dp(context, 1.8f)
        val radius = LuxuryTheme.dp(context, 16f)
        rect.set(strokeW, strokeW, width - strokeW, height - strokeW)

        // Soft White Mint Clay surface
        val topColor = if (isDark) Color.argb(240, 22, 60, 48) else Color.argb(255, 255, 255, 255)
        val botColor = if (isDark) Color.argb(240, 14, 40, 32) else Color.argb(252, 248, 253, 250)
        fillPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            topColor, botColor, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, fillPaint)

        // Chunky Mint Border
        borderPaint.strokeWidth = strokeW
        borderPaint.color = if (isDark) Color.argb(120, 52, 211, 153) else Color.argb(240, 167, 243, 208)
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Left Fresh Mint Pill Indicator
        val padV = LuxuryTheme.dp(context, 14f)
        val barW = LuxuryTheme.dp(context, 3.2f)
        accentRect.set(rect.left + strokeW, rect.top + padV, rect.left + strokeW + barW, max(rect.top + padV + barW, rect.bottom - padV))
        accentPaint.color = if (isDark) Color.parseColor("#34D399") else Color.parseColor("#10B981")
        canvas.drawRoundRect(accentRect, barW / 2f, barW / 2f, accentPaint)

        super.onDraw(canvas)
    }
}

/**
 * Claymorphism recessed groove trough + Honey Amber 3D Slider with top sheen.
 */
class ChampagneProgressBarView(context: Context) : View(context) {
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    private val trackRect = RectF()
    private val fillRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val isDark = LuxuryTheme.isDark(context)
        val strokeW = LuxuryTheme.dp(context, 1.0f)
        trackRect.set(strokeW, strokeW, width.toFloat() - strokeW, height.toFloat() - strokeW)
        val radius = trackRect.height() / 2f

        // Recessed groove trough
        trackPaint.color = if (isDark) Color.argb(160, 7, 59, 44) else Color.rgb(230, 244, 234)
        canvas.drawRoundRect(trackRect, radius, radius, trackPaint)

        borderPaint.strokeWidth = strokeW
        borderPaint.color = if (isDark) Color.argb(70, 52, 211, 153) else Color.argb(160, 167, 243, 208)
        canvas.drawRoundRect(trackRect, radius, radius, borderPaint)

        if (progress > 0f) {
            val fillW = max(trackRect.height(), trackRect.width() * progress)
            fillRect.set(trackRect.left, trackRect.top, trackRect.left + fillW, trackRect.bottom)

            // Honey Amber 3D Slider (#F59E0B -> #FBBF24 -> #FCD34D)
            fillPaint.shader = LinearGradient(
                fillRect.left, fillRect.top, fillRect.right, fillRect.bottom,
                intArrayOf(
                    Color.parseColor("#F59E0B"),
                    Color.parseColor("#FBBF24"),
                    Color.parseColor("#FCD34D")
                ),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(fillRect, radius, radius, fillPaint)

            // Top soft specular sheen on the jelly slider
            val sheenH = fillRect.height() * 0.45f
            sheenPaint.shader = LinearGradient(
                fillRect.left, fillRect.top, fillRect.left, fillRect.top + sheenH,
                Color.argb(180, 255, 255, 255),
                Color.argb(20, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
            canvas.save()
            canvas.clipRect(fillRect)
            canvas.drawRect(RectF(fillRect.left, fillRect.top, fillRect.right, fillRect.top + sheenH), sheenPaint)
            canvas.restore()
        }
    }
}

/**
 * AssistiveTouch-style floating Bubbly 3D Claymorphism Pill View.
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
        val desiredW = (textW + LuxuryTheme.dp(context, 54f)).toInt()
        val desiredH = LuxuryTheme.dp(context, 42f).toInt()
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

        // 1. Soft diffused green-tinted outer drop shadow
        shadowPaint.color = if (isDark) Color.argb(70, 0, 0, 0) else Color.argb(40, 6, 78, 59)
        canvas.drawRoundRect(
            RectF(rect.left, rect.top + LuxuryTheme.dp(context, 1.8f), rect.right, rect.bottom + LuxuryTheme.dp(context, 2.2f)),
            radius,
            radius,
            shadowPaint
        )

        // 2. Bubbly 3D Marshmallow Clay Surface (Fresh Mint White or Deep Emerald Clay)
        val topBg = if (isDark) Color.argb(255, 27, 74, 60) else Color.argb(255, 255, 255, 255)
        val botBg = if (isDark) Color.argb(255, 14, 44, 34) else Color.argb(255, 236, 253, 245)
        bgPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.bottom,
            topBg, botBg, Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, bgPaint)

        // 3. Top-left Inset Clay Curvature Sheen
        sheenPaint.shader = LinearGradient(
            rect.left, rect.top, rect.left, rect.top + rect.height() * 0.5f,
            if (isDark) Color.argb(55, 52, 211, 153) else Color.argb(220, 255, 255, 255),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, sheenPaint)

        // 4. Bottom Honey Amber Progress Line
        if (progress > 0f) {
            val barMargin = LuxuryTheme.dp(context, 14f)
            val maxBarW = max(0f, rect.width() - barMargin * 2f)
            val fillW = max(LuxuryTheme.dp(context, 4f), maxBarW * progress)
            val barH = LuxuryTheme.dp(context, 2.5f)
            progRect.set(
                rect.left + barMargin,
                rect.bottom - barH - LuxuryTheme.dp(context, 2.2f),
                rect.left + barMargin + fillW,
                rect.bottom - LuxuryTheme.dp(context, 2.2f)
            )
            progPaint.shader = LinearGradient(
                progRect.left, progRect.top, progRect.right, progRect.bottom,
                Color.parseColor("#F59E0B"),
                Color.parseColor("#FCD34D"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(progRect, barH / 2f, barH / 2f, progPaint)
        }

        // 5. Chunky Clay Rim (2.0dp)
        borderPaint.strokeWidth = LuxuryTheme.dp(context, 2.0f)
        borderPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            if (isDark) Color.parseColor("#34D399") else Color.parseColor("#A7F3D0"),
            if (isDark) Color.parseColor("#10B981") else Color.parseColor("#6EE7B7"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, radius, radius, borderPaint)

        // Status Indicator Bubble Dot (Fresh Mint & Honey Amber)
        val (dotColor, glowColor) = when (status) {
            Status.WORKING -> Color.parseColor("#10B981") to Color.argb(110, 16, 185, 129)
            Status.LUNCH -> Color.parseColor("#F59E0B") to Color.argb(105, 245, 158, 11)
            Status.BEFORE -> Color.parseColor("#D97706") to Color.argb(85, 217, 119, 6)
            Status.FINISHED -> Color.parseColor("#059669") to Color.argb(95, 16, 185, 129)
            Status.REST -> Color.parseColor("#6B7280") to Color.argb(60, 107, 114, 128)
        }
        val cy = height / 2f
        val dotX = LuxuryTheme.dp(context, 16f)
        dotPaint.color = glowColor
        canvas.drawCircle(dotX, cy, LuxuryTheme.dp(context, 6.5f), dotPaint)
        dotPaint.color = dotColor
        canvas.drawCircle(dotX, cy, LuxuryTheme.dp(context, 3.8f), dotPaint)
        dotPaint.color = Color.WHITE
        canvas.drawCircle(dotX - LuxuryTheme.dp(context, 1.0f), cy - LuxuryTheme.dp(context, 1.0f), LuxuryTheme.dp(context, 1.2f), dotPaint)

        // Per-digit vertical rolling & thousandths continuous ticker
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

        val baseColor = if (isDark) Color.parseColor("#ECFDF5") else Color.parseColor("#064E3B")
        val tickerColor = if (isDark) Color.parseColor("#FBBF24") else Color.parseColor("#F59E0B")

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
 * Friendly rounded bold amount roller for MainActivity dashboard.
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
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.01f
    }
    private val milliPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        letterSpacing = 0.01f
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
        val fgPri = if (isDark) Color.parseColor("#ECFDF5") else Color.parseColor("#064E3B")
        val amberAccent = if (isDark) Color.parseColor("#FBBF24") else Color.parseColor("#F59E0B")

        textPaint.textSize = LuxuryTheme.dp(context, 38f)
        textPaint.color = fgPri
        milliPaint.textSize = LuxuryTheme.dp(context, 38f)
        milliPaint.color = amberAccent

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

                p.color = amberAccent
                canvas.drawText(d1, curX + (colW - w1) / 2f, baseLineY - slideDist * fraction, p)
                canvas.drawText(d2, curX + (colW - w2) / 2f, baseLineY + slideDist * (1f - fraction), p)
            } else if (a == b) {
                if (b != ' ') {
                    p.color = if (isMilli) amberAccent else fgPri
                    val wb = p.measureText(b.toString())
                    canvas.drawText(b.toString(), curX + (colW - wb) / 2f, baseLineY, p)
                }
            } else {
                val baseCol = if (isMilli) amberAccent else fgPri
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
