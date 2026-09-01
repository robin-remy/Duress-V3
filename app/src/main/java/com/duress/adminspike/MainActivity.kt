package com.duress.adminspike

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var admin: ComponentName
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        admin = ComponentName(this, DuressAdminReceiver::class.java)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        status = TextView(this).apply {
            textSize = 18f
            text = "Estado: -"
        }

        val btnStatus = Button(this).apply {
            text = "Actualizar estado"
            setOnClickListener { refresh() }
        }

        val btnWipe = Button(this).apply {
            text = "Probar wipeData()  [BORRA]"
            setOnClickListener { confirmWipe() }
        }

        root.addView(status)
        root.addView(space())
        root.addView(btnStatus)
        root.addView(space())
        root.addView(btnWipe)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        status.text = when {
            dpm.isDeviceOwnerApp(packageName) -> "Estado: DEVICE OWNER (OK)"
            dpm.isAdminActive(admin) -> "Estado: device admin (SIN Device Owner)"
            else -> "Estado: sin privilegios"
        }
    }

    private fun confirmWipe() {
        val warn = if (dpm.isDeviceOwnerApp(packageName)) {
            "Es Device Owner. wipeData() deberia hacer factory reset REAL."
        } else {
            "NO es Device Owner: probablemente falle igual que antes."
        }
        AlertDialog.Builder(this)
            .setTitle("PRUEBA DESTRUCTIVA")
            .setMessage("$warn\n\nContinuar y BORRAR el dispositivo?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("BORRAR AHORA") { _, _ -> doWipe() }
            .show()
    }

    private fun doWipe() {
        var flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 34) {
            // Borrado silencioso (solo Device Owner, Android 14+)
            flags = flags or DevicePolicyManager.WIPE_SILENTLY
        }
        try {
            dpm.wipeData(flags)
        } catch (e: SecurityException) {
            toast("SecurityException: ${e.message}")
        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun space(): TextView = TextView(this).apply { text = "\n" }
}
