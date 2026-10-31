package com.kugo.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent

/**
 * - Recebe teclas globais (onKeyEvent) para ativar/desativar e capturar a tecla.
 * - Executa os toques com dispatchGesture.
 */
class KugoAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: KugoAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
        Engine.init(applicationContext)
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                val info = serviceInfo
                info.motionEventSources = InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_MOUSE_RELATIVE
                serviceInfo = info
            } catch (_: Throwable) {}
        }
        Engine.refresh()
    }

    /**
     * Android 14+ (API 34): eventos de mouse entregues ao serviço de acessibilidade, sem root.
     * O estado "pressionado" segue o botão primário (esquerdo).
     */
    override fun onMotionEvent(event: MotionEvent) {
        State.motionEvents++
        val relative = event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)
        State.relativeMode = relative
        State.lastMotion = (if (relative) "RELATIVO " else "ABSOLUTO ") +
            MotionEvent.actionToString(event.actionMasked) + " botões=" + event.buttonState

        // Em modo relativo (cursor capturado) X/Y são deltas, não posição: não usar.
        if (!relative && (event.rawX > 0f || event.rawY > 0f)) {
            Engine.x = event.rawX
            Engine.y = event.rawY
        }
        val ending = event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        State.mouseA11y = !ending && (event.buttonState and MotionEvent.BUTTON_PRIMARY) != 0
        Engine.refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        State.releaseMouse()
        Engine.refresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        State.enabled = false
        State.releaseMouse()
        Engine.refresh()
        return super.onUnbind(intent)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return false

        State.lastKeyName = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        Prefs.keyName(this)
        Engine.refresh()

        if (State.capturing) {
            Prefs.saveKey(this, event.keyCode)
            State.capturing = false
            return true
        }

        if (State.serviceRunning && event.keyCode == Prefs.keyCode(this)) {
            State.enabled = !State.enabled
            Engine.refresh()
        }
        // Não consome a tecla: ela continua funcionando normalmente no app/jogo.
        return false
    }

    /** Executa um toque curto. Retorna false se o sistema recusou o gesto. */
    fun tap(x: Float, y: Float, durationMs: Long, handler: Handler, onDone: () -> Unit): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) {
                State.tapsOk++
                onDone()
            }
            override fun onCancelled(g: GestureDescription?) {
                State.tapsCancelled++
                onDone()
            }
        }, handler)
    }
}
