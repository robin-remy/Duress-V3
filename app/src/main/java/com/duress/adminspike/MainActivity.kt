package com.duress.adminspike

import android.Manifest
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private enum class Screen { SETUP, GATE, ACCESS }

    private lateinit var store: SecurePinStore
    private lateinit var prefs: AppPrefs
    private lateinit var guard: AttemptGuard
    private lateinit var dpm: DevicePolicyManager
    private val admin by lazy { ComponentName(this, DuressAdminReceiver::class.java) }

    private val entry = StringBuilder()
    private val maxLen = 8

    private var screen = Screen.GATE
    private var wasStopped = false
    private var pendingNormal: String? = null

    private var currentError: TextView? = null
    private var currentDots: TextView? = null
    private var currentKeypad: PinKeypadView? = null

    private val ticker = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()
        store = SecurePinStore(this)
        prefs = AppPrefs(this)
        guard = AttemptGuard(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (screen == Screen.ACCESS) showGate()
            }
        })

        if (store.isConfigured()) showGate() else showSetupNormal()
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (screen == Screen.ACCESS) wasStopped = true
    }

    override fun onResume() {
        super.onResume()
        if (wasStopped && screen == Screen.ACCESS) { wasStopped = false; showGate() }
        else if (screen == Screen.GATE) maybeStartKiosk()
    }

    private fun isDO() = dpm.isDeviceOwnerApp(packageName)

    private fun maybeStartKiosk() {
        if (prefs.kiosk && isDO()) {
            try { dpm.setLockTaskPackages(admin, arrayOf(packageName)) } catch (_: Exception) {}
            try { startLockTask() } catch (_: Exception) {}
        }
    }
    private fun stopKiosk() { try { stopLockTask() } catch (_: Exception) {} }

    private fun column() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(32, 32, 32, 32)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    private fun title(t: String) = TextView(this).apply { text = t; textSize = 20f; gravity = Gravity.CENTER }
    private fun small(t: String) = TextView(this).apply { text = t; textSize = 12f; gravity = Gravity.CENTER }
    private fun space(): View = TextView(this).apply { text = "\n" }
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun validPin(p: String) = p.length in 4..8 && p.all { it.isDigit() }

    private fun renderDots() { currentDots?.text = "\u25CF ".repeat(entry.length).trim() }
    private fun clearEntryAndReshuffle() { entry.setLength(0); renderDots(); currentKeypad?.reshuffle() }

    private fun startLockoutTicker() {
        stopTicker()
        val r = object : Runnable {
            override fun run() {
                if (guard.isLockedOut()) {
                    val s = (guard.remainingMs() / 1000) + 1
                    currentError?.text = "Bloqueado. Espera ${s}s"
                    ticker.postDelayed(this, 1000)
                } else currentError?.text = ""
            }
        }
        tickRunnable = r; ticker.post(r)
    }
    private fun stopTicker() { tickRunnable?.let { ticker.removeCallbacks(it) }; tickRunnable = null }

    private fun buildPinScreen(
        titleText: String, subtitle: String?, shuffle: Boolean,
        onConfirm: (String) -> Unit, extra: View? = null
    ) {
        stopTicker()
        entry.setLength(0)
        val root = column()
        val dots = TextView(this).apply { textSize = 28f; gravity = Gravity.CENTER; text = "" }
        val err = TextView(this).apply { setTextColor(0xFFCC0000.toInt()); gravity = Gravity.CENTER }
        val keypad = PinKeypadView(
            this,
            onDigit = { d -> if (entry.length < maxLen) { entry.append(d); renderDots(); if (!guard.isLockedOut()) err.text = "" } },
            onDelete = { if (entry.isNotEmpty()) { entry.deleteCharAt(entry.length - 1); renderDots() } },
            onConfirm = { onConfirm(entry.toString()) }
        )
        currentError = err; currentDots = dots; currentKeypad = keypad
        keypad.applyLayout(shuffle)

        root.addView(title(titleText))
        if (subtitle != null) root.addView(small(subtitle))
        root.addView(space()); root.addView(dots); root.addView(err); root.addView(space())
        root.addView(keypad)
        if (extra != null) { root.addView(space()); root.addView(extra) }
        setContentView(root)
    }

    private fun showSetupNormal() {
        screen = Screen.SETUP
        pendingNormal = null
        val shuffleBox = CheckBox(this).apply {
            text = "Teclado desordenado al desbloquear"
            isChecked = prefs.shuffle
            setOnCheckedChangeListener { _, c -> prefs.shuffle = c }
        }
        val mode = small(if (isDO()) "Modo: DEVICE OWNER (borrado real activo)" else "Modo: sin privilegios (solo logica)")
        val extras = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(shuffleBox); addView(mode)
        }
        buildPinScreen("Define tu PIN normal\n(4 a 8 digitos)", "Paso 1 de 2", false,
            onConfirm = { pin ->
                if (!validPin(pin)) { clearEntryAndReshuffle(); currentError?.text = "Debe tener 4 a 8 digitos" }
                else { pendingNormal = pin; showSetupDuress() }
            }, extra = extras)
    }

    private fun showSetupDuress() {
        screen = Screen.SETUP
        buildPinScreen("Define tu PIN de emergencia\n(Duress, distinto del normal)", "Paso 2 de 2", false,
            onConfirm = { pin ->
                when {
                    !validPin(pin) -> { clearEntryAndReshuffle(); currentError?.text = "Debe tener 4 a 8 digitos" }
                    pin == pendingNormal -> { clearEntryAndReshuffle(); currentError?.text = "Debe ser distinto del normal" }
                    else -> { store.configure(pendingNormal!!, pin); pendingNormal = null; toast("PIN configurados"); showGate() }
                }
            })
    }

    private fun showGate() {
        screen = Screen.GATE
        buildPinScreen("Introduce tu PIN", null, prefs.shuffle,
            onConfirm = { pin ->
                val result = store.verify(pin)
                when {
                    result == SecurePinStore.Result.DURESS -> triggerDuress()
                    guard.isLockedOut() -> { clearEntryAndReshuffle(); startLockoutTicker() }
                    result == SecurePinStore.Result.NORMAL -> { guard.recordSuccess(); stopTicker(); showAccess() }
                    else -> {
                        guard.recordFailure(); clearEntryAndReshuffle()
                        if (guard.isLockedOut()) startLockoutTicker() else currentError?.text = "PIN incorrecto"
                    }
                }
            })
        maybeStartKiosk()
        if (guard.isLockedOut()) startLockoutTicker()
    }

    private fun showAccess() {
        screen = Screen.ACCESS
        stopKiosk(); stopTicker()
        val root = column()

        val shuffleBox = CheckBox(this).apply {
            text = "Teclado desordenado al desbloquear"
            isChecked = prefs.shuffle
            setOnCheckedChangeListener { _, c -> prefs.shuffle = c }
        }
        val kioskBox = CheckBox(this).apply {
            text = "Modo kiosco: no salir sin PIN (solo DO)"
            isChecked = prefs.kiosk
            setOnCheckedChangeListener { _, c ->
                prefs.kiosk = c
                if (c && !isDO()) toast("Requiere Device Owner para bloquear del todo")
            }
        }
        val bootBox = CheckBox(this).apply {
            text = "Aparecer al encender / sobre el bloqueo"
            isChecked = prefs.showOnBoot
            setOnCheckedChangeListener { _, c -> if (c) enableShowOnBoot() else prefs.showOnBoot = false }
        }

        val uninstallBtn = Button(this)
        fun refreshUninstall() {
            val blocked = if (isDO()) try { dpm.isUninstallBlocked(admin, packageName) } catch (_: Exception) { false } else false
            uninstallBtn.text = if (blocked) "Anti-desinstalacion: ON (tocar = OFF)" else "Anti-desinstalacion: OFF (tocar = ON)"
        }
        uninstallBtn.setOnClickListener {
            if (!isDO()) { toast("Requiere Device Owner"); return@setOnClickListener }
            val blocked = try { dpm.isUninstallBlocked(admin, packageName) } catch (_: Exception) { false }
            try { dpm.setUninstallBlocked(admin, packageName, !blocked) } catch (_: Exception) {}
            refreshUninstall()
        }
        refreshUninstall()

        root.addView(title("\u2713 Acceso concedido")); root.addView(space())
        root.addView(shuffleBox); root.addView(kioskBox); root.addView(bootBox); root.addView(space())
        root.addView(uninstallBtn); root.addView(space())
        root.addView(Button(this).apply { text = "Bloquear"; setOnClickListener { showGate() } }); root.addView(space())
        root.addView(Button(this).apply {
            text = "Reconfigurar PIN"
            setOnClickListener { store.reset(); showSetupNormal() }
        })
        setContentView(root)
    }

    private fun enableShowOnBoot() {
        prefs.showOnBoot = true
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {}
            }
        }
        toast("Activado. En MIUI/XOS: habilita ademas 'Inicio automatico' y permisos de pantalla de bloqueo en Ajustes de la app.")
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
