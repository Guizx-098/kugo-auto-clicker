package com.kugo.autoclicker

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Executa no processo de SHELL (uid 2000) iniciado pelo Shizuku. Faz duas coisas que um app comum não faz:
 *  1) lê o botão esquerdo do mouse em /dev/input (getevent), mesmo com o cursor capturado pelo jogo;
 *  2) injeta cliques de mouse reais (botão esquerdo) no sistema.
 * Comunicação com o app: transações Binder (sem AIDL).
 */
class ShellService : Binder() {

    companion object {
        const val T_SET_CALLBACK = 1
        const val T_START = 2
        const val T_STOP = 3
        const val T_CLICK = 4
        const val T_INFO = 5

        const val CB_BUTTON = 1
        const val CB_PING = 2

        const val MODE_ABS = 1
        const val MODE_REL = 2
    }

    @Volatile private var callback: IBinder? = null
    @Volatile private var running = false
    @Volatile private var down = false
    private var proc: Process? = null
    private var injectTarget: Any? = null
    private var injectMethod: Method? = null
    private var injectArgs = 2

    // diagnóstico
    @Volatile private var geteventState = "não iniciado"
    @Volatile private var lines = 0L
    @Volatile private var btnEvents = 0L
    @Volatile private var lastBtn = "nenhum"
    private val firstLines = ArrayList<String>()
    @Volatile private var injOk = 0L
    @Volatile private var injFail = 0L
    @Volatile private var lastError = "nenhum"
    @Volatile private var injectorName = "não preparado"
    @Volatile private var cbSent = 0L

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return try {
            when (code) {
                T_SET_CALLBACK -> { callback = data.readStrongBinder(); true }
                T_START -> { start(); true }
                T_STOP -> { stop(); true }
                T_CLICK -> {
                    val x = data.readFloat()
                    val y = data.readFloat()
                    val mode = data.readInt()
                    click(x, y, mode)
                    true
                }
                T_INFO -> { reply?.writeString(info()); true }
                else -> super.onTransact(code, data, reply, flags)
            }
        } catch (t: Throwable) {
            lastError = "onTransact($code): " + t
            false
        }
    }

    private fun info(): String {
        val fl = synchronized(firstLines) { firstLines.joinToString(" | ") }
        return "uid=" + android.os.Process.myUid() + " pid=" + android.os.Process.myPid() + "\n" +
            "getevent: " + geteventState + ", linhas=" + lines + ", BTN_LEFT=" + btnEvents +
            " (último: " + lastBtn + ")\n" +
            "primeiras linhas: " + (if (fl.isEmpty()) "nenhuma" else fl) + "\n" +
            "avisos enviados ao app: " + cbSent + "\n" +
            "injetor: " + injectorName + ", ok=" + injOk + ", falhas=" + injFail + "\n" +
            "último erro: " + lastError
    }

    // ---------- leitura do botão ----------

    private fun send(code: Int, v: Int) {
        val cb = callback ?: return
        val d = Parcel.obtain()
        try {
            d.writeInt(v)
            cb.transact(code, d, null, IBinder.FLAG_ONEWAY)
            cbSent++
        } catch (t: Throwable) {
            lastError = "aviso ao app: " + t
        } finally {
            d.recycle()
        }
    }

    private fun start() {
        if (running) return
        running = true
        geteventState = "iniciando"

        Thread({
            try {
                val p = ProcessBuilder("/system/bin/getevent", "-l")
                    .redirectErrorStream(true)
                    .start()
                proc = p
                geteventState = "lendo"
                val br = p.inputStream.bufferedReader()
                while (running) {
                    val line = br.readLine() ?: break
                    lines++
                    synchronized(firstLines) {
                        if (firstLines.size < 3) firstLines.add(line.take(90))
                    }
                    if (!line.contains("BTN_LEFT")) continue
                    btnEvents++
                    lastBtn = line.trim().takeLast(40)
                    if (line.contains("DOWN")) {
                        down = true
                        send(CB_BUTTON, 1)
                    } else if (line.contains("UP")) {
                        down = false
                        send(CB_BUTTON, 0)
                    }
                }
                val code = try { p.waitFor() } catch (_: Throwable) { -1 }
                geteventState = "encerrado (exit=$code)"
            } catch (t: Throwable) {
                geteventState = "erro"
                lastError = "getevent: " + t
            } finally {
                down = false
                send(CB_BUTTON, 0)
            }
        }, "kugo-getevent").start()

        // Sinal de vida: o app solta o mouse sozinho se estes avisos pararem.
        Thread({
            while (running) {
                if (down) send(CB_BUTTON, 1) else send(CB_PING, 0)
                try { Thread.sleep(500) } catch (_: InterruptedException) {}
            }
        }, "kugo-heartbeat").start()
    }

    private fun stop() {
        running = false
        down = false
        try { proc?.destroy() } catch (_: Throwable) {}
        proc = null
        callback = null
    }

    // ---------- injeção de cliques de mouse ----------

    private fun prepareInjector(): Boolean {
        if (injectMethod != null) return true
        val classes = arrayOf(
            "android.hardware.input.InputManagerGlobal",
            "android.hardware.input.InputManager"
        )
        val tried = StringBuilder()
        for (cn in classes) {
            try {
                val cls = Class.forName(cn)
                val gi = cls.getDeclaredMethod("getInstance")
                gi.isAccessible = true
                val inst = gi.invoke(null)
                if (inst == null) { tried.append(cn.substringAfterLast('.')).append(": instância nula; "); continue }
                for (m in cls.declaredMethods) {
                    val pt = m.parameterTypes
                    if (m.name == "injectInputEvent" && pt.isNotEmpty() && pt[0] == InputEvent::class.java) {
                        m.isAccessible = true
                        injectTarget = inst
                        injectMethod = m
                        injectArgs = pt.size
                        injectorName = cn.substringAfterLast('.') + ".injectInputEvent/" + pt.size
                        return true
                    }
                }
                tried.append(cn.substringAfterLast('.')).append(": sem injectInputEvent; ")
            } catch (t: Throwable) {
                val c = if (t is InvocationTargetException) (t.cause ?: t) else t
                tried.append(cn.substringAfterLast('.')).append(": ").append(c.toString().take(80)).append("; ")
            }
        }
        injectorName = "NÃO ENCONTRADO"
        lastError = "injetor: $tried"
        return false
    }

    private fun inject(e: InputEvent): Boolean {
        val m = injectMethod ?: return false
        return try {
            val r = if (injectArgs >= 3) m.invoke(injectTarget, e, 0, -1) else m.invoke(injectTarget, e, 0)
            val ok = (r as? Boolean) ?: true
            if (ok) injOk++ else { injFail++; lastError = "injectInputEvent retornou false" }
            ok
        } catch (t: Throwable) {
            val c = if (t is InvocationTargetException) (t.cause ?: t) else t
            injFail++
            lastError = "inject: " + c.toString().take(120)
            false
        }
    }

    private fun motion(
        action: Int, downTime: Long, x: Float, y: Float,
        buttons: Int, source: Int, actionButton: Int
    ) {
        val pp = MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_MOUSE
        }
        val pc = MotionEvent.PointerCoords().apply {
            this.x = x
            this.y = y
            pressure = 1f
            size = 1f
        }
        val e = MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, 1,
            arrayOf(pp), arrayOf(pc), 0, buttons, 1f, 1f, 0, 0, source, 0
        )
        if (actionButton != 0) {
            try {
                MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType)
                    .invoke(e, actionButton)
            } catch (_: Throwable) {}
        }
        try { inject(e) } finally { e.recycle() }
    }

    private fun click(x: Float, y: Float, mode: Int) {
        if (!prepareInjector()) return
        val t = SystemClock.uptimeMillis()
        val b = MotionEvent.BUTTON_PRIMARY
        if (mode == MODE_REL) {
            val s = InputDevice.SOURCE_MOUSE_RELATIVE
            motion(MotionEvent.ACTION_DOWN, t, 0f, 0f, b, s, 0)
            motion(MotionEvent.ACTION_BUTTON_PRESS, t, 0f, 0f, b, s, b)
            motion(MotionEvent.ACTION_BUTTON_RELEASE, t, 0f, 0f, 0, s, b)
            motion(MotionEvent.ACTION_UP, t, 0f, 0f, 0, s, 0)
        } else {
            val s = InputDevice.SOURCE_MOUSE
            motion(MotionEvent.ACTION_DOWN, t, x, y, b, s, 0)
            motion(MotionEvent.ACTION_BUTTON_PRESS, t, x, y, b, s, b)
            motion(MotionEvent.ACTION_BUTTON_RELEASE, t, x, y, 0, s, b)
            motion(MotionEvent.ACTION_UP, t, x, y, 0, s, 0)
        }
    }
}
