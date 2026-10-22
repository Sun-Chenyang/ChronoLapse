package com.chronolapse.app.recorder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws the current time as a watermark (white text with black outline,
 * bottom right corner) onto image canvases. For high frequency video overlay
 * frames the text is pre-rendered once per second and blitted.
 */
object Watermark {

    private const val TEXT_LIFETIME_MS = 1000L
    private const val TEXT_SIZE_RATIO = 0.04f
    private const val MARGIN_RATIO = 0.025f

    private var textBitmap: Bitmap? = null
    private var textTime = 0L
    private val formatLock = Any()

    /** direct draw for image post processing (a few calls per second) */
    fun draw(canvas: Canvas, whenMs: Long, rotationDegrees: Int) {
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        val uprightW = (if (swapped) canvas.height else canvas.width).toFloat()
        val uprightH = (if (swapped) canvas.width else canvas.height).toFloat()

        val size = uprightW * TEXT_SIZE_RATIO
        val margin = uprightW * MARGIN_RATIO
        val text = format(whenMs)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            color = Color.WHITE
            textSize = size
        }
        val stroke = Paint(fill).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = size / 5f
        }

        val textWidth = fill.measureText(text)
        val x = uprightW - margin - textWidth
        val y = uprightH - margin

        canvas.save()
        applyRotation(canvas, rotationDegrees)
        canvas.drawText(text, x, y, stroke)
        canvas.drawText(text, x, y, fill)
        canvas.restore()
    }

    /** cached draw for camera frame rate overlays */
    fun drawCached(canvas: Canvas, whenMs: Long, rotationDegrees: Int) {
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        val uprightW = (if (swapped) canvas.height else canvas.width).toFloat()
        val uprightH = (if (swapped) canvas.width else canvas.height).toFloat()

        val size = uprightW * TEXT_SIZE_RATIO
        val margin = uprightW * MARGIN_RATIO

        val slot = whenMs / TEXT_LIFETIME_MS * TEXT_LIFETIME_MS
        val bmp = synchronized(this) {
            if (textBitmap == null || textTime != slot) {
                textTime = slot
                renderTextBitmap(format(whenMs), Math.ceil((size * 1.2f).toDouble()).toInt())
            }
            textBitmap
        } ?: return

        canvas.save()
        applyRotation(canvas, rotationDegrees)
        canvas.drawBitmap(bmp, uprightW - margin - bmp.width, uprightH - margin - bmp.height, null)
        canvas.restore()
    }

    private fun renderTextBitmap(text: String, textHeight: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            color = Color.WHITE
            textSize = textHeight * 0.82f
        }
        val stroke = Paint(fill).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val strokeWidth = textHeight * 0.16f
        stroke.strokeWidth = strokeWidth

        val textWidth = fill.measureText(text)
        val padX = strokeWidth * 2f
        val padTop = strokeWidth * 1.5f
        val padBottom = strokeWidth

        val bmp = Bitmap.createBitmap(
            Math.max(1, (textWidth + padX * 2).toInt()),
            Math.max(1, (textHeight + padTop + padBottom).toInt()),
            Bitmap.Config.ARGB_8888
        )
        val c = Canvas(bmp)
        val baseline = padTop + textHeight * 0.78f
        c.drawText(text, padX, baseline, stroke)
        c.drawText(text, padX, baseline, fill)
        textBitmap = bmp
    }

    /** map upright coordinates onto the sensor oriented overlay canvas */
    private fun applyRotation(canvas: Canvas, rotationDegrees: Int) {
        if (rotationDegrees == 0) return
        val swapped = rotationDegrees == 90 || rotationDegrees == 270
        val uprightW = (if (swapped) canvas.height else canvas.width).toFloat()
        val uprightH = (if (swapped) canvas.width else canvas.height).toFloat()
        val cx = canvas.width / 2f
        val cy = canvas.height / 2f
        canvas.rotate(-rotationDegrees.toFloat(), cx, cy)
        canvas.translate(cx - uprightW / 2f, cy - uprightH / 2f)
    }

    private fun format(whenMs: Long): String = synchronized(formatLock) {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(whenMs))
    }
}
