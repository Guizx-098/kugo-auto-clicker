package com.kugo.autoclicker

/** Estado compartilhado entre serviços, motor e interface. */
object State {
    /** Serviço em primeiro plano iniciado pelo botão INICIAR. */
    @Volatile var serviceRunning = false

    /** Auto Clicker ativado pela tecla (apenas habilita; não clica sozinho). */
    @Volatile var enabled = false

    /** Botão esquerdo pressionado segundo o AccessibilityService (Android 14+, sem root). */
    @Volatile var mouseA11y = false

    /** Botão esquerdo pressionado segundo a leitura de /dev/input (root). */
    @Volatile var mouseRoot = false

    /** Botão esquerdo do mouse fisicamente pressionado (qualquer fonte). */
    val mouseDown: Boolean get() = mouseA11y || mouseRoot

    fun releaseMouse() {
        mouseA11y = false
        mouseRoot = false
    }

    /** Aguardando o usuário pressionar uma tecla para definir a tecla de ativação. */
    @Volatile var capturing = false

    @Volatile var currentCps = 0f

    /** null = ainda verificando, true = root disponível, false = sem root. */
    @Volatile var rootOk: Boolean? = null

    // Diagnóstico
    @Volatile var lastKeyName = "nenhuma"
    @Volatile var inputEvents = 0L
    @Volatile var lastInput = "nenhum"
    @Volatile var motionEvents = 0L
    @Volatile var lastMotion = "nenhum"
}
