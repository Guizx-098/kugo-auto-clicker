package com.kugo.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.view.KeyEvent
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
        Engine.refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        State.mouseDown = false
        Engine.refresh()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        State.enabled = false
        State.mouseDown = false
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
    fun tap(x: Float, y: Float, handler: Handler, onDone: () -> Unit): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, 5L))
            .build()
        return dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) = onDone()
            override fun onCancelled(g: GestureDescription?) = onDone()
        }, handler)
    }
}
