package com.skysplatwoomy.petblud

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
import kotlin.math.round

object NumericEntry {
    fun parseAndClamp(raw: String, minimum: Double, maximum: Double): Double? {
        val value = raw.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        return value.coerceIn(minimum, maximum)
    }

    fun snap(value: Double, minimum: Double, maximum: Double, stepSize: Double): Double {
        val clamped = value.coerceIn(minimum, maximum)
        if (stepSize <= 0.0) return clamped
        return (minimum + round((clamped - minimum) / stepSize) * stepSize)
            .coerceIn(minimum, maximum)
    }

    fun format(value: Double, decimalPlaces: Int): String =
        String.format(Locale.ROOT, "%.${decimalPlaces.coerceAtLeast(0)}f", value)
}

object NumericInputDialog {
    fun show(
        context: Context,
        title: String,
        currentValue: Double,
        minimum: Double,
        maximum: Double,
        decimalPlaces: Int,
        stepSize: Double = 0.0,
        onSelected: (Double) -> Unit,
    ) {
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        fun dp(value: Int) = (value * density).toInt()
        val input = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
            setText(NumericEntry.format(currentValue, decimalPlaces))
            selectAll()
        }
        val field = TextInputLayout(context).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = context.getString(
                R.string.number_range,
                NumericEntry.format(minimum, decimalPlaces),
                NumericEntry.format(maximum, decimalPlaces),
            )
            addView(
                input,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val container = FrameLayout(context).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(
                field,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        if (context.findActivity() == null) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = NumericEntry.parseAndClamp(input.text?.toString().orEmpty(), minimum, maximum)
                if (parsed == null) {
                    field.error = context.getString(R.string.enter_valid_number)
                } else {
                    field.error = null
                    onSelected(NumericEntry.snap(parsed, minimum, maximum, stepSize))
                    dialog.dismiss()
                }
            }
            input.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            input.post {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
