package com.duress.adminspike

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var store: SecurePinStore
    private lateinit var dpm: DevicePolicyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecurePinStore(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (store.isConfigured()) showGate() else showSetup()
    }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    private fun pinField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 20f; gravity = Gravity.CENTER
    }

    private fun space(): View = TextView(this).apply { text = "\n" }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun validPin(p: String) = p.length in 4..8 && p.all { it.isDigit() }

    private fun showSetup() {
        val root = column()
        val mode = TextView(this).apply {
            text = if (dpm.isDeviceOwnerApp(packageName))
                "Modo: DEVICE OWNER (el borrado real funcionara)"
            else
                "Modo: sin privilegios (probaras solo la logica; no se borrara)"
            textSize = 12f; gravity = Gravity.CENTER
        }
        val normal = pinField("PIN normal")
        val duress = pinField("PIN de emergencia (Duress)")
        val save = Button(this).apply { text = "Guardar" }
        save.setOnClickListener {
            val n = normal.text.toString(); val d = duress.text.toString()
            when {
                !validPin(n) || !validPin(d) -> toast("Cada PIN debe tener entre 4 y 8 digitos")
                n == d -> toast("Los dos PIN deben ser diferentes")
                else -> { store.configure(n, d); toast("PIN configurados"); showGate() }
            }
        }
        root.addView(title("Configura tus dos PIN\n(4 a 8 digitos, distintos)"))
        root.addView(mode); root.addView(space())
        root.addView(normal); root.addView(duress); root.addView(space())
        root.addView(save)
        setContentView(root)
    }

    private fun showGate() {
        val root = column()
        val field = pinField("PIN")
        val error = TextView(this).apply { setTextColor(0xFFCC0000.toInt()) }
        val ok = Button(this).apply { text = "Entrar" }
        ok.setOnClickListener {
            val pin = field.text.toString()
            field.text.clear()
            when (store.verify(pin)) {
                SecurePinStore.Result.NORMAL -> showAccess()
                SecurePinStore.Result.DURESS -> triggerDuress()
                SecurePinStore.Result.WRONG -> error.text = "PIN incorrecto"
            }
        }
        root.addView(title("Introduce tu PIN")); root.addView(space())
        root.addView(field); root.addView(error); root.addView(space())
        root.addView(ok)
        setContentView(root)
    }

    private fun showAccess() {
        val root = column()
        root.addView(title("\u2713 Acceso concedido")); root.addView(space())
        root.addView(Button(this).apply {
            text = "Bloquear"; setOnClickListener { showGate() }
        })
        root.addView(space())
        root.addView(Button(this).apply {
            text = "Reconfigurar PIN"
            setOnClickListener { store.reset(); showSetup() }
        })
        setContentView(root)
    }

    private fun triggerDuress() {
        // Elegido: borrar de inmediato, en silencio.
        var flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 34) flags = flags or DevicePolicyManager.WIPE_SILENTLY
        try {
            dpm.wipeData(flags)
        } catch (e: SecurityException) {
            // Sin Device Owner no borra: sirve para validar la LOGICA sin destruir.
            toast("[Deteccion Duress OK] wipeData bloqueado: ${e.message}")
        } catch (e: Exception) {
            toast("[Deteccion Duress OK] error: ${e.message}")
        }
    }
}
