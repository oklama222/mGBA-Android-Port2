package com.example.mgbalink

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View

/**
 * Holds the current frame as a Bitmap and draws it scaled up with nearest-
 * neighbor filtering (crisp pixels rather than a blurry smoothed image —
 * the traditional look for scaled-up retro console output).
 *
 * The native side writes directly into [frameBitmap]'s pixel memory each
 * frame (see nativeRunFrameAndRender); this view's only job is to blit that
 * bitmap to the screen, scaled and centered.
 */
class EmulatorView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    constructor(context: Context) : this(context, null)

    var frameBitmap: Bitmap? = null
        private set

    private val paint = Paint().apply {
        isFilterBitmap = false // nearest-neighbor: keep pixels crisp when scaled up
        isAntiAlias = false
    }
    private val destRect = Rect()

    fun ensureBitmap(width: Int, height: Int): Bitmap {
        val existing = frameBitmap
        if (existing != null && existing.width == width && existing.height == height) {
            return existing
        }
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setHasAlpha(false) // the core doesn't meaningfully set alpha; treat as always-opaque
        frameBitmap = bmp
        return bmp
    }

    /** Call after nativeRunFrameAndRender() has written into the bitmap. */
    fun onFrameRendered() {
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = frameBitmap ?: return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        if (viewW <= 0 || viewH <= 0) return

        // Aspect-fit, integer-scaled when possible for the crispest result.
        val scale = minOf(viewW / bmpW, viewH / bmpH)
        val drawW = bmpW * scale
        val drawH = bmpH * scale
        val left = ((viewW - drawW) / 2f).toInt()
        val top = ((viewH - drawH) / 2f).toInt()
        destRect.set(left, top, left + drawW.toInt(), top + drawH.toInt())

        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(bmp, null, destRect, paint)
    }
}
