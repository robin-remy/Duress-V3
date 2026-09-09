package com.duress.adminspike

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Teclado numerico propio. Cada boton captura SU digito directamente
 * (sin depender de indices), por eso el modo desordenado funciona bien.
 */
class PinKeypadView(
    context: Context,
    private val onDigit: (Int) -> Unit,
    private val onDelete: () -> Unit,
    private val onConfirm: () -> Unit
) : LinearLayout(context) {

    private var shuffled = false
    private var digits = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)

    private val keyFill = Color.parseColor("#1B2430")
    private val keyText = Color.parseColor("#E6EDF3")
    private val accent = Color.parseColor("#2DD4BF")
    private val danger = Color.parseColor("#F87171")

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        rebuild()
    }

    fun applyLayout(shuffle: Boolean) { shuffled = shuffle; reshuffle() }

    fun reshuffle() {
        digits = if (shuffled) (0..9).toMutableList().also { it.shuffle() }.toIntArray()
        else intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        rebuild()
    }

    private fun rebuild() {
        removeAllViews()
        // Primeras 9 teclas en cuadricula 3x3
        var slot = 0
        for (r in 0 until 3) {
            val row = row()
            for (c in 0 until 3) {
                val value = digits[slot]                       // capturado por valor
                row.addView(circleKey(value.toString(), keyText) { onDigit(value) })
                slot++
            }
            addView(row)
        }
        // Fila final: borrar, ultimo digito, confirmar
        val lastValue = digits[9]
        val last = row()
        last.addView(circleKey("\u232B", danger) { onDelete() })
        last.addView(circleKey(lastValue.toString(), keyText) { onDigit(lastValue) })
        last.addView(circleKey("\u2713", accent) { onConfirm() })
        addView(last)
    }

    private fun row() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun circleKey(label: String, textColor: Int, onClick: () -> Unit) = TextView(context).apply {
        text = label
        setTextColor(textColor)
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        val size = dp(74)
        layoutParams = LayoutParams(size, size).apply { setMargins(dp(10), dp(10), dp(10), dp(10)) }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(keyFill)
            setStroke(dp(1), Color.parseColor("#2A3644"))
        }
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
