package com.kugo.autoclicker

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
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
                else -> super.onTransact(code, data, reply, flags)
            }
        } catch (_: Throwable) {
            false
        }
    }

    // ---------- leitura do botão ----------

    private fun send(code: Int, v: Int) {
        val cb = callback ?: return
        val d = Parcel.obtain()
        try {
            d.writeInt(v)
            cb.transact(code, d, null, IBinder.FLAG_ONEWAY)
        } catch (_: Throwable) {
        } finally {
            d.recycle()
        }
    }

    private fun start() {
        if (running) return
        running = true

        Thread({
            try {
                val p = Runtime.getRuntime().exec(arrayOf("getevent", "-l"))
                proc = p
                val br = p.inputStream.bufferedReader()
                while (running) {
                    val line = br.readLine() ?: break
                    if (!line.contains("BTN_LEFT")) continue
                    if (line.contains("DOWN")) {
                        down = true
                        send(CB_BUTTON, 1)
                    } else if (line.contains("UP")) {
                        down = false
                        send(CB_BUTTON, 0)
                    }
                }
            } catch (_: Throwable) {
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
        for (cn in classes) {
            try {
                val cls = Class.forName(cn)
                val gi = cls.getDeclaredMethod("getInstance")
                gi.isAccessible = true
                val inst = gi.invoke(null) ?: continue
                for (m in cls.declaredMethods) {
                    val pt = m.parameterTypes
                    if (m.name == "injectInputEvent" && pt.isNotEmpty() && pt[0] == InputEvent::class.java) {
                        m.isAccessible = true
                        injectTarget = inst
                        injectMethod = m
                        injectArgs = pt.size
                        return true
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return false
    }

    private fun inject(e: InputEvent): Boolean {
        val m = injectMethod ?: return false
        return try {
            val r = if (injectArgs >= 3) m.invoke(injectTarget, e, 0, -1) else m.invoke(injectTarget, e, 0)
            (r as? Boolean) ?: true
        } catch (_: Throwable) {
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
            // Cursor capturado (Minecraft em jogo): eventos de mouse relativo.
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
