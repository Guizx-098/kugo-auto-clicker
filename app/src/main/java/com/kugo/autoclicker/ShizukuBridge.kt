package com.kugo.autoclicker

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.widget.Toast
import rikka.shizuku.Shizuku

/** Liga o app ao Shizuku: permissão, serviço de shell, leitura do botão e cliques reais. */
object ShizukuBridge {
    private const val PKG = "moe.shizuku.privileged.api"
    private const val REQ = 4242

    private val main = Handler(Looper.getMainLooper())

    @Volatile private var service: IBinder? = null
    @Volatile private var bound = false
    @Volatile private var bindAt = 0L
    @Volatile private var bindCalls = 0
    @Volatile private var lastError = "nenhum"
    @Volatile private var shellInfo = "(sem conexão com o serviço de shell)"
    private var inited = false
    private var watcherOn = false
    private var infoThread: Thread? = null
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
            lastError = "nenhum"
            transact(ShellService.T_SET_CALLBACK, 0) { it.writeStrongBinder(callback) }
            transact(ShellService.T_START, 0) {}
            startInfoPoll()
            Engine.refresh()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
            State.mouseExt = false
            State.lastExtPing = 0L
            lastError = "serviço de shell desconectou"
            Engine.refresh()
        }
    }

    private fun transact(code: Int, flags: Int, fill: (Parcel) -> Unit) {
        val b = service ?: return
        val d = Parcel.obtain()
        try {
            fill(d)
            b.transact(code, d, null, flags)
        } catch (t: Throwable) {
            lastError = "transact($code): $t"
        } finally {
            d.recycle()
        }
    }

    private fun startInfoPoll() {
        if (infoThread?.isAlive == true) return
        infoThread = Thread({
            while (service != null) {
                val b = service ?: break
                val d = Parcel.obtain()
                val r = Parcel.obtain()
                try {
                    b.transact(ShellService.T_INFO, d, r, 0)
                    shellInfo = r.readString() ?: "(vazio)"
                } catch (t: Throwable) {
                    shellInfo = "erro ao ler o serviço de shell: $t"
                } finally {
                    d.recycle()
                    r.recycle()
                }
                try { Thread.sleep(1000) } catch (_: InterruptedException) {}
            }
        }, "kugo-info").also { it.isDaemon = true; it.start() }
    }

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        if (!inited) {
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
            } catch (t: Throwable) {
                lastError = "init: $t"
            }
        }
        startWatcher()
    }

    /** A cada 2 s confere se dá para conectar e tenta de novo se travar. */
    private fun startWatcher() {
        if (watcherOn) return
        watcherOn = true
        main.post(object : Runnable {
            override fun run() {
                try {
                    if (State.serviceRunning && running() && permitted()) {
                        if (!bound) {
                            tryBind()
                        } else if (service == null && SystemClock.elapsedRealtime() - bindAt > 8000L) {
                            lastError = "sem conexão após 8 s; tentando de novo"
                            unbindQuietly()
                            tryBind()
                        }
                    }
                } catch (t: Throwable) {
                    lastError = "watcher: $t"
                }
                main.postDelayed(this, 2000L)
            }
        })
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

    private fun args(ctx: Context) =
        Shizuku.UserServiceArgs(ComponentName(ctx.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("kugo")
            .version(1)

    /** Liga o serviço de shell se tudo estiver pronto e o KUGO estiver iniciado. */
    fun tryBind() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post { tryBind() }
            return
        }
        val ctx = appCtx ?: return
        if (bound || !State.serviceRunning) return
        if (!running() || !permitted()) return
        try {
            bindCalls++
            bindAt = SystemClock.elapsedRealtime()
            Shizuku.bindUserService(args(ctx), conn)
            bound = true
        } catch (t: Throwable) {
            bound = false
            lastError = "bindUserService: $t"
        }
    }

    private fun unbindQuietly() {
        val ctx = appCtx ?: return
        try { Shizuku.unbindUserService(args(ctx), conn, true) } catch (_: Throwable) {}
        service = null
        bound = false
    }

    fun unbind() {
        try { transact(ShellService.T_STOP, 0) {} } catch (_: Throwable) {}
        unbindQuietly()
        State.mouseExt = false
        State.lastExtPing = 0L
        shellInfo = "(sem conexão com o serviço de shell)"
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
                try { Shizuku.requestPermission(REQ) } catch (t: Throwable) { lastError = "requestPermission: $t" }
            }
            else -> {
                // Já está tudo pronto: liga o serviço do KUGO (se preciso) e conecta.
                if (!State.serviceRunning && activity is MainActivity) activity.startKugo()
                main.postDelayed({ tryBind() }, 400L)
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
        !running() -> "instalado, mas o KUGO não recebeu o sinal do Shizuku (inicie o Shizuku)"
        !permitted() -> "sem permissão para o KUGO"
        !State.serviceRunning -> "permitido (toque em INICIAR)"
        else -> "conectando… (" + lastError + ")"
    }

    /** Relatório completo para diagnóstico (copiável). */
    fun report(ctx: Context): String {
        val sb = StringBuilder()
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (_: Throwable) { "?" }
        sb.append("KUGO v").append(ver)
            .append(" | Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Shizuku instalado: ").append(if (isInstalled(ctx)) "sim" else "não").append('\n')
        sb.append("Shizuku ativo para o KUGO (binder): ").append(if (running()) "sim" else "NÃO").append('\n')
        val uid = try { Shizuku.getUid().toString() } catch (_: Throwable) { "?" }
        val sver = try { Shizuku.getVersion().toString() } catch (_: Throwable) { "?" }
        sb.append("Shizuku uid=").append(uid).append(" versão=").append(sver).append('\n')
        sb.append("Permissão do KUGO: ").append(if (permitted()) "concedida" else "NÃO concedida").append('\n')
        sb.append("KUGO iniciado (serviço): ").append(if (State.serviceRunning) "sim" else "NÃO").append('\n')
        sb.append("bindUserService: chamadas=").append(bindCalls)
            .append(", ligado=").append(bound)
            .append(", conectado=").append(connected).append('\n')
        sb.append("Erro do app: ").append(lastError).append('\n')
        sb.append("Modo de clique: ").append(Prefs.clickModeName(ctx)).append('\n')
        sb.append("Avisos do mouse (shell): ").append(State.extEvents)
            .append(" | mouse pressionado agora: ").append(State.mouseDown).append('\n')
        sb.append("--- serviço de shell ---\n").append(shellInfo)
        return sb.toString()
    }
}
