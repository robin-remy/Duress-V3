package com.duress.adminspike

import android.content.Context

/** Preferencia de UI (no secreta): teclado ordenado o desordenado. */
class AppPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)

    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) { sp.edit().putBoolean("shuffle", v).apply() }
}
