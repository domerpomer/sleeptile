package com.example.sleeptile

import android.content.Context
import android.os.PowerManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Короткий журнал: когда на самом деле сработал таймер или правило. Показывается внизу главного экрана. */
object EventLog {
    private const val KEY = "log"
    private const val MAX = 40

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("event_log", Context.MODE_PRIVATE)

    fun add(ctx: Context, text: String) {
        val time = SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault()).format(Date())
        val list = all(ctx).toMutableList()
        list.add(0, "$time  $text")
        while (list.size > MAX) list.removeAt(list.size - 1)
        prefs(ctx).edit().putString(KEY, list.joinToString("\n")).apply()
    }

    fun all(ctx: Context): List<String> {
        val s = prefs(ctx).getString(KEY, "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split("\n")
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY).apply()
    }

    fun screenState(ctx: Context): String {
        val pm = ctx.getSystemService(PowerManager::class.java)
        return if (pm.isInteractive) "экран включён" else "экран выключен"
    }
}
