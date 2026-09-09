package com.duress.adminspike

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Teclado numerico propio con estetica del mockup:
 * botones circulares oscuros, texto claro, acento teal.
 * Ordenado o desordenado (rebaraja en cada intento cuando el modo esta activo).
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
    private val accent = Color.parseColor("#2DD4BF")   // teal
    private val danger = Color.parseColor("#F87171")   // rojo suave

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
        var slot = 0
        for (r in 0 until 3) {
            val row = row()
            for (c in 0 until 3) { row.addView(circleKey(digits[slot].toString(), keyText) { onDigit(digits_at(slot)) }); slot++ }
            addView(row)
        }
        val last = row()
        last.addView(circleKey("\u232B", danger) { onDelete() })         // borrar
        last.addView(circleKey(digits[9].toString(), keyText) { onDigit(digits[9]) })
        last.addView(circleKey("\u2713", accent) { onConfirm() })        // confirmar
        addView(last)
    }

    // captura el valor correcto del slot al construir el listener
    private fun digits_at(index: Int): Int = digits[index]

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
