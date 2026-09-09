package com.duress.adminspike

import android.content.Context

class AppPrefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("duress_prefs", Context.MODE_PRIVATE)

    var shuffle: Boolean
        get() = sp.getBoolean("shuffle", false)
        set(v) { sp.edit().putBoolean("shuffle", v).apply() }

    var kiosk: Boolean
        get() = sp.getBoolean("kiosk", false)
        set(v) { sp.edit().putBoolean("kiosk", v).apply() }

    var showOnBoot: Boolean
        get() = sp.getBoolean("show_on_boot", false)
        set(v) { sp.edit().putBoolean("show_on_boot", v).apply() }

    var launcher: Boolean
        get() = sp.getBoolean("launcher", false)
        set(v) { sp.edit().putBoolean("launcher", v).apply() }
}
