package com.duress.adminspike

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return
        if (!SecurePinStore(context).isConfigured()) return

        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DuressAdminReceiver::class.java)
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            val f = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            try { dpm.clearPackagePersistentPreferredActivities(admin, context.packageName) } catch (_: Exception) {}
            try {
                dpm.addPersistentPreferredActivity(admin, f,
                    ComponentName(context, MainActivity::class.java))
            } catch (_: Exception) {}
        }
        try {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        } catch (_: Exception) {}
    }
}
