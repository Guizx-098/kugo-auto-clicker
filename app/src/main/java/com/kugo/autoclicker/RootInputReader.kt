package com.kugo.autoclicker

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Lê eventos de entrada brutos (/dev/input) com `su -c getevent -l` para saber quando o
 * botão esquerdo do mouse (BTN_LEFT) ou o toque (BTN_TOUCH) está pressionado.
 * REQUER ROOT dentro do Android/emulador.
 */
object RootInputReader {
    @Volatile private var thread: Thread? = null
    @Volatile private var stopFlag = false
    @Volatile private var proc: Process? = null

    private val maxX = HashMap<String, Int>()
    private val maxY = HashMap<String, Int>()

    fun start(context: Context) {
        if (thread?.isAlive == true) return
        stopFlag = false
        val app = context.applicationContext
        State.rootOk = null
        thread = Thread({ run(app) }, "kugo-input").also { it.isDaemon = true; it.start() }
    }

    fun stop() {
        stopFlag = true
        try { proc?.destroy() } catch (_: Throwable) {}
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "pkill -f 'getevent -l'"))
        } catch (_: Throwable) {}
        proc = null
        State.mouseRoot = false
        Engine.refresh()
    }

    private fun run(app: Context) {
        try {
            if (!hasRoot()) {
                State.rootOk = false
                Engine.refresh()
                return
            }
            State.rootOk = true
            Engine.refresh()
            loadRanges()

            val p = ProcessBuilder("su", "-c", "exec getevent -l")
                .redirectErrorStream(true).start()
            proc = p
            val reader = BufferedReader(InputStreamReader(p.inputStream))

            var rawX = -1L
            var rawY = -1L
            while (!stopFlag) {
                val line = reader.readLine() ?: break
                val t = line.trim().split(Regex("\\s+"))
                if (t.size < 4) continue
                if (t[1] != "EV_KEY" && t[1] != "EV_ABS") continue
                val dev = t[0].trimEnd(':')
                State.inputEvents++

                when (t[2]) {
                    "BTN_LEFT", "BTN_TOUCH" -> {
                        State.lastInput = t[2] + " " + t[3]
                        when (t[3]) {
                            "DOWN" -> State.mouseRoot = true
                            "UP" -> State.mouseRoot = false
                        }
                        Engine.refresh()
                    }
                    "ABS_MT_POSITION_X", "ABS_X" -> {
                        rawX = t[3].toLongOrNull(16) ?: continue
                        Engine.x = scale(rawX, maxX[dev], app.resources.displayMetrics.widthPixels)
                    }
                    "ABS_MT_POSITION_Y", "ABS_Y" -> {
                        rawY = t[3].toLongOrNull(16) ?: continue
                        Engine.y = scale(rawY, maxY[dev], app.resources.displayMetrics.heightPixels)
                    }
                }
            }
        } catch (_: Throwable) {
            // falha na leitura: cai para o bloco finally e solta o mouse por segurança
        } finally {
            // Mecanismo de segurança: se a leitura acabar/falhar, nunca continuar clicando.
            State.mouseRoot = false
            Engine.refresh()
            try { proc?.destroy() } catch (_: Throwable) {}
            proc = null
        }
    }

    private fun scale(raw: Long, max: Int?, screen: Int): Float =
        if (max != null && max > 0) raw.toFloat() * screen / max else raw.toFloat()

    private fun hasRoot(): Boolean = try {
        val p = ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out.contains("uid=0")
    } catch (_: Throwable) {
        false
    }

    private fun loadRanges() {
        try {
            val p = ProcessBuilder("su", "-c", "getevent -lp").redirectErrorStream(true).start()
            var dev = ""
            val devRe = Regex("add device \\d+: (\\S+)")
            val absRe = Regex("ABS_(?:MT_POSITION_)?([XY])\\s*:.*max (\\d+)")
            p.inputStream.bufferedReader().forEachLine { l ->
                devRe.find(l)?.let { dev = it.groupValues[1] }
                absRe.find(l)?.let {
                    val max = it.groupValues[2].toIntOrNull() ?: return@let
                    if (it.groupValues[1] == "X") maxX[dev] = max else maxY[dev] = max
                }
            }
            p.waitFor()
        } catch (_: Throwable) {}
    }
}
