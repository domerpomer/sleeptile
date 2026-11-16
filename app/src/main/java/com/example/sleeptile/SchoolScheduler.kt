package com.example.sleeptile

import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

/**
 * Главный «движок» школьного режима.
 * Один будильник всегда стоит на ближайшее событие (напоминание, начало или конец урока).
 * При срабатывании приводим звук в порядок, шлём уведомление и ставим следующий будильник.
 */
object SchoolScheduler {
    private const val REQ_ALARM = 7000
    private const val JOB_PERIODIC = 7001
    private const val JOB_NOW = 7002

    private val SILENT_RULE = Rule(900001, RuleAction.SILENT, 0, -1, ALL_DAYS, true)
    private val DND_RULE = Rule(900002, RuleAction.DND, 0, -1, ALL_DAYS, true)
    private val VIBRATE_RULE = Rule(900003, RuleAction.VIBRATE, 0, -1, ALL_DAYS, true)

    fun isActive(ctx: Context): Boolean =
        (SchoolSettings.enabled(ctx) && SchoolSettings.klass(ctx).isNotEmpty()) || SchoolData.testActive(ctx)

    private fun alarmPi(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, REQ_ALARM, Intent(ctx, SchoolReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun hms(ms: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))

    /** Вызывается из будильника, из фоновой задачи, из приложения и после перезагрузки. */
    @Synchronized
    fun tick(ctx: Context) {
        val now = System.currentTimeMillis()
        val data = SchoolData.load(ctx)
        applyState(ctx, data, now)
        if (isActive(ctx)) runDue(ctx, data, now)
        if (isActive(ctx)) SchoolWallpaper.autoCheck(ctx, data, now)
        reschedule(ctx, data, now)
        ensureJob(ctx)
        if (isActive(ctx) && now - SchoolSettings.lastFetchAt(ctx) > 10 * 60_000L) fetchSoon(ctx)
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

    private fun ensureJob(ctx: Context) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        try {
            if (isActive(ctx)) {
                if (js.getPendingJob(JOB_PERIODIC) == null) {
                    js.schedule(
                        JobInfo.Builder(JOB_PERIODIC, ComponentName(ctx, FetchJobService::class.java))
                            .setPeriodic(15 * 60_000L)
                            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                            .setPersisted(true)
                            .build()
                    )
                }
            } else {
                js.cancel(JOB_PERIODIC)
            }
        } catch (e: Exception) {
        }
    }

    /** Разовая загрузка расписания в ближайшее время (в течение минуты). */
    fun fetchSoon(ctx: Context) {
        try {
            val js = ctx.getSystemService(JobScheduler::class.java)
            js.schedule(
                JobInfo.Builder(JOB_NOW, ComponentName(ctx, FetchJobService::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setOverrideDeadline(60_000L)
                    .build()
            )
        } catch (e: Exception) {
        }
    }
}
