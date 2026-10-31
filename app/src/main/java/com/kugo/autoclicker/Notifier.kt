package com.kugo.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Notificação discreta e permanente que mostra se o Auto Clicker está ATIVADO ou DESATIVADO. */
object Notifier {
    private const val CHANNEL = "kugo_status"
    const val ID = 1
    private var lastKey = ""

    private fun title() = "KUGO • " + if (State.enabled) "ATIVADO" else "DESATIVADO"

    private fun short() =
        "Mouse: " + (if (State.mouseDown) "PRESSIONADO" else "SOLTO") +
            "  |  Tecla: " + Prefs.keyNameOrNone()

    private fun big(ctx: Context): String {
        val root = when (State.rootOk) {
            true -> "OK"
            false -> "SEM ROOT (mouse não detectável)"
            null -> "verificando…"
        }
        return "Auto Clicker: " + (if (State.enabled) "ATIVADO" else "DESATIVADO") + "\n" +
            "Mouse: " + (if (State.mouseDown) "PRESSIONADO" else "SOLTO") + "\n" +
            "Tecla configurada: " + Prefs.keyName(ctx) + " (última recebida: " + State.lastKeyName + ")\n" +
            "Acessibilidade: " + (if (KugoAccessibilityService.instance != null) "ATIVA" else "DESATIVADA") + "\n" +
            "Leitura do mouse (root): " + root
    }

    fun build(ctx: Context): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Status do KUGO", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(ctx)
        return b.setContentTitle(title())
            .setContentText(short())
            .setStyle(Notification.BigTextStyle().bigText(big(ctx)))
            .setSmallIcon(
                if (State.enabled) android.R.drawable.presence_online
                else android.R.drawable.presence_offline
            )
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    /** Atualiza a notificação somente se o texto mudou (evita excesso de atualizações). */
    fun update(ctx: Context) {
        if (!State.serviceRunning) return
        val key = title() + short() + big(ctx)
        if (key == lastKey) return
        lastKey = key
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(ID, build(ctx))
        } catch (_: Throwable) {}
    }

    fun reset() { lastKey = "" }
}
