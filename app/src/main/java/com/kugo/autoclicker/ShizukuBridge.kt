package com.kugo.autoclicker

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.widget.Toast
import rikka.shizuku.Shizuku

/** Liga o app ao Shizuku: permissão, serviço de shell, leitura do botão e cliques reais. */
object ShizukuBridge {
    private const val PKG = "moe.shizuku.privileged.api"
    private const val REQ = 4242

    @Volatile private var service: IBinder? = null
    @Volatile private var bound = false
    private var inited = false
    private var appCtx: Context? = null

    val connected: Boolean get() = service != null

    private val callback = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            val now = SystemClock.elapsedRealtime()
            when (code) {
                ShellService.CB_BUTTON -> {
                    val isDown = data.readInt() == 1
                    State.lastExtPing = now
                    State.lastExtTime = now
                    State.extEvents++
                    State.mouseExt = isDown
                    Engine.refresh()
                }
                ShellService.CB_PING -> State.lastExtPing = now
                else -> return super.onTransact(code, data, reply, flags)
            }
            return true
        }
    }

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder
            transact(ShellService.T_SET_CALLBACK, 0) { it.writeStrongBinder(callback) }
            transact(ShellService.T_START, 0) {}
            Engine.refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            State.mouseExt = false
            State.lastExtPing = 0L
            Engine.refresh()
        }
    }

    private fun transact(code: Int, flags: Int, fill: (Parcel) -> Unit) {
        val b = service ?: return
        val d = Parcel.obtain()
        try {
            fill(d)
            b.transact(code, d, null, flags)
        } catch (_: Throwable) {
        } finally {
            d.recycle()
        }
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        if (inited) return
        inited = true
        try {
            Shizuku.addBinderReceivedListenerSticky { tryBind() }
            Shizuku.addBinderDeadListener {
                service = null
                bound = false
                State.mouseExt = false
                State.lastExtPing = 0L
                Engine.refresh()
            }
            Shizuku.addRequestPermissionResultListener { _, result ->
                if (result == PackageManager.PERMISSION_GRANTED) tryBind()
                Engine.refresh()
            }
        } catch (_: Throwable) {}
    }

    private fun isInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (_: Throwable) {
        false
    }

    private fun running(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    private fun permitted(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    /** Liga o serviço de shell se tudo estiver pronto e o KUGO estiver iniciado. */
    fun tryBind() {
        val ctx = appCtx ?: return
        if (bound || !State.serviceRunning) return
        if (!running() || !permitted()) return
        try {
            val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellService::class.java.name))
                .daemon(false)
                .processNameSuffix("kugo")
                .version(1)
            Shizuku.bindUserService(args, conn)
            bound = true
        } catch (_: Throwable) {
            bound = false
        }
    }

    fun unbind() {
        val ctx = appCtx ?: return
        try { transact(ShellService.T_STOP, 0) {} } catch (_: Throwable) {}
        try {
            val args = Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellService::class.java.name))
                .daemon(false)
                .processNameSuffix("kugo")
                .version(1)
            Shizuku.unbindUserService(args, conn, true)
        } catch (_: Throwable) {}
        service = null
        bound = false
        State.mouseExt = false
        State.lastExtPing = 0L
    }

    /** Botão "CONECTAR SHIZUKU": guia o usuário pelo que estiver faltando. */
    fun connect(activity: Activity) {
        init(activity)
        when {
            !isInstalled(activity) -> {
                Toast.makeText(activity, "Instale o Shizuku e volte aqui.", Toast.LENGTH_LONG).show()
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/download/")))
            }
            !running() -> {
                Toast.makeText(activity, "Abra o Shizuku e toque em Iniciar (Depuração sem fio ou root).", Toast.LENGTH_LONG).show()
                activity.packageManager.getLaunchIntentForPackage(PKG)?.let { activity.startActivity(it) }
            }
            !permitted() -> {
                try { Shizuku.requestPermission(REQ) } catch (_: Throwable) {}
            }
            else -> {
                if (!State.serviceRunning) {
                    Toast.makeText(activity, "Toque em INICIAR para ligar o serviço.", Toast.LENGTH_LONG).show()
                }
                tryBind()
            }
        }
    }

    fun click(x: Float, y: Float, mode: Int) {
        transact(ShellService.T_CLICK, IBinder.FLAG_ONEWAY) {
            it.writeFloat(x)
            it.writeFloat(y)
            it.writeInt(mode)
        }
    }

    fun statusText(ctx: Context): String = when {
        connected -> "CONECTADO (leitura do mouse e cliques via shell)"
        !isInstalled(ctx) -> "não instalado"
        !running() -> "instalado, mas não iniciado"
        !permitted() -> "sem permissão para o KUGO"
        !State.serviceRunning -> "permitido (toque em INICIAR)"
        else -> "conectando…"
    }
}
