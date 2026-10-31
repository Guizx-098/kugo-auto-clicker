package com.kugo.autoclicker

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var sbMin: SeekBar
    private lateinit var sbMax: SeekBar
    private lateinit var tvMin: TextView
    private lateinit var tvMax: TextView
    private var updating = false

    private val poll = object : Runnable {
        override fun run() {
            refreshIndicators()
            ui.postDelayed(this, 250)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sbMin = findViewById(R.id.sbMin)
        sbMax = findViewById(R.id.sbMax)
        tvMin = findViewById(R.id.tvMin)
        tvMax = findViewById(R.id.tvMax)

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Throwable) { "?" }
        findViewById<TextView>(R.id.tvVersion).text = "v$version"

        updating = true
        sbMin.progress = Prefs.minCps(this) - 1
        sbMax.progress = Prefs.maxCps(this) - 1
        updating = false
        showCps()

        sbMin.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (updating) return
                updating = true
                if (sbMin.progress > sbMax.progress) sbMax.progress = sbMin.progress
                updating = false
                saveAndShow()
            }
        })
        sbMax.setOnSeekBarChangeListener(object : SimpleSeek() {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (updating) return
                updating = true
                if (sbMax.progress < sbMin.progress) sbMin.progress = sbMax.progress
                updating = false
                saveAndShow()
            }
        })

        findViewById<Button>(R.id.btnKey).setOnClickListener {
            if (KugoAccessibilityService.instance == null) {
                Toast.makeText(this, "Ative o serviço de acessibilidade do KUGO primeiro.", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } else {
                State.capturing = true
            }
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener { startKugo() }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, KugoForegroundService::class.java))
            State.enabled = false
        }
        findViewById<Button>(R.id.btnAccess).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun startKugo() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        val i = Intent(this, KugoForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
    }

    private fun saveAndShow() {
        Prefs.saveCps(this, sbMin.progress + 1, sbMax.progress + 1)
        showCps()
    }

    private fun showCps() {
        tvMin.text = "${sbMin.progress + 1} CPS"
        tvMax.text = "${sbMax.progress + 1} CPS"
    }

    private fun a11yEnabled(): Boolean = KugoAccessibilityService.instance != null

    private fun refreshIndicators() {
        val running = State.serviceRunning
        val dot = findViewById<TextView>(R.id.tvStatusDot)
        dot.text = if (running) "● EM EXECUÇÃO" else "● PARADO"
        dot.setTextColor(if (running) 0xFFB6FF00.toInt() else 0xFFFF4D4D.toInt())

        findViewById<TextView>(R.id.tvKey).text =
            if (State.capturing) "AGUARDANDO TECLA…" else Prefs.keyName(this)
        findViewById<Button>(R.id.btnKey).text =
            if (State.capturing) "PRESSIONE UMA TECLA…" else "SELECIONAR KEY"

        findViewById<TextView>(R.id.tvAuto).text =
            "Auto Clicker: " + if (State.enabled) "ATIVADO" else "DESATIVADO"
        findViewById<TextView>(R.id.tvMouse).text =
            "Mouse: " + if (State.mouseDown) "PRESSIONADO" else "SOLTO"
        findViewById<TextView>(R.id.tvCps).text =
            "CPS atual: " + if (State.currentCps > 0f) String.format("%.1f", State.currentCps) else "—"
        findViewById<TextView>(R.id.tvKeyInd).text = "Tecla configurada: " + Prefs.keyName(this)
        findViewById<TextView>(R.id.tvA11y).text =
            "Acessibilidade: " + if (a11yEnabled()) "ATIVA" else "DESATIVADA"
        findViewById<TextView>(R.id.tvRoot).text = "Leitura do mouse (root): " + when (State.rootOk) {
            true -> "OK"
            false -> "SEM ROOT — mouse não detectável"
            null -> if (running) "verificando…" else "—"
        }
    }

    override fun onResume() {
        super.onResume()
        ui.post(poll)
    }

    override fun onPause() {
        ui.removeCallbacks(poll)
        State.capturing = false
        super.onPause()
    }

    private abstract class SimpleSeek : SeekBar.OnSeekBarChangeListener {
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }
}
