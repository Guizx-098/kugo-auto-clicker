package com.kugo.autoclicker

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/** Mantém o app vivo em segundo plano com uma notificação discreta (sem janelas sobre o jogo). */
class KugoForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Engine.init(applicationContext)
        Prefs.keyName(applicationContext) // atualiza o nome em cache
        State.serviceRunning = true
        Notifier.reset()

        val notification = Notifier.build(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Notifier.ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Notifier.ID, notification)
        }

        RootInputReader.start(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        State.serviceRunning = false
        State.enabled = false
        State.mouseDown = false
        RootInputReader.stop()
        Engine.refresh()
        super.onDestroy()
    }
}
