package com.duress.adminspike

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * Al encender el telefono, si "showOnBoot" esta activo y hay PIN configurado,
 * publica una notificacion con intent de pantalla completa que abre el gate.
 * Frágil por diseño de Android: depende de permisos (13+/14+) y del autoarranque
 * del fabricante (MIUI/XOS/HiOS pueden bloquearlo).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        if (!AppPrefs(context).showOnBoot) return
        if (!SecurePinStore(context).isConfigured()) return

        val channelId = "duress_boot"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "DURESS", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val gate = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        var piFlags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= 23) piFlags = piFlags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(context, 0, gate, piFlags)

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("DURESS")
            .setContentText("Toca para desbloquear")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .build()
        nm.notify(1001, notif)
    }
}
