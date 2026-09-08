package com.duress.adminspike

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout

/**
 * Teclado numerico propio que reemplaza al teclado del sistema.
 * Ordenado: 1..9 y 0 abajo al centro.
 * Desordenado: permutacion aleatoria de 0..9 en los 10 slots numericos.
 * reshuffle() rebaraja (se llama en cada intento cuando el modo esta activo).
 */
class PinKeypadView(
    context: Context,
    private val onDigit: (Int) -> Unit,
    private val onDelete: () -> Unit,
    private val onConfirm: () -> Unit
) : LinearLayout(context) {

    private var shuffled: Boolean = false
    private var digits: IntArray = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        rebuild()
    }

    fun applyLayout(shuffle: Boolean) {
        shuffled = shuffle
        reshuffle()
    }

    fun reshuffle() {
        digits = if (shuffled) {
            (0..9).toMutableList().also { it.shuffle() }.toIntArray()
        } else {
            intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
        }
        rebuild()
    }

    private fun rebuild() {
        removeAllViews()
        var slot = 0
        for (r in 0 until 3) {
            val row = row()
            for (c in 0 until 3) {
                row.addView(digitButton(digits[slot])); slot++
            }
            addView(row)
        }
        val row4 = row()
        row4.addView(actionButton("\u232B") { onDelete() })   // borrar
        row4.addView(digitButton(digits[9]))
        row4.addView(actionButton("\u2713") { onConfirm() })  // confirmar
        addView(row4)
    }

    private fun row() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun digitButton(d: Int) = Button(context).apply {
        text = d.toString()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
        layoutParams = LayoutParams(0, dp(64), 1f)
            .apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
        setOnClickListener { onDigit(d) }
    }

    private fun actionButton(label: String, onClick: () -> Unit) = Button(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        layoutParams = LayoutParams(0, dp(64), 1f)
            .apply { setMargins(dp(6), dp(6), dp(6), dp(6)) }
        setOnClickListener { onClick() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
