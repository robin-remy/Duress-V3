package com.duress.adminspike

import android.Manifest
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private var clockView: TextView? = null

    private val ticker = Handler(Looper.getMainLooper())
    private var tickRunnable: Runnable? = null
    private val clockTicker = Handler(Looper.getMainLooper())
    private var clockRunnable: Runnable? = null

    // --- paleta ---
    private val bg = Color.parseColor("#0B0F14")
    private val fg = Color.parseColor("#E6EDF3")
    private val muted = Color.parseColor("#8A97A6")
    private val accent = Color.parseColor("#2DD4BF")
    private val cardBg = Color.parseColor("#141C26")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyLockScreenFlags()
        store = SecurePinStore(this)
        prefs = AppPrefs(this)
        guard = AttemptGuard(this)
        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        supportActionBar?.hide()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (screen == Screen.ACCESS) showGate() }
        })

        if (store.isConfigured()) showGate() else showSetupNormal()
    }

    private fun applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else @Suppress("DEPRECATION") window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
    }

    override fun onStop() { super.onStop(); if (screen == Screen.ACCESS) wasStopped = true }

    override fun onResume() {
        super.onResume()
        if (wasStopped && screen == Screen.ACCESS) { wasStopped = false; showGate() }
        else if (screen == Screen.GATE) maybeStartKiosk()
    }

    override fun onDestroy() { super.onDestroy(); stopClock(); stopTicker() }

    private fun isDO() = dpm.isDeviceOwnerApp(packageName)
    private fun maybeStartKiosk() {
        if (prefs.kiosk && isDO()) {
            try { dpm.setLockTaskPackages(admin, arrayOf(packageName)) } catch (_: Exception) {}
            try { startLockTask() } catch (_: Exception) {}
        }
    }
    private fun stopKiosk() { try { stopLockTask() } catch (_: Exception) {} }

    // ---------- UI base ----------
    private fun screenRoot(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(bg)
        setPadding(dp(28), dp(40), dp(28), dp(28))
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text; setTextColor(color); gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun gap(h: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
    private fun validPin(p: String) = p.length in 4..8 && p.all { it.isDigit() }

    private fun badge(text: String): TextView = TextView(this).apply {
        this.text = "  $text  "
        setTextColor(accent); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(20).toFloat()
            setColor(cardBg); setStroke(dp(1), Color.parseColor("#22333F"))
        }
    }
    private fun pillButton(text: String, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text; setTextColor(fg); gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        layoutParams = LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) }
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE; cornerRadius = dp(28).toFloat()
            setColor(cardBg); setStroke(dp(1), Color.parseColor("#22333F"))
        }
        isClickable = true; setOnClickListener { onClick() }
    }
    private fun themedCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) = CheckBox(this).apply {
        text = label; setTextColor(fg); isChecked = checked
        setOnCheckedChangeListener { _, c -> onChange(c) }
    }

    private fun renderDots() {
        val n = entry.length
        currentDots?.text = buildString {
            repeat(n) { append("\u25CF  ") }
            repeat((4 - n).coerceAtLeast(0)) { append("\u25CB  ") }
        }.trim()
    }
    private fun clearEntryAndReshuffle() { entry.setLength(0); renderDots(); currentKeypad?.reshuffle() }

    private fun startClock(view: TextView) {
        stopClock()
        val fmt = SimpleDateFormat("hh:mm", Locale.getDefault())
        val r = object : Runnable {
            override fun run() { view.text = fmt.format(Date()); clockTicker.postDelayed(this, 1000) }
        }
        clockRunnable = r; clockTicker.post(r)
    }
    private fun stopClock() { clockRunnable?.let { clockTicker.removeCallbacks(it) }; clockRunnable = null }

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

    // ---------- pantalla PIN con estetica ----------
    private fun buildPinScreen(
        heading: String, subtitle: String?, shuffle: Boolean, showClock: Boolean,
        onConfirm: (String) -> Unit, extra: View? = null
    ) {
        stopTicker(); stopClock(); entry.setLength(0)
        val root = screenRoot()

        if (showClock) {
            val clock = tv("", 46f, fg, bold = true)
            root.addView(gap(6)); root.addView(clock)
            root.addView(tv(SimpleDateFormat("EEEE, d 'de' MMMM", Locale.getDefault()).format(Date()), 13f, muted))
            root.addView(gap(10))
            val badges = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            badges.addView(badge("\u25CF offline"))
            root.addView(badges)
            clockView = clock; startClock(clock)
            root.addView(gap(24))
        } else root.addView(gap(30))

        root.addView(tv(heading, 20f, fg, bold = true))
        if (subtitle != null) { root.addView(gap(4)); root.addView(tv(subtitle, 12f, muted)) }
        root.addView(gap(20))

        val dots = tv("", 20f, accent); currentDots = dots
        root.addView(dots)
        val err = tv("", 13f, Color.parseColor("#F87171")); currentError = err
        root.addView(gap(6)); root.addView(err); root.addView(gap(18))

        val keypad = PinKeypadView(
            this,
            onDigit = { d -> if (entry.length < maxLen) { entry.append(d); renderDots(); if (!guard.isLockedOut()) err.text = "" } },
            onDelete = { if (entry.isNotEmpty()) { entry.deleteCharAt(entry.length - 1); renderDots() } },
            onConfirm = { onConfirm(entry.toString()) }
        )
        currentKeypad = keypad; keypad.applyLayout(shuffle)
        root.addView(keypad)
        renderDots()

        if (extra != null) { root.addView(gap(16)); root.addView(extra) }
        setContentView(root)
    }

    // ---------- setup ----------
    private fun showSetupNormal() {
        screen = Screen.SETUP; pendingNormal = null
        val extras = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(themedCheck("Teclado desordenado al desbloquear", prefs.shuffle) { prefs.shuffle = it })
            addView(tv(if (isDO()) "Modo: DEVICE OWNER (borrado real activo)" else "Modo: sin privilegios (solo logica)", 11f, muted))
        }
        buildPinScreen("Define tu PIN normal", "Paso 1 de 2 \u00b7 4 a 8 digitos", false, false,
            onConfirm = { pin ->
                if (!validPin(pin)) { clearEntryAndReshuffle(); currentError?.text = "Debe tener 4 a 8 digitos" }
                else { pendingNormal = pin; showSetupDuress() }
            }, extra = extras)
    }

    private fun showSetupDuress() {
        screen = Screen.SETUP
        buildPinScreen("Define tu PIN de emergencia", "Paso 2 de 2 \u00b7 distinto del normal", false, false,
            onConfirm = { pin ->
                when {
                    !validPin(pin) -> { clearEntryAndReshuffle(); currentError?.text = "Debe tener 4 a 8 digitos" }
                    pin == pendingNormal -> { clearEntryAndReshuffle(); currentError?.text = "Debe ser distinto del normal" }
                    else -> { store.configure(pendingNormal!!, pin); pendingNormal = null; toast("PIN configurados"); showGate() }
                }
            })
    }

    // ---------- gate ----------
    private fun showGate() {
        screen = Screen.GATE
        buildPinScreen("Introduce el PIN", null, prefs.shuffle, true,
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
        screen = Screen.ACCESS; stopKiosk(); stopTicker(); stopClock()
        val root = screenRoot()
        root.addView(gap(30))
        root.addView(tv("\u2713 Acceso concedido", 22f, accent, bold = true))
        root.addView(gap(20))
        root.addView(themedCheck("Teclado desordenado al desbloquear", prefs.shuffle) { prefs.shuffle = it })
        root.addView(themedCheck("Modo kiosco: no salir sin PIN (solo DO)", prefs.kiosk) { c ->
            prefs.kiosk = c; if (c && !isDO()) toast("Requiere Device Owner para bloquear del todo")
        })
        root.addView(themedCheck("Aparecer al encender / sobre el bloqueo", prefs.showOnBoot) { c ->
            if (c) enableShowOnBoot() else prefs.showOnBoot = false
        })
        root.addView(gap(10))

        val uninstall = pillButton("") { }
        fun refresh() {
            val blocked = if (isDO()) try { dpm.isUninstallBlocked(admin, packageName) } catch (_: Exception) { false } else false
            uninstall.text = if (blocked) "Anti-desinstalacion: ON" else "Anti-desinstalacion: OFF"
        }
        uninstall.setOnClickListener {
            if (!isDO()) { toast("Requiere Device Owner"); return@setOnClickListener }
            val blocked = try { dpm.isUninstallBlocked(admin, packageName) } catch (_: Exception) { false }
            try { dpm.setUninstallBlocked(admin, packageName, !blocked) } catch (_: Exception) {}
            refresh()
        }
        refresh()
        root.addView(uninstall)
        root.addView(pillButton("Bloquear") { showGate() })
        root.addView(pillButton("Reconfigurar PIN") { store.reset(); showSetupNormal() })
        setContentView(root)
    }

    private fun enableShowOnBoot() {
        prefs.showOnBoot = true
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                try { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
            }
        }
        toast("Activado. En MIUI/XOS puede requerir 'Inicio automatico' en Ajustes.")
    }

    private fun triggerDuress() {
        var flags = DevicePolicyManager.WIPE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= 34) flags = flags or DevicePolicyManager.WIPE_SILENTLY
        try { dpm.wipeData(flags) }
        catch (e: SecurityException) { toast("[Deteccion Duress OK] wipeData bloqueado: ${e.message}") }
        catch (e: Exception) { toast("[Deteccion Duress OK] error: ${e.message}") }
    }
}
