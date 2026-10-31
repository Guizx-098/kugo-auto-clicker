package com.kugo.autoclicker

/** Estado compartilhado entre serviços, motor e interface. */
object State {
    /** Serviço em primeiro plano iniciado pelo botão INICIAR. */
    @Volatile var serviceRunning = false

    /** Auto Clicker ativado pela tecla (apenas habilita; não clica sozinho). */
    @Volatile var enabled = false

    /** Botão esquerdo do mouse fisicamente pressionado (via leitura de /dev/input). */
    @Volatile var mouseDown = false

    /** Aguardando o usuário pressionar uma tecla para definir a tecla de ativação. */
    @Volatile var capturing = false

    @Volatile var currentCps = 0f

    /** null = ainda verificando, true = root disponível, false = sem root. */
    @Volatile var rootOk: Boolean? = null
}
