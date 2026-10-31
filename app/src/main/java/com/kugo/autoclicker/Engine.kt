package com.kugo.autoclicker

import android.content.Context
import android.os.Handler
import android.os.Looper
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

    /** Última posição conhecida do ponteiro (pixels da tela). -1 = desconhecida. */
    @Volatile var x = -1f
    @Volatile var y = -1f

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private fun shouldRun(): Boolean =
        State.serviceRunning && State.enabled && State.mouseDown &&
            KugoAccessibilityService.instance != null

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
        return (1000f / cps).toLong().coerceAtLeast(1L)
    }

    private fun fire() {
        val svc = KugoAccessibilityService.instance ?: return
        val dm = svc.resources.displayMetrics
        val px = if (x >= 0f) x else dm.widthPixels / 2f
        val py = if (y >= 0f) y else dm.heightPixels / 2f
        inFlight = true
        val ok = svc.tap(px, py, handler) { inFlight = false }
        if (!ok) inFlight = false
    }
}
