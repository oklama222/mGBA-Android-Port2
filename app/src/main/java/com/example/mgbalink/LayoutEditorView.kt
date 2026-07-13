package com.example.mgbalink

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * Full-screen drag editor for the virtual gamepad layout.
 *
 * Each button is drawn at its current position (read from AppPrefs on first
 * layout, falling back to the default positions). Dragging a button moves it;
 * lifting the finger saves the new normalized position (0..1 × 0..1) to prefs.
 *
 * Call [resetToDefaults] to wipe custom positions and revert.
 */
class LayoutEditorView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    // ── Button descriptors ────────────────────────────────────────────────────

    data class Btn(
        val key: String,
        val label: String,
        var cx: Float = 0f,
        var cy: Float = 0f,
        val radiusFraction: Float   // radius as fraction of min(w,h)
    )

    private val buttons = listOf(
        Btn("dpad",   "D-PAD",  radiusFraction = 0.21f),
        Btn("a",      "A",      radiusFraction = 0.09f),
        Btn("b",      "B",      radiusFraction = 0.09f),
        Btn("l",      "L",      radiusFraction = 0.07f),
        Btn("r",      "R",      radiusFraction = 0.07f),
        Btn("select", "SELECT", radiusFraction = 0.055f),
        Btn("start",  "START",  radiusFraction = 0.055f)
    )

    private var dragBtn: Btn? = null

    // ── Default normalized positions (match TouchControlsView default layout) ─

    private fun defaultNormX(key: String) = when (key) {
        "dpad"   -> 0.14f
        "a"      -> 0.87f
        "b"      -> 0.79f
        "l"      -> 0.06f
        "r"      -> 0.94f
        "select" -> 0.43f
        "start"  -> 0.57f
        else     -> 0.5f
    }

    private fun defaultNormY(key: String) = when (key) {
        "dpad"   -> 0.72f
        "a"      -> 0.65f
        "b"      -> 0.80f
        "l"      -> 0.10f
        "r"      -> 0.10f
        "select" -> 0.90f
        "start"  -> 0.90f
        else     -> 0.5f
    }

    // ── Paints ────────────────────────────────────────────────────────────────

    private val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(160, 80, 140, 220)
        style = Paint.Style.FILL
    }
    private val dragPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 120, 200, 80)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 200, 200, 200)
        textAlign = Paint.Align.CENTER
    }

    // ── Sizing & layout ───────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val unit = min(w, h).toFloat()
        labelPaint.textSize = unit * 0.045f
        hintPaint.textSize  = unit * 0.030f
        for (btn in buttons) {
            val normX = AppPrefs.getButtonCenterX(context, btn.key, defaultNormX(btn.key))
            val normY = AppPrefs.getButtonCenterY(context, btn.key, defaultNormY(btn.key))
            btn.cx = normX * w
            btn.cy = normY * h
        }
    }

    fun resetToDefaults() {
        AppPrefs.resetButtonPositions(context)
        val w = width; val h = height
        for (btn in buttons) {
            btn.cx = defaultNormX(btn.key) * w
            btn.cy = defaultNormY(btn.key) * h
        }
        invalidate()
    }

    // ── Touch ─────────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val unit = min(width, height).toFloat()
                dragBtn = buttons.minByOrNull { btn ->
                    val dx = btn.cx - event.x; val dy = btn.cy - event.y
                    dx * dx + dy * dy
                }?.takeIf { btn ->
                    val dx = btn.cx - event.x; val dy = btn.cy - event.y
                    val r = btn.radiusFraction * unit * 1.5f  // generous hitbox
                    dx * dx + dy * dy <= r * r
                }
            }
            MotionEvent.ACTION_MOVE -> {
                dragBtn?.let { btn ->
                    btn.cx = event.x.coerceIn(0f, width.toFloat())
                    btn.cy = event.y.coerceIn(0f, height.toFloat())
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragBtn?.let { btn ->
                    AppPrefs.setButtonCenter(context, btn.key,
                        btn.cx / width, btn.cy / height)
                }
                dragBtn = null
                invalidate()
            }
        }
        return true
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#111122"))

        // Hint text
        hintPaint.color = Color.argb(100, 180, 180, 180)
        canvas.drawText("Drag buttons to reposition", width / 2f, height * 0.05f, hintPaint)

        val unit = min(width, height).toFloat()

        for (btn in buttons) {
            val r = btn.radiusFraction * unit
            val paint = if (btn === dragBtn) dragPaint else btnPaint

            when (btn.key) {
                "dpad" -> drawDpad(canvas, btn.cx, btn.cy, r, paint)
                "l", "r" -> {
                    val rect = RectF(btn.cx - r * 1.2f, btn.cy - r * 0.6f,
                                     btn.cx + r * 1.2f, btn.cy + r * 0.6f)
                    canvas.drawRoundRect(rect, 12f, 12f, paint)
                    canvas.drawRoundRect(rect, 12f, 12f, strokePaint)
                }
                "start", "select" -> {
                    val rect = RectF(btn.cx - r * 1.3f, btn.cy - r * 0.7f,
                                     btn.cx + r * 1.3f, btn.cy + r * 0.7f)
                    canvas.drawRoundRect(rect, 10f, 10f, paint)
                    canvas.drawRoundRect(rect, 10f, 10f, strokePaint)
                }
                else -> {
                    canvas.drawCircle(btn.cx, btn.cy, r, paint)
                    canvas.drawCircle(btn.cx, btn.cy, r, strokePaint)
                }
            }

            labelPaint.color = if (btn === dragBtn) Color.parseColor("#111122") else Color.WHITE
            canvas.drawText(btn.label, btn.cx, btn.cy + labelPaint.textSize * 0.38f, labelPaint)
        }
    }

    private fun drawDpad(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val arm = r * 0.55f
        // Horizontal bar
        canvas.drawRoundRect(cx - r, cy - arm, cx + r, cy + arm, 10f, 10f, paint)
        // Vertical bar
        canvas.drawRoundRect(cx - arm, cy - r, cx + arm, cy + r, 10f, 10f, paint)
        // Stroke
        canvas.drawRoundRect(cx - r, cy - arm, cx + r, cy + arm, 10f, 10f, strokePaint)
        canvas.drawRoundRect(cx - arm, cy - r, cx + arm, cy + r, 10f, 10f, strokePaint)
    }
}

