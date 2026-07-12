package com.example.mgbalink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/**
 * Holds the current frame as a Bitmap and draws it to the screen.
 *
 * Two rendering modes controlled by [stretchToFit]:
 *  - false (default): aspect-fit, integer-scaled when possible — crisp pixels, black bars.
 *  - true: stretch to fill the full view, ignoring the GBA's native 3:2 ratio.
 */
class EmulatorView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    /** When true, the frame is stretched to fill the whole screen. */
    var stretchToFit: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var frameBitmap: Bitmap? = null
        private set

    private val paint = Paint().apply {
        isFilterBitmap = false // nearest-neighbor: keep pixels crisp when scaled up
        isAntiAlias = false
    }
    private val destRect = Rect()

    fun ensureBitmap(width: Int, height: Int): Bitmap {
        val existing = frameBitmap
        if (existing != null && existing.width == width && existing.height == height) return existing
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setHasAlpha(false)
        frameBitmap = bmp
        return bmp
    }

    /** Call after nativeRunFrameAndRender() has written into the bitmap. */
    fun onFrameRendered() = postInvalidateOnAnimation()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frameBitmap ?: return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0 || viewH <= 0) return

        canvas.drawColor(android.graphics.Color.BLACK)

        if (stretchToFit) {
            // Fill the entire view.
            destRect.set(0, 0, width, height)
        } else {
            // Aspect-fit, integer-scaled when possible.
            val bmpW = bmp.width.toFloat()
            val bmpH = bmp.height.toFloat()
            val scale = minOf(viewW / bmpW, viewH / bmpH)
            val drawW = bmpW * scale
            val drawH = bmpH * scale
            val left = ((viewW - drawW) / 2f).toInt()
            val top  = ((viewH - drawH) / 2f).toInt()
            destRect.set(left, top, left + drawW.toInt(), top + drawH.toInt())
        }

        canvas.drawBitmap(bmp, null, destRect, paint)
    }
}
