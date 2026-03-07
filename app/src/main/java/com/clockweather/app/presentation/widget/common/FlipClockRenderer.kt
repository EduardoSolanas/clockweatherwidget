package com.clockweather.app.presentation.widget.common

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.SparseArray

data class FlipClockBitmaps(
    val hourTens: Bitmap,
    val hourOnes: Bitmap,
    val minuteTens: Bitmap,
    val minuteOnes: Bitmap
)

class FlipClockRenderer(
    private val digitWidth: Int = 80,
    private val digitHeight: Int = 100,
    private val customTypeface: Typeface? = null
) {

    // Cache to avoid re-rendering unchanged digits
    private val digitCache = SparseArray<Bitmap>(10)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC1A1A2E")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = digitHeight * 0.65f
        textAlign = Paint.Align.CENTER
        typeface = customTypeface ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60000000")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val cornerRadius = digitWidth * 0.12f

    private fun renderDigit(digit: Int): Bitmap {
        // Return cached bitmap if available
        digitCache[digit]?.let { return it }

        val bitmap = Bitmap.createBitmap(digitWidth, digitHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val rect = RectF(0f, 0f, digitWidth.toFloat(), digitHeight.toFloat())

        // Draw background rounded rect
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, backgroundPaint)

        // Draw digit text centered
        val textX = digitWidth / 2f
        val textY = (digitHeight / 2f) - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(digit.toString(), textX, textY, textPaint)

        // Draw horizontal divider line (the "flip" separator)
        val midY = digitHeight / 2f
        canvas.drawLine(0f, midY, digitWidth.toFloat(), midY, shadowPaint)
        canvas.drawLine(0f, midY, digitWidth.toFloat(), midY, dividerPaint)

        digitCache.put(digit, bitmap)
        return bitmap
    }

    fun renderTime(hour: Int, minute: Int, is24Hour: Boolean): FlipClockBitmaps {
        val displayHour = if (!is24Hour && hour > 12) hour - 12
        else if (!is24Hour && hour == 0) 12
        else hour

        val hourTens = (displayHour / 10) % 10
        val hourOnes = displayHour % 10
        val minuteTens = minute / 10
        val minuteOnes = minute % 10

        return FlipClockBitmaps(
            hourTens = renderDigit(hourTens),
            hourOnes = renderDigit(hourOnes),
            minuteTens = renderDigit(minuteTens),
            minuteOnes = renderDigit(minuteOnes)
        )
    }

    fun clearCache() {
        for (i in 0 until digitCache.size()) {
            digitCache.valueAt(i).recycle()
        }
        digitCache.clear()
    }
}

