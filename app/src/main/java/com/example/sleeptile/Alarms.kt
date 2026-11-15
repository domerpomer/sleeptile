package com.example.sleeptile

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
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

    fun cancel(ctx: Context, pi: PendingIntent) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pi)
    }
}
