package com.kugo.autoclicker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.random.Random

/**
 * Motor de cliques. Uma única tarefa agendada (sem sobreposição) repete o toque
 * somente enquanto: serviço iniciado + ativado pela tecla + mouse pressionado.
 */
object Engine {
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var scheduled = false
    private var inFlight = false
    private var lastDelayMs = 80L

    /** Última posição conhecida do ponteiro (pixels da tela). -1 = desconhecida. */
    @Volatile var x = -1f
    @Volatile var y = -1f

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun shouldRun(): Boolean {
        // Segurança: se a ponte parou de avisar por 3 s, solta o mouse (evita cliques presos).
        if (State.mouseExt && SystemClock.elapsedRealtime() - State.lastExtTime > 3000L) {
            State.mouseExt = false
        }
        return State.serviceRunning && State.enabled && State.mouseDown &&
            KugoAccessibilityService.instance != null
    }

    /** Reavalia o estado; chamável de qualquer thread. */
    fun refresh() {
        handler.post { evaluate() }
    }

    private fun evaluate() {
        appContext?.let { Notifier.update(it) }
        if (shouldRun()) {
            if (!scheduled) {
                scheduled = true
                handler.postDelayed(tick, nextDelayMs())
            }
        } else {
            stopNow()
        }
    }

    private fun stopNow() {
        handler.removeCallbacks(tick)
        scheduled = false
        inFlight = false
        State.currentCps = 0f
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!shouldRun()) {
                stopNow()
                return
            }
            if (!inFlight) fire()
            handler.postDelayed(this, nextDelayMs())
        }
    }

    private fun nextDelayMs(): Long {
        val ctx = appContext
        val a = if (ctx != null) Prefs.minCps(ctx) else 8
        val b = if (ctx != null) Prefs.maxCps(ctx) else 14
        val lo = minOf(a, b).toFloat()
        val hi = maxOf(a, b).toFloat()
        val cps = if (lo == hi) lo else lo + Random.nextFloat() * (hi - lo)
        State.currentCps = cps
        lastDelayMs = (1000f / cps).toLong().coerceAtLeast(1L)
        return lastDelayMs
    }

    private fun fire() {
        val svc = KugoAccessibilityService.instance ?: return
        val dm = svc.resources.displayMetrics
        // Cursor capturado pelo jogo (modo relativo): não há posição absoluta confiável.
        // Toca no centro da tela (onde fica a mira no Minecraft).
        val useCenter = State.relativeMode || x < 0f || y < 0f ||
            (State.mouseExt && !State.mouseA11y)
        val px = if (useCenter) dm.widthPixels / 2f else x
        val py = if (useCenter) dm.heightPixels / 2f else y
        // Shizuku conectado e modo 1/2: clique de mouse REAL (botão esquerdo) via shell.
        val mode = appContext?.let { Prefs.clickMode(it) } ?: 3
        if (mode != 3 && ShizukuBridge.connected) {
            State.tapsSent++
            State.lastTap = "(" + px.toInt() + "," + py.toInt() + ") shell modo " + mode
            ShizukuBridge.click(px, py, mode)
            return
        }
        // Duração do toque proporcional ao intervalo (5–25 ms) para nunca sobrepor o próximo.
        val dur = (lastDelayMs * 0.4).toLong().coerceIn(5L, 25L)
        inFlight = true
        State.tapsSent++
        State.lastTap = "(" + px.toInt() + "," + py.toInt() + ") " + dur + "ms" +
            if (useCenter) " centro" else ""
        val ok = svc.tap(px, py, dur, handler) { inFlight = false }
        if (!ok) {
            inFlight = false
            State.tapsRejected++
        }
    }
}
