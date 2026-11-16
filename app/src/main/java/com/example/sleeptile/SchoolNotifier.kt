package com.example.sleeptile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object SchoolNotifier {
    private const val CH_BREAK = "school_break"
    private const val CH_LESSON = "school_lesson"
    private const val ID_LESSON = 4001
    private const val ID_BREAK = 4002

    private fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_BREAK, "Перемены и напоминания", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_LESSON, "Начало урока (без звука)", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun post(ctx: Context, ev: SchoolEvent) {
        if (!SchoolSettings.notify(ctx)) return
        when (ev.type) {
            EvType.PRE -> if (!SchoolSettings.notifyPre(ctx)) return
            EvType.START -> if (!SchoolSettings.notifyStart(ctx)) return
            EvType.END -> {
                if (ev.next == null) {
                    if (!SchoolSettings.notifyEnd(ctx)) return
                } else {
                    if (!SchoolSettings.notifyBreak(ctx)) return
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannels(ctx)
        val l = ev.lesson
        var title = ""
        var text = ""
        var channel = CH_BREAK
        var id = ID_BREAK

        when (ev.type) {
            EvType.PRE -> {
                title = "Первый урок сегодня в ${SchoolData.hm(l.start)}"
                text = "${l.n}-й урок: ${SchoolData.describeLesson(l)}"
                channel = CH_BREAK
                id = ID_BREAK
            }
            EvType.START -> {
                title = "Урок ${l.n}: ${l.subject}"
                text = (if (l.room.isEmpty()) "" else "Кабинет ${l.room} · ") + "до ${SchoolData.hm(l.end)}"
                channel = CH_LESSON
                id = ID_LESSON
            }
            EvType.END -> {
                val next = ev.next
                if (next == null) {
                    title = "Уроки на сегодня закончились"
                    text = "Звук возвращён."
                } else {
                    val gap = next.n != l.n + 1
                    title = (if (gap) "Окно до " else "Перемена до ") + SchoolData.hm(next.start)
                    text = "Дальше — урок ${next.n}: ${SchoolData.describeLesson(next)}"
                }
                channel = CH_BREAK
                id = ID_BREAK
            }
        }

        val open = PendingIntent.getActivity(
            ctx, 3, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: Exception) {
        }
    }
}
