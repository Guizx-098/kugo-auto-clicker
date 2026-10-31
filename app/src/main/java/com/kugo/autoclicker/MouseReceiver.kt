package com.kugo.autoclicker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/**
 * Recebe o estado do botão esquerdo enviado pela ponte Termux/ADB (termux/kugo-mouse.sh).
 * Extra "down" = true/false. A ponte reenvia "true" a cada ~1 s enquanto o botão está
 * pressionado; se os avisos pararem, o motor solta o mouse sozinho (veja Engine).
 */
class MouseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.kugo.autoclicker.MOUSE") return
        Engine.init(context.applicationContext)
        State.extEvents++
        State.lastExtTime = SystemClock.elapsedRealtime()
        State.mouseExt = intent.getBooleanExtra("down", false)
        Engine.refresh()
    }
}
