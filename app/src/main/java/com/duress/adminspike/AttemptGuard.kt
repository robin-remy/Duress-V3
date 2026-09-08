package com.duress.adminspike

import android.content.Context

/**
 * Anti-fuerza-bruta con retardo creciente.
 * Persistido en disco: matar la app NO reinicia el contador.
 * No borra por intentos (respeta la decision del usuario), solo ralentiza.
 * El PIN Duress NUNCA pasa por aqui: siempre puede dispararse (se maneja aparte).
 */
class AttemptGuard(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("duress_guard", Context.MODE_PRIVATE)

    private val freeAttempts = 4   // intentos sin penalizacion

    private fun delaySeconds(failCount: Int): Long {
        val over = failCount - freeAttempts
        if (over <= 0) return 0
        return when (over) {
            1 -> 5
            2 -> 15
            3 -> 30
            4 -> 60
            5 -> 120
            else -> 300   // tope 5 min
        }
    }

    private var failCount: Int
        get() = sp.getInt("fail", 0)
        set(v) { sp.edit().putInt("fail", v).apply() }

    private var lockoutUntil: Long
        get() = sp.getLong("until", 0L)
        set(v) { sp.edit().putLong("until", v).apply() }

    fun remainingMs(): Long =
        (lockoutUntil - System.currentTimeMillis()).coerceAtLeast(0)

    fun isLockedOut(): Boolean = remainingMs() > 0

    fun recordFailure() {
        val fc = failCount + 1
        failCount = fc
        val d = delaySeconds(fc)
        if (d > 0) lockoutUntil = System.currentTimeMillis() + d * 1000
    }

    fun recordSuccess() {
        failCount = 0
        lockoutUntil = 0
    }
}
