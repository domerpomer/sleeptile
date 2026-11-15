package com.example.sleeptile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object Alarms {
    /** Ставит точный будильник; если система не разрешила точный — обычный (может сработать с небольшим опозданием). */
    fun setExact(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /**
     * Самый «привилегированный» будильник: Origin OS почти не откладывает такие срабатывания.
     * Пока он стоит, в строке состояния виден значок будильника.
     */
    fun setAlarmClock(ctx: Context, at: Long, pi: PendingIntent) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            val show = PendingIntent.getActivity(
                ctx, 2, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        } catch (e: SecurityException) {
            setExact(ctx, at, pi)
        }
    }

    fun cancel(ctx: Context, pi: PendingIntent) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
    }
}
