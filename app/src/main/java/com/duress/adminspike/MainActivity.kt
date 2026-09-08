package com.duress.adminspike

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var store: SecurePinStore
    private lateinit var prefs: AppPrefs
    private lateinit var dpm: DevicePolicyManager

    private val entry = StringBuilder()
    private val maxLen = 8

    private var currentError: TextView? = null
    private var currentDots: TextView? = null
    private var currentKeypad: PinKeypadView? = null
    private var pendingNormal: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecurePinStore(this)
        prefs = AppPrefs(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (store.isConfigured()) showGate() else showSetupNormal()
    }

    // ---------- helpers ----------
    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(32, 32, 32, 32)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    private fun title(t: String) = TextView(this).apply {
        text = t; textSize = 20f; gravity = Gravity.CENTER
    }
    private fun small(t: String) = TextView(this).apply {
        text = t; textSize = 12f; gravity = Gravity.CENTER
    }
    private fun space(): View = TextView(this).apply { text = "\n" }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun validPin(p: String) = p.length in 4..8 && p.all { it.isDigit() }

    private fun renderDots() {
        currentDots?.text = "\u25CF ".repeat(entry.length).trim()
    }
    private fun onWrong(msg: String) {
        currentError?.text = msg
        entry.setLength(0)
        renderDots()
        currentKeypad?.reshuffle()   // rebaraja en cada intento
    }

    private fun buildPinScreen(
        titleText: String,
        subtitle: String?,
        shuffle: Boolean,
        onConfirm: (String) -> Unit,
        extra: View? = null
    ) {
        entry.setLength(0)
        val root = column()
        val dots = TextView(this).apply { textSize = 28f; gravity = Gravity.CENTER; text = "" }
        val err = TextView(this).apply { setTextColor(0xFFCC0000.toInt()); gravity = Gravity.CENTER }

        val keypad = PinKeypadView(
            this,
            onDigit = { d ->
                if (entry.length < maxLen) { entry.append(d); renderDots(); err.text = "" }
            },
            onDelete = {
                if (entry.isNotEmpty()) { entry.deleteCharAt(entry.length - 1); renderDots() }
            },
            onConfirm = { onConfirm(entry.toString()) }
        )

        currentError = err
        currentDots = dots
        currentKeypad = keypad
        keypad.applyLayout(shuffle)

        root.addView(title(titleText))
        if (subtitle != null) root.addView(small(subtitle))
        root.addView(space())
        root.addView(dots)
        root.addView(err)
        root.addView(space())
        root.addView(keypad)
        if (extra != null) { root.addView(space()); root.addView(extra) }
        setContentView(root)
    }

    // ---------- setup (2 pasos, teclado ordenado) ----------
    private fun showSetupNormal() {
        pendingNormal = null
        val shuffleBox = CheckBox(this).apply {
            text = "Teclado desordenado al desbloquear"
            isChecked = prefs.shuffle
            setOnCheckedChangeListener { _, checked -> prefs.shuffle = checked }
        }
        val mode = small(
            if (dpm.isDeviceOwnerApp(packageName))
                "Modo: DEVICE OWNER (el borrado real funcionara)"
            else
                "Modo: sin privilegios (solo logica; no se borrara)"
        )
        val extras = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(shuffleBox); addView(mode)
        }
        buildPinScreen(
            titleText = "Define tu PIN normal\n(4 a 8 digitos)",
            subtitle = "Paso 1 de 2",
            shuffle = false,
            onConfirm = { pin ->
                if (!validPin(pin)) onWrong("Debe tener entre 4 y 8 digitos")
                else { pendingNormal = pin; showSetupDuress() }
            },
            extra = extras
        )
    }

    private fun showSetupDuress() {
        buildPinScreen(
            titleText = "Define tu PIN de emergencia\n(Duress, distinto del normal)",
            subtitle = "Paso 2 de 2",
            shuffle = false,
            onConfirm = { pin ->
                when {
                    !validPin(pin) -> onWrong("Debe tener entre 4 y 8 digitos")
                    pin == pendingNormal -> onWrong("Debe ser distinto del PIN normal")
                    else -> {
                        store.configure(pendingNormal!!, pin)
                        pendingNormal = null
                        toast("PIN configurados")
                        showGate()
                    }
                }
            }
        )
    }

    // ---------- gate (respeta el toggle) ----------
    private fun showGate() {
        buildPinScreen(
            titleText = "Introduce tu PIN",
            subtitle = null,
            shuffle = prefs.shuffle,
            onConfirm = { pin ->
                when (store.verify(pin)) {
                    SecurePinStore.Result.NORMAL -> showAccess()
                    SecurePinStore.Result.DURESS -> triggerDuress()
                    SecurePinStore.Result.WRONG -> onWrong("PIN incorrecto")
                }
            }
        )
    }

    private fun showAccess() {
        val root = column()
        val shuffleBox = CheckBox(this).apply {
            text = "Teclado desordenado al desbloquear"
            isChecked = prefs.shuffle
            setOnCheckedChangeListener { _, checked -> prefs.shuffle = checked }
        }
        root.addView(title("\u2713 Acceso concedido"))
        root.addView(space())
        root.addView(shuffleBox)
        root.addView(space())
        root.addView(Button(this).apply { text = "Bloquear"; setOnClickListener { showGate() } })
        root.addView(space())
        root.addView(Button(this).apply {
            text = "Reconfigurar PIN"
            setOnClickListener { store.reset(); showSetupNormal() }
        })
        setContentView(root)
    }

    private fun triggerDuress() {
        var flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 34) flags = flags or DevicePolicyManager.WIPE_SILENTLY
        try {
            dpm.wipeData(flags)
        } catch (e: SecurityException) {
            toast("[Deteccion Duress OK] wipeData bloqueado: ${e.message}")
        } catch (e: Exception) {
            toast("[Deteccion Duress OK] error: ${e.message}")
        }
    }
}
