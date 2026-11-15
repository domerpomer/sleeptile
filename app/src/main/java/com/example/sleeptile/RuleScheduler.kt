package com.example.sleeptile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

object RuleScheduler {
    const val EXTRA_ID = "rule_id"
    const val EXTRA_KIND = "kind"
    const val KIND_START = "start"
    const val KIND_END = "end"

    /** Пересчитать все правила: поставить будильники и привести состояние телефона в порядок. */
    fun syncAll(ctx: Context) {
        for (r in RuleStore.load(ctx)) sync(ctx, r)
    }

    fun sync(ctx: Context, r: Rule) {
        cancelAlarms(ctx, r.id)
        if (!r.enabled) {
            RuleExecutor.end(ctx, r)
            return
        }
        scheduleAlarms(ctx, r)
        if (isInWindow(r, System.currentTimeMillis())) {
            RuleExecutor.start(ctx, r)
        } else {
            RuleExecutor.end(ctx, r)
        }
    }

    fun scheduleAlarms(ctx: Context, r: Rule) {
        val now = System.currentTimeMillis()
        val start = nextTrigger(r.startMin, r.days, now)
        if (start != null) Alarms.setExact(ctx, start, pending(ctx, r.id, KIND_START))
        if (r.endMin >= 0) {
            val end = nextTrigger(r.endMin, ALL_DAYS, now)
            if (end != null) Alarms.setExact(ctx, end, pending(ctx, r.id, KIND_END))
        }
    }

    fun cancelAlarms(ctx: Context, id: Int) {
        Alarms.cancel(ctx, pending(ctx, id, KIND_START))
        Alarms.cancel(ctx, pending(ctx, id, KIND_END))
    }

    private fun pending(ctx: Context, id: Int, kind: String): PendingIntent {
        val intent = Intent(ctx, RuleReceiver::class.java)
            .putExtra(EXTRA_ID, id)
            .putExtra(EXTRA_KIND, kind)
        val code = id * 2 + (if (kind == KIND_START) 0 else 1)
        return PendingIntent.getBroadcast(
            ctx, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun dayIndex(cal: Calendar): Int = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // пн=0 ... вс=6

    private fun dayOn(mask: Int, day: Int): Boolean = ((mask shr day) and 1) == 1

    /** Ближайший момент после [after], когда на часах [minuteOfDay] и день входит в [days]. */
    fun nextTrigger(minuteOfDay: Int, days: Int, after: Long): Long? {
        for (offset in 0..7) {
            val cal = Calendar.getInstance()
            cal.timeInMillis = after
            cal.add(Calendar.DAY_OF_YEAR, offset)
            cal.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
            cal.set(Calendar.MINUTE, minuteOfDay % 60)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > after && dayOn(days, dayIndex(cal))) return cal.timeInMillis
        }
        return null
    }

    /** Попадает ли момент [now] внутрь интервала правила (с учётом перехода через полночь). */
    fun isInWindow(r: Rule, now: Long): Boolean {
        if (r.endMin < 0 || r.endMin == r.startMin) return false
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = dayIndex(cal)
        val yesterday = (today + 6) % 7
        return if (r.startMin < r.endMin) {
            nowMin >= r.startMin && nowMin < r.endMin && dayOn(r.days, today)
        } else {
            (nowMin >= r.startMin && dayOn(r.days, today)) ||
                (nowMin < r.endMin && dayOn(r.days, yesterday))
        }
    }
}
