package com.duress.adminspike

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Spike 1: activa un device admin y prueba wipeData().
 * La UI se construye en código para no depender de recursos de layout.
 */
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
            text = "Device admin: -"
        }

        val btnEnable = Button(this).apply {
            text = "1) Activar device admin"
            setOnClickListener { requestAdmin() }
        }

        val btnWipe = Button(this).apply {
            text = "2) Probar wipeData()  [BORRA]"
            setOnClickListener { confirmWipe() }
        }

        root.addView(status)
        root.addView(space())
        root.addView(btnEnable)
        root.addView(space())
        root.addView(btnWipe)

        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        status.text = if (dpm.isAdminActive(admin)) {
            "Device admin: ACTIVO"
        } else {
            "Device admin: inactivo"
        }
    }

    private fun requestAdmin() {
        if (dpm.isAdminActive(admin)) {
            toast("Ya está activo")
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prueba de factibilidad DURESS: permite el borrado del dispositivo."
            )
        }
        startActivity(intent)
    }

    private fun confirmWipe() {
        if (!dpm.isAdminActive(admin)) {
            toast("Activa el device admin primero")
            return
        }
        // Confirmación presente SOLO en el spike, para evitar borrados accidentales.
        // El producto final no la tendrá (borrado discreto).
        AlertDialog.Builder(this)
            .setTitle("PRUEBA DESTRUCTIVA")
            .setMessage("Esto llamará a wipeData() y BORRARÁ el dispositivo de fábrica. ¿Continuar?")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("BORRAR AHORA") { _, _ -> doWipe() }
            .show()
    }

    private fun doWipe() {
        try {
            dpm.wipeData(0)
        } catch (e: SecurityException) {
            toast("SecurityException: ${e.message}")
        } catch (e: Exception) {
            toast("Error: ${e.message}")
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun space(): TextView = TextView(this).apply { text = "\n" }
}
