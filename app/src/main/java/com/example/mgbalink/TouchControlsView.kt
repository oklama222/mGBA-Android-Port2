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
 * On-screen D-pad + A/B/L/R/Start/Select.
 *
 * Three layout modes (set via [setLayout]):
 *  - "default"     — D-pad bottom-left, A/B bottom-right.
 *  - "left_handed" — D-pad bottom-right, A/B bottom-left.
 *  - "custom"      — Button centers read from AppPrefs (set by LayoutEditorActivity).
 *
 * Multi-touch is fully supported: every active pointer is checked on every event
 * and the full button-down/up diff is applied atomically.
 */
class TouchControlsView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    private var layoutMode: String = "default"

    fun setLayout(mode: String) {
        layoutMode = mode
        requestLayout()
        invalidate()
    }

    private var lastActiveKeys: Set<Int> = emptySet()

    private val dpadRect   = RectF()
    private val btnA       = RectF()
    private val btnB       = RectF()
    private val btnL       = RectF()
    private val btnR       = RectF()
    private val btnStart   = RectF()
    private val btnSelect  = RectF()

    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255); style = Paint.Style.FILL
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255); style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0); textAlign = Paint.Align.CENTER
    }

    // ── Layout calculation ────────────────────────────────────────────────────

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w == 0 || h == 0) return
        when (layoutMode) {
            "custom"      -> applyCustomLayout(w, h)
            "left_handed" -> applyDefaultLayout(w, h, leftHanded = true)
            else          -> applyDefaultLayout(w, h, leftHanded = false)
        }
        labelPaint.textSize = min(w, h) * 0.030f
    }

    private fun applyDefaultLayout(w: Int, h: Int, leftHanded: Boolean) {
        val m        = min(w, h) * 0.04f
        val dpadSize = min(w, h) * 0.42f
        val btnSize  = dpadSize * 0.42f

        // D-pad
        if (leftHanded)
            dpadRect.set(w - m - dpadSize, h - dpadSize - m, w - m, h - m)
        else
            dpadRect.set(m, h - dpadSize - m, m + dpadSize, h - m)

        // A / B
        if (leftHanded) {
            btnA.set(m, h - btnSize * 1.3f - m, m + btnSize, h - btnSize * 0.3f - m)
            btnB.set(m + btnSize * 1.1f, h - btnSize * 0.5f - m, m + btnSize * 2.1f, h - m)
        } else {
            btnA.set(w - m - btnSize, h - btnSize * 1.3f - m, w - m, h - btnSize * 0.3f - m)
            btnB.set(w - m - btnSize * 2.1f, h - btnSize * 0.5f - m, w - m - btnSize * 1.1f, h - m + btnSize * 0.5f - btnSize)
        }

        // L / R — always top corners (same as physical GBA)
        val sw = w * 0.16f; val sh = h * 0.08f
        btnL.set(m, m, m + sw, m + sh)
        btnR.set(w - m - sw, m, w - m, m + sh)

        // Start / Select — bottom centre
        val smW = w * 0.12f; val smH = h * 0.05f
        val cx  = w / 2f
        btnSelect.set(cx - smW - 8f, h - m - smH, cx - 8f, h - m)
        btnStart.set(cx + 8f, h - m - smH, cx + smW + 8f, h - m)
    }

    /**
     * Reads button centres from AppPrefs (stored as normalized 0..1 fractions).
     * Falls back to the default layout value for any button that has no saved position.
     */
    private fun applyCustomLayout(w: Int, h: Int) {
        // First apply defaults so every button has a base position.
        applyDefaultLayout(w, h, leftHanded = false)

        fun centreRect(rect: RectF, key: String) {
            val defNx = (rect.centerX() / w)
            val defNy = (rect.centerY() / h)
            val nx = AppPrefs.getButtonCenterX(context, key, defNx)
            val ny = AppPrefs.getButtonCenterY(context, key, defNy)
            val halfW = rect.width() / 2f
            val halfH = rect.height() / 2f
            rect.set(nx * w - halfW, ny * h - halfH, nx * w + halfW, ny * h + halfH)
        }

        centreRect(dpadRect, "dpad")
        centreRect(btnA,     "a")
        centreRect(btnB,     "b")
        centreRect(btnL,     "l")
        centreRect(btnR,     "r")
        centreRect(btnStart, "start")
        centreRect(btnSelect,"select")
    }

    // ── Touch handling ────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Tell the parent not to intercept while we have fingers on screen.
        // This prevents the overlay LinearLayout from stealing pointer events.
        parent?.requestDisallowInterceptTouchEvent(true)

        val action = event.actionMasked

        if (action == MotionEvent.ACTION_CANCEL) {
            applyActiveKeys(emptySet())
            return true
        }

        // The pointer that is in the process of lifting is still in the list for
        // exactly this one event; exclude it so we don't keep its key held.
        val releasingIndex = when (action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> event.actionIndex
            else -> -1
        }

        val active = mutableSetOf<Int>()
        for (i in 0 until event.pointerCount) {
            if (i == releasingIndex) continue
            active.addAll(keysAt(event.getX(i), event.getY(i)))
        }
        applyActiveKeys(active)

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }

    private fun keysAt(x: Float, y: Float): Set<Int> {
        val keys = mutableSetOf<Int>()
        if (dpadRect.contains(x, y)) {
            val relX = (x - dpadRect.centerX()) / (dpadRect.width() / 2f)
            val relY = (y - dpadRect.centerY()) / (dpadRect.height() / 2f)
            val dz = 0.2f
            if (relX < -dz) keys.add(NativeBridge.KEY_LEFT)
            if (relX >  dz) keys.add(NativeBridge.KEY_RIGHT)
            if (relY < -dz) keys.add(NativeBridge.KEY_UP)
            if (relY >  dz) keys.add(NativeBridge.KEY_DOWN)
        }
        if (btnA.contains(x, y))      keys.add(NativeBridge.KEY_A)
        if (btnB.contains(x, y))      keys.add(NativeBridge.KEY_B)
        if (btnL.contains(x, y))      keys.add(NativeBridge.KEY_L)
        if (btnR.contains(x, y))      keys.add(NativeBridge.KEY_R)
        if (btnStart.contains(x, y))  keys.add(NativeBridge.KEY_START)
        if (btnSelect.contains(x, y)) keys.add(NativeBridge.KEY_SELECT)
        return keys
    }

    private fun applyActiveKeys(newKeys: Set<Int>) {
        for (key in newKeys) if (key !in lastActiveKeys) NativeBridge.nativeAddKey(key)
        for (key in lastActiveKeys) if (key !in newKeys) NativeBridge.nativeClearKey(key)
        lastActiveKeys = newKeys
        invalidate()
    }

    private fun paintFor(key: Int) = if (key in lastActiveKeys) activePaint else idlePaint

    // ── Drawing ───────────────────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // D-pad cross
        val cx = dpadRect.centerX(); val cy = dpadRect.centerY()
        val armW = dpadRect.width() * 0.34f
        canvas.drawRoundRect(dpadRect.left, cy - armW / 2, dpadRect.right, cy + armW / 2, 16f, 16f, idlePaint)
        canvas.drawRoundRect(cx - armW / 2, dpadRect.top, cx + armW / 2, dpadRect.bottom, 16f, 16f, idlePaint)
        drawTriangle(canvas, cx, dpadRect.top,    cx - armW/2, cy - armW/2, cx + armW/2, cy - armW/2, paintFor(NativeBridge.KEY_UP))
        drawTriangle(canvas, cx, dpadRect.bottom, cx - armW/2, cy + armW/2, cx + armW/2, cy + armW/2, paintFor(NativeBridge.KEY_DOWN))
        drawTriangle(canvas, dpadRect.left,  cy,  cx - armW/2, cy - armW/2, cx - armW/2, cy + armW/2, paintFor(NativeBridge.KEY_LEFT))
        drawTriangle(canvas, dpadRect.right, cy,  cx + armW/2, cy - armW/2, cx + armW/2, cy + armW/2, paintFor(NativeBridge.KEY_RIGHT))

        // A / B
        canvas.drawCircle(btnA.centerX(), btnA.centerY(), btnA.width()/2, paintFor(NativeBridge.KEY_A))
        canvas.drawText("A", btnA.centerX(), btnA.centerY() + labelPaint.textSize/3, labelPaint)
        canvas.drawCircle(btnB.centerX(), btnB.centerY(), btnB.width()/2, paintFor(NativeBridge.KEY_B))
        canvas.drawText("B", btnB.centerX(), btnB.centerY() + labelPaint.textSize/3, labelPaint)

        // L / R
        canvas.drawRoundRect(btnL, 12f, 12f, paintFor(NativeBridge.KEY_L))
        canvas.drawText("L", btnL.centerX(), btnL.centerY() + labelPaint.textSize/3, labelPaint)
        canvas.drawRoundRect(btnR, 12f, 12f, paintFor(NativeBridge.KEY_R))
        canvas.drawText("R", btnR.centerX(), btnR.centerY() + labelPaint.textSize/3, labelPaint)

        // Start / Select
        canvas.drawRoundRect(btnSelect, 10f, 10f, paintFor(NativeBridge.KEY_SELECT))
        canvas.drawText("SELECT", btnSelect.centerX(), btnSelect.centerY() + labelPaint.textSize/3, labelPaint)
        canvas.drawRoundRect(btnStart, 10f, 10f, paintFor(NativeBridge.KEY_START))
        canvas.drawText("START", btnStart.centerX(), btnStart.centerY() + labelPaint.textSize/3, labelPaint)
    }

    private fun drawTriangle(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, paint: Paint) {
        val path = Path()
        path.moveTo(x1, y1); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close()
        canvas.drawPath(path, paint)
    }
}
