package com.skysplatwoomy.petblud

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import java.util.Locale

object BubbleColorPickerDialog {
    fun show(
        context: Context,
        title: String,
        initialColor: Int,
        onSelected: (Int) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        fun dp(value: Int) = (value * density).toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(initialColor, hsv)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
        }
        val preview = View(context).apply {
            minimumHeight = dp(64)
        }
        val previewParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(64),
        ).apply { bottomMargin = dp(12) }
        container.addView(preview, previewParams)

        val hueLabel = label(context)
        val hue = slider(context, 0f, 360f, 1f, hsv[0])
        val saturationLabel = label(context)
        val saturation = slider(context, 0f, 100f, 1f, hsv[1] * 100f)
        val brightnessLabel = label(context)
        val brightness = slider(context, 0f, 100f, 1f, hsv[2] * 100f)
        listOf(hueLabel, hue, saturationLabel, saturation, brightnessLabel, brightness)
            .forEach(container::addView)

        var selectedColor = initialColor
        fun refresh() {
            hsv[0] = hue.value
            hsv[1] = saturation.value / 100f
            hsv[2] = brightness.value / 100f
            selectedColor = Color.HSVToColor(hsv)
            preview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(selectedColor)
            }
            hueLabel.text = context.getString(R.string.color_hue_value, hue.value.toInt())
            saturationLabel.text = context.getString(
                R.string.color_saturation_value,
                saturation.value.toInt(),
            )
            brightnessLabel.text = context.getString(
                R.string.color_brightness_value,
                brightness.value.toInt(),
            )
        }
        listOf(hue, saturation, brightness).forEach { control ->
            control.addOnChangeListener { _, _, _ -> refresh() }
        }
        refresh()

        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ -> onSelected(selectedColor) }
            .show()
    }

    fun formatColor(color: Int): String = String.format(
        Locale.ROOT,
        "#%06X",
        color and 0xFFFFFF,
    )

    private fun label(context: Context) = TextView(context).apply {
        setTextAppearance(R.style.TextAppearance_PetBlud_Body)
    }

    private fun slider(
        context: Context,
        from: Float,
        to: Float,
        step: Float,
        initial: Float,
    ) = Slider(context).apply {
        valueFrom = from
        valueTo = to
        stepSize = step
        value = initial.coerceIn(from, to)
    }
}
