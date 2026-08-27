package com.skysplatwoomy.petblud

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java.util.Locale
import kotlin.math.roundToInt

data class BubbleColorSelection(
    val color: Int,
    val opacityPercent: Double,
)

object BubbleColorPickerDialog {
    fun show(
        context: Context,
        title: String,
        initialColor: Int,
        initialOpacityPercent: Double,
        onSelected: (BubbleColorSelection) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        fun dp(value: Int) = (value * density).toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor or (0xFF shl 24), hsv)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val preview = View(context).apply { minimumHeight = dp(64) }
        container.addView(
            preview,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(64)).apply {
                bottomMargin = dp(12)
            },
        )

        val hueLabel = label(context)
        val hue = continuousSlider(context, 0f, 360f, hsv[0])
        val saturationLabel = label(context)
        val saturation = continuousSlider(context, 0f, 100f, hsv[1] * 100f)
        val brightnessLabel = label(context)
        val brightness = continuousSlider(context, 0f, 100f, hsv[2] * 100f)
        val opacityLabel = label(context)
        val opacity = continuousSlider(
            context,
            PetSettings.MIN_BUBBLE_OPACITY_PERCENT.toFloat(),
            PetSettings.MAX_BUBBLE_OPACITY_PERCENT.toFloat(),
            initialOpacityPercent.toFloat(),
        )
        listOf(
            hueLabel,
            hue,
            saturationLabel,
            saturation,
            brightnessLabel,
            brightness,
            opacityLabel,
            opacity,
        ).forEach(container::addView)

        var selectedColor = initialColor or (0xFF shl 24)
        fun refresh() {
            hsv[0] = hue.value
            hsv[1] = saturation.value / 100f
            hsv[2] = brightness.value / 100f
            selectedColor = Color.HSVToColor(hsv)
            val previewAlpha = (255 * opacity.value / 100f).roundToInt().coerceIn(0, 255)
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(ColorUtils.setAlphaComponent(selectedColor, previewAlpha))
                setStroke(dp(1), ColorUtils.setAlphaComponent(Color.WHITE, 80))
            }
            hueLabel.text = context.getString(R.string.color_hue_value, hue.value.roundToInt())
            saturationLabel.text = context.getString(
                R.string.color_saturation_value,
                saturation.value.roundToInt(),
            )
            brightnessLabel.text = context.getString(
                R.string.color_brightness_value,
                brightness.value.roundToInt(),
            )
            opacityLabel.text = context.getString(
                R.string.color_opacity_value,
                opacity.value.roundToInt(),
            )
        }
        listOf(hue, saturation, brightness, opacity).forEach { control ->
            control.addOnChangeListener { _, _, _ -> refresh() }
        }
        bindTypedValue(hueLabel, context, R.string.edit_hue, hue, 0.0, 360.0)
        bindTypedValue(saturationLabel, context, R.string.edit_saturation, saturation, 0.0, 100.0)
        bindTypedValue(brightnessLabel, context, R.string.edit_brightness, brightness, 0.0, 100.0)
        bindTypedValue(
            opacityLabel,
            context,
            R.string.edit_colour_opacity,
            opacity,
            PetSettings.MIN_BUBBLE_OPACITY_PERCENT,
            PetSettings.MAX_BUBBLE_OPACITY_PERCENT,
        )
        refresh()

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                onSelected(BubbleColorSelection(selectedColor, opacity.value.toDouble()))
            }
            .show()
    }

    fun formatColor(color: Int): String = String.format(
        Locale.ROOT,
        "#%06X",
        color and 0xFFFFFF,
    )

    private fun label(context: Context) = TextView(context).apply {
        setTextAppearance(R.style.TextAppearance_PetBlud_Body)
        gravity = Gravity.CENTER_VERTICAL
        minHeight = (48 * resources.displayMetrics.density).roundToInt()
        isClickable = true
        isFocusable = true
    }

    private fun continuousSlider(
        context: Context,
        from: Float,
        to: Float,
        initial: Float,
    ) = Slider(context).apply {
        valueFrom = from
        valueTo = to
        stepSize = 0f
        value = initial.coerceIn(from, to)
    }

    private fun bindTypedValue(
        label: TextView,
        context: Context,
        titleResource: Int,
        slider: Slider,
        minimum: Double,
        maximum: Double,
    ) {
        label.setOnClickListener {
            NumericInputDialog.show(
                context = context,
                title = context.getString(titleResource),
                currentValue = slider.value.toDouble(),
                minimum = minimum,
                maximum = maximum,
                decimalPlaces = 0,
            ) { slider.value = it.toFloat() }
        }
    }
}
