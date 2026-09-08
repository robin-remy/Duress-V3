package com.duress.adminspike

import android.content.Context

/** Preferencias de UI/seguridad no secretas. */
class AppPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)

    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) { sp.edit().putBoolean("shuffle", v).apply() }

    var kiosk: Boolean
        get() = sp.getBoolean("kiosk", false)
        set(v) { sp.edit().putBoolean("kiosk", v).apply() }
}
