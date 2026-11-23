package com.example.sleeptile

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Главный «движок» школьного режима.
 * Будильник всегда стоит на ближайшее событие (напоминание, начало или конец урока).
 * При срабатывании переключаем звук, шлём уведомление и ставим следующий будильник.
 */
object SchoolScheduler {
    private const val REQ_ALARM = 7000

    private val SILENT_RULE = Rule(900001, RuleAction.SILENT, 0, -1, ALL_DAYS, true)
    private val DND_RULE = Rule(900002, RuleAction.DND, 0, -1, ALL_DAYS, true)
    private val VIBRATE_RULE = Rule(900003, RuleAction.VIBRATE, 0, -1, ALL_DAYS, true)

    /** Активен, если школьный режим включён и заданы уроки, либо идёт проверка. */
    fun isActive(ctx: Context): Boolean =
        (SchoolSettings.enabled(ctx) && SchoolData.hasAnyLocalLessons(ctx)) || SchoolData.testActive(ctx)

    private fun alarmPi(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ_ALARM, Intent(ctx, SchoolReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun hms(ms: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

    /** Вызывается из будильника, из приложения и после перезагрузки. */
    @Synchronized
    fun tick(ctx: Context) {
        val now = System.currentTimeMillis()
        val data = SchoolData.load(ctx)
        applyState(ctx, data, now)
        if (isActive(ctx)) runDue(ctx, data, now)
        if (isActive(ctx)) SchoolWallpaper.autoCheck(ctx, data, now)
        reschedule(ctx, data, now)
    }

    /** Беззвучный режим (или вибрация) и «Не беспокоить» — только пока идёт урок. */
    private fun applyState(ctx: Context, data: SchoolDataSet, now: Long) {
        val lessons = if (isActive(ctx)) {
            SchoolData.lessonsForDay(ctx, data, LocalDate.now())
        } else {
            emptyList()
        }
        val t = now + 1000L
        val windows = SchoolData.windows(lessons, SchoolSettings.keepGapMinutes(ctx))
        val inWindow = windows.any { t >= it[0] && t < it[1] }

        val soundOn = inWindow && SchoolSettings.silent(ctx)
        val wantSilent = soundOn && SchoolSettings.ringMode(ctx) == 0
        val wantVibrate = soundOn && SchoolSettings.ringMode(ctx) == 1
        val wantDnd = inWindow && SchoolSettings.dnd(ctx)

        if (!wantDnd) RuleExecutor.end(ctx, DND_RULE)
        if (!wantSilent) RuleExecutor.end(ctx, SILENT_RULE)
        if (!wantVibrate) RuleExecutor.end(ctx, VIBRATE_RULE)
        if (wantSilent) RuleExecutor.start(ctx, SILENT_RULE)
        if (wantVibrate) RuleExecutor.start(ctx, VIBRATE_RULE)
        if (wantDnd) RuleExecutor.start(ctx, DND_RULE)
    }

    /** Уведомления для событий, время которых наступило (но не старше 10 минут). */
    private fun runDue(ctx: Context, data: SchoolDataSet, now: Long) {
        val from = maxOf(SchoolSettings.lastRun(ctx), now - 10 * 60_000L)
        val until = now + 1000L
        val events = SchoolData.eventsForDay(ctx, data, LocalDate.now()).sortedBy { it.at }
        for (ev in events) {
            if (ev.at > from && ev.at <= until) {
                val what = when (ev.type) {
                    EvType.PRE -> "напоминание перед уроком ${ev.lesson.n}"
                    EvType.START -> "начало урока ${ev.lesson.n}"
                    EvType.END -> "конец урока ${ev.lesson.n}"
                }
                EventLog.add(ctx, "Школа: $what, план ${hms(ev.at)}, ${EventLog.screenState(ctx)}")
                SchoolNotifier.post(ctx, ev)
            }
        }
        SchoolSettings.setLastRun(ctx, until)
    }

    private fun reschedule(ctx: Context, data: SchoolDataSet, now: Long) {
        if (!isActive(ctx)) {
            Alarms.cancel(ctx, alarmPi(ctx))
            return
        }
        var next = now + 6 * 3600_000L // не реже чем раз в 6 часов
        val today = LocalDate.now()
        for (d in 0..7) {
            val events = SchoolData.eventsForDay(ctx, data, today.plusDays(d.toLong()))
            val candidate = events.map { it.at }.filter { it > now + 500L }.minOrNull()
            if (candidate != null) {
                if (candidate < next) next = candidate
                break
            }
        }
        Alarms.setExact(ctx, next, alarmPi(ctx))
    }
}
