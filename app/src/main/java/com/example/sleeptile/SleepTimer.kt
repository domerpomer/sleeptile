package com.example.sleeptile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService

/** Таймер сна: по истечении времени останавливает любое аудио. */
object SleepTimer {
    private const val KEY_END = "end_at"
    private const val KEY_MINUTES = "default_minutes"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("sleep_timer", Context.MODE_PRIVATE)

    private fun pending(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, SleepTimerReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun defaultMinutes(ctx: Context): Int = prefs(ctx).getInt(KEY_MINUTES, 30)

    fun setDefaultMinutes(ctx: Context, minutes: Int) {
        prefs(ctx).edit().putInt(KEY_MINUTES, minutes).apply()
    }

    /** Время срабатывания (мс) или 0, если таймер не запущен. */
    fun endAt(ctx: Context): Long {
        val end = prefs(ctx).getLong(KEY_END, 0L)
        return if (end > System.currentTimeMillis()) end else 0L
    }

    fun isRunning(ctx: Context): Boolean = endAt(ctx) != 0L

    fun start(ctx: Context, minutes: Int) {
        val end = System.currentTimeMillis() + minutes * 60_000L
        prefs(ctx).edit().putLong(KEY_END, end).apply()
        Alarms.setExact(ctx, end, pending(ctx))
        refreshTile(ctx)
    }

    fun cancel(ctx: Context) {
        Alarms.cancel(ctx, pending(ctx))
        prefs(ctx).edit().remove(KEY_END).apply()
        refreshTile(ctx)
    }

    fun onFired(ctx: Context) {
        prefs(ctx).edit().remove(KEY_END).apply()
        refreshTile(ctx)
    }

    fun refreshTile(ctx: Context) {
        try {
            TileService.requestListeningState(ctx, ComponentName(ctx, SleepTileService::class.java))
        } catch (e: Exception) {
        }
    }
}
