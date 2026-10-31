package com.kugo.autoclicker

import android.content.Context
import android.view.KeyEvent

/** Persistência simples das configurações (SharedPreferences). */
object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("kugo", Context.MODE_PRIVATE)

    fun minCps(c: Context): Int = sp(c).getInt("min", 8).coerceIn(1, 100)
    fun maxCps(c: Context): Int = sp(c).getInt("max", 14).coerceIn(1, 100)
    fun keyCode(c: Context): Int = sp(c).getInt("key", -1)

    fun saveCps(c: Context, min: Int, max: Int) {
        val lo = minOf(min, max).coerceIn(1, 100)
        val hi = maxOf(min, max).coerceIn(1, 100)
        sp(c).edit().putInt("min", lo).putInt("max", hi).apply()
    }

    fun saveKey(c: Context, code: Int) {
        sp(c).edit().putInt("key", code).apply()
    }

    fun keyName(c: Context): String {
        val code = keyCode(c)
        return if (code < 0) "NENHUMA" else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
    }
}
