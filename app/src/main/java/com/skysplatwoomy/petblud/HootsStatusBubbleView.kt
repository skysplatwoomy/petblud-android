package com.skysplatwoomy.petblud

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

@SuppressLint("ViewConstructor")
class HootsStatusBubbleView(context: Context) : LinearLayout(context) {
    private val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.pet_cream)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.hoots_orange)
        style = Paint.Style.STROKE
        strokeWidth = dpFloat(2f)
    }
    private val cardBounds = RectF()
    private val tailPath = Path()
    private val titleView = textView(sizeSp = 14f, maxLines = 1).apply {
        setTypeface(typeface, Typeface.BOLD)
    }
    private val bodyView = textView(sizeSp = 13f, maxLines = 3)
    private val horizontalPadding = dp(14)
    private val verticalPadding = dp(11)
    private val tailHeight = dp(9)
    private val tailHalfWidth = dp(8)
    private val cornerRadius = dpFloat(16f)
    private var tailAtTop = false
    private var tailCenterX = dp(40)
    private var borderColor = PetSettings.DEFAULT_BUBBLE_BORDER_COLOR
    private var fillColor = PetSettings.DEFAULT_BUBBLE_FILL_COLOR
    private var opacityPercent = PetSettings.DEFAULT_BUBBLE_OPACITY_PERCENT

    init {
        orientation = VERTICAL
        setWillNotDraw(false)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(titleView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(bodyView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        updateBubblePadding()
    }

    fun showStatus(status: ChatGptNotificationStatus) {
        titleView.text = status.title
        titleView.visibility = if (status.title.isEmpty()) View.GONE else View.VISIBLE
        bodyView.text = status.body
        bodyView.visibility = if (status.body.isEmpty()) View.GONE else View.VISIBLE
    }

    fun applyAppearance(border: Int, fill: Int, opacity: Double) {
        val normalizedOpacity = opacity.coerceIn(
            PetSettings.MIN_BUBBLE_OPACITY_PERCENT,
            PetSettings.MAX_BUBBLE_OPACITY_PERCENT,
        )
        if (
            borderColor == border && fillColor == fill && opacityPercent == normalizedOpacity
        ) {
            return
        }
        borderColor = border
        fillColor = fill
        opacityPercent = normalizedOpacity
        val alpha = (255 * normalizedOpacity / 100.0).toInt().coerceIn(0, 255)
        strokePaint.color = ColorUtils.setAlphaComponent(border, alpha)
        fillPaint.color = ColorUtils.setAlphaComponent(fill, alpha)
        val textColor = if (ColorUtils.calculateLuminance(fill) < 0.42) {
            Color.WHITE
        } else {
            ContextCompat.getColor(context, R.color.pet_bg)
        }
        titleView.setTextColor(textColor)
        bodyView.setTextColor(textColor)
        invalidate()
    }

    fun setTail(valueAtTop: Boolean, centerX: Int) {
        if (tailAtTop == valueAtTop && tailCenterX == centerX) return
        tailAtTop = valueAtTop
        tailCenterX = centerX
        updateBubblePadding()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val strokeInset = strokePaint.strokeWidth / 2f
        val cardTop = if (tailAtTop) tailHeight.toFloat() else 0f
        val cardBottom = if (tailAtTop) height.toFloat() else height - tailHeight.toFloat()
        cardBounds.set(
            strokeInset,
            cardTop + strokeInset,
            width - strokeInset,
            cardBottom - strokeInset,
        )
        val center = tailCenterX.toFloat().coerceIn(
            tailHalfWidth.toFloat(),
            (width - tailHalfWidth).coerceAtLeast(tailHalfWidth).toFloat(),
        )
        val baseY = if (tailAtTop) cardTop else cardBottom
        val tipY = if (tailAtTop) 0f else height.toFloat()
        tailPath.reset()
        tailPath.moveTo(center - tailHalfWidth, baseY)
        tailPath.lineTo(center, tipY)
        tailPath.lineTo(center + tailHalfWidth, baseY)
        tailPath.close()

        canvas.drawPath(tailPath, fillPaint)
        canvas.drawRoundRect(cardBounds, cornerRadius, cornerRadius, fillPaint)
        canvas.drawRoundRect(cardBounds, cornerRadius, cornerRadius, strokePaint)
        canvas.drawLine(center - tailHalfWidth, baseY, center, tipY, strokePaint)
        canvas.drawLine(center, tipY, center + tailHalfWidth, baseY, strokePaint)
        super.onDraw(canvas)
    }

    private fun updateBubblePadding() {
        setPadding(
            horizontalPadding,
            verticalPadding + if (tailAtTop) tailHeight else 0,
            horizontalPadding,
            verticalPadding + if (tailAtTop) 0 else tailHeight,
        )
    }

    private fun textView(sizeSp: Float, maxLines: Int) = TextView(context).apply {
        setTextColor(ContextCompat.getColor(context, R.color.pet_bg))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        this.maxLines = maxLines
        ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private fun dpFloat(value: Float): Float = value * density
}
