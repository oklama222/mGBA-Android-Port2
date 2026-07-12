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

/**
 * On-screen D-pad + A/B/L/R/Start/Select. Handles multiple simultaneous
 * touches (so e.g. holding a diagonal on the D-pad while pressing A works),
 * by recomputing the full set of "keys that should be down" from every
 * active pointer on each touch event and diffing that against the previous
 * frame's set.
 */
class TouchControlsView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    private var lastActiveKeys: Set<Int> = emptySet()

    private val dpadRect = RectF()
    private val btnA = RectF()
    private val btnB = RectF()
    private val btnL = RectF()
    private val btnR = RectF()
    private val btnStart = RectF()
    private val btnSelect = RectF()

    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(170, 255, 255, 255)
        style = Paint.Style.FILL
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0)
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val margin = minOf(w, h) * 0.04f
        val dpadSize = minOf(w, h) * 0.42f
        dpadRect.set(margin, h - dpadSize - margin, margin + dpadSize, h - margin)

        val btnSize = dpadSize * 0.42f
        val abRight = w - margin
        val abBottom = h - margin
        btnA.set(abRight - btnSize, abBottom - btnSize * 1.3f, abRight, abBottom - btnSize * 0.3f)
        btnB.set(abRight - btnSize * 2.1f, abBottom - btnSize * 0.5f, abRight - btnSize * 1.1f, abBottom + btnSize * 0.5f - btnSize)

        val shoulderW = w * 0.16f
        val shoulderH = h * 0.08f
        btnL.set(margin, margin, margin + shoulderW, margin + shoulderH)
        btnR.set(w - margin - shoulderW, margin, w - margin, margin + shoulderH)

        val smallW = w * 0.12f
        val smallH = h * 0.05f
        val centerX = w / 2f
        btnSelect.set(centerX - smallW - 8f, h - margin - smallH, centerX - 8f, h - margin)
        btnStart.set(centerX + 8f, h - margin - smallH, centerX + smallW + 8f, h - margin)

        labelPaint.textSize = smallH * 0.55f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_CANCEL) {
            applyActiveKeys(emptySet())
            return true
        }

        // The pointer in the middle of going up is still in the pointer list
        // for this one event; exclude it from "currently held" this frame.
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
        return true
    }

    private fun keysAt(x: Float, y: Float): Set<Int> {
        val keys = mutableSetOf<Int>()

        if (dpadRect.contains(x, y)) {
            val relX = (x - dpadRect.centerX()) / (dpadRect.width() / 2f)
            val relY = (y - dpadRect.centerY()) / (dpadRect.height() / 2f)
            val deadzone = 0.2f
            if (relX < -deadzone) keys.add(NativeBridge.KEY_LEFT)
            if (relX > deadzone) keys.add(NativeBridge.KEY_RIGHT)
            if (relY < -deadzone) keys.add(NativeBridge.KEY_UP)
            if (relY > deadzone) keys.add(NativeBridge.KEY_DOWN)
        }
        if (btnA.contains(x, y)) keys.add(NativeBridge.KEY_A)
        if (btnB.contains(x, y)) keys.add(NativeBridge.KEY_B)
        if (btnL.contains(x, y)) keys.add(NativeBridge.KEY_L)
        if (btnR.contains(x, y)) keys.add(NativeBridge.KEY_R)
        if (btnStart.contains(x, y)) keys.add(NativeBridge.KEY_START)
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // D-pad: a plus-shaped background plus 4 directional arrows,
        // individually highlighted while their direction is active.
        val cx = dpadRect.centerX()
        val cy = dpadRect.centerY()
        val armW = dpadRect.width() * 0.34f
        canvas.drawRoundRect(dpadRect.left, cy - armW / 2, dpadRect.right, cy + armW / 2, 16f, 16f, idlePaint)
        canvas.drawRoundRect(cx - armW / 2, dpadRect.top, cx + armW / 2, dpadRect.bottom, 16f, 16f, idlePaint)

        drawTriangle(canvas, cx, dpadRect.top, cx - armW / 2, cy - armW / 2, cx + armW / 2, cy - armW / 2, paintFor(NativeBridge.KEY_UP))
        drawTriangle(canvas, cx, dpadRect.bottom, cx - armW / 2, cy + armW / 2, cx + armW / 2, cy + armW / 2, paintFor(NativeBridge.KEY_DOWN))
        drawTriangle(canvas, dpadRect.left, cy, cx - armW / 2, cy - armW / 2, cx - armW / 2, cy + armW / 2, paintFor(NativeBridge.KEY_LEFT))
        drawTriangle(canvas, dpadRect.right, cy, cx + armW / 2, cy - armW / 2, cx + armW / 2, cy + armW / 2, paintFor(NativeBridge.KEY_RIGHT))

        canvas.drawCircle(btnA.centerX(), btnA.centerY(), btnA.width() / 2, paintFor(NativeBridge.KEY_A))
        canvas.drawText("A", btnA.centerX(), btnA.centerY() + labelPaint.textSize / 3, labelPaint)
        canvas.drawCircle(btnB.centerX(), btnB.centerY(), btnB.width() / 2, paintFor(NativeBridge.KEY_B))
        canvas.drawText("B", btnB.centerX(), btnB.centerY() + labelPaint.textSize / 3, labelPaint)

        canvas.drawRoundRect(btnL, 12f, 12f, paintFor(NativeBridge.KEY_L))
        canvas.drawText("L", btnL.centerX(), btnL.centerY() + labelPaint.textSize / 3, labelPaint)
        canvas.drawRoundRect(btnR, 12f, 12f, paintFor(NativeBridge.KEY_R))
        canvas.drawText("R", btnR.centerX(), btnR.centerY() + labelPaint.textSize / 3, labelPaint)

        canvas.drawRoundRect(btnSelect, 10f, 10f, paintFor(NativeBridge.KEY_SELECT))
        canvas.drawText("SELECT", btnSelect.centerX(), btnSelect.centerY() + labelPaint.textSize / 3, labelPaint)
        canvas.drawRoundRect(btnStart, 10f, 10f, paintFor(NativeBridge.KEY_START))
        canvas.drawText("START", btnStart.centerX(), btnStart.centerY() + labelPaint.textSize / 3, labelPaint)
    }

    private fun drawTriangle(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, paint: Paint) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        path.lineTo(x3, y3)
        path.close()
        canvas.drawPath(path, paint)
    }
}
