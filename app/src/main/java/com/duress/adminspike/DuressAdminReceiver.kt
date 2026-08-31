package com.duress.adminspike

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Receiver mínimo de device admin para el spike.
 * Su única política declarada (device_admin.xml) es <wipe-data />.
 */
class DuressAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Toast.makeText(context, "DURESS admin ACTIVADO", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Toast.makeText(context, "DURESS admin desactivado", Toast.LENGTH_SHORT).show()
    }
}

