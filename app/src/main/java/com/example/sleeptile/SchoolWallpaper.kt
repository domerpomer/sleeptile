package com.example.sleeptile

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.text.TextUtils
import java.time.LocalDate

/**
 * Рисует расписание на день как картинку и ставит её на обои (экран блокировки / рабочий стол / оба).
 * Картинка собирается из данных расписания, поэтому фото для этого не нужно.
 */
object SchoolWallpaper {
    const val ACTION_APPLY = "com.example.sleeptile.APPLY_WALLPAPER"
    private const val CH = "school_wallpaper"
    private const val ID_OFFER = 4003

    fun enabled(ctx: Context): Boolean = SchoolSettings.b(ctx, "wp_enabled", false)

    // ---------- рисование ----------

    fun render(ctx: Context, date: LocalDate, lessons: List<ResolvedLesson>): Bitmap {
        val dm = ctx.resources.displayMetrics
        var w = dm.widthPixels
        var h = dm.heightPixels
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val wm = ctx.getSystemService(android.view.WindowManager::class.java)
                val bounds = wm.currentWindowMetrics.bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    w = bounds.width()
                    h = bounds.height()
                }
            } catch (e: Exception) {
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        val theme = SchoolSettings.i(ctx, "wp_theme", 0)
        val bg1: Int
        val bg2: Int
        val fg: Int
        val sub: Int
        when (theme) {
            1 -> { bg1 = 0xFFF5F7FA.toInt(); bg2 = 0xFFDDE5EE.toInt(); fg = 0xFF1A1F26.toInt(); sub = 0xFF5A6572.toInt() }
            2 -> { bg1 = 0xFF1A237E.toInt(); bg2 = 0xFF3949AB.toInt(); fg = 0xFFFFFFFF.toInt(); sub = 0xFFC5CAE9.toInt() }
            else -> { bg1 = 0xFF0E1216.toInt(); bg2 = 0xFF1C2733.toInt(); fg = 0xFFFFFFFF.toInt(); sub = 0xFFB0BEC5.toInt() }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), bg1, bg2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        val sizeChoice = SchoolSettings.i(ctx, "wp_size", 1)
        val sizeFactor = when (sizeChoice) {
            0 -> 0.85f
            2 -> 1.2f
            else -> 1.0f
        }
        val showTimes = SchoolSettings.b(ctx, "wp_times", true)
        val showRooms = SchoolSettings.b(ctx, "wp_rooms", true)

        val margin = w * 0.07f
        val posChoice = SchoolSettings.i(ctx, "wp_pos", 0)
        val top = when (posChoice) {
            1 -> h * 0.36f
            2 -> h * 0.14f
            else -> h * 0.50f
        }

        // если строк много — уменьшаем, чтобы всё влезло
        var base = w / 24f * sizeFactor
        val rows = lessons.size + 2.2f
        val need = rows * base * 1.7f
        val room = h * 0.94f - top
        if (need > room) base *= room / need

        // заголовок: день + класс (если выбран)
        val klassStr = SchoolSettings.klass(ctx)
        val title = SchoolData.dayTitle(date) + if (klassStr.isNotEmpty()) " · $klassStr" else ""
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = sub
        paint.textSize = base * 1.1f
        paint.textAlign = Paint.Align.LEFT
        var y = top + base * 1.2f
        c.drawText(title, margin, y, paint)
        y += base * 1.9f

        // строки уроков
        val numX = margin
        val timeX = margin + base * 1.7f
        val textX = if (showTimes) timeX + base * 3.9f else timeX
        val rightEdge = w - margin

        for (l in lessons) {
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = base
            paint.textAlign = Paint.Align.LEFT
            paint.color = sub
            c.drawText(l.n.toString(), numX, y, paint)

            if (showTimes) {
                paint.typeface = Typeface.DEFAULT
                c.drawText(SchoolData.hm(l.start), timeX, y, paint)
            }

            var roomWidth = 0f
            if (showRooms && l.room.isNotEmpty()) {
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textAlign = Paint.Align.RIGHT
                paint.color = sub
                c.drawText(l.room, rightEdge, y, paint)
                roomWidth = paint.measureText(l.room) + base * 0.6f
                paint.textAlign = Paint.Align.LEFT
            }

            paint.typeface = Typeface.DEFAULT_BOLD
            paint.color = fg
            val maxWidth = rightEdge - textX - roomWidth
            val text = TextUtils.ellipsize(l.subject, android.text.TextPaint(paint), maxWidth, TextUtils.TruncateAt.END)
            c.drawText(text.toString(), textX, y, paint)

            y += base * 1.7f
        }
        return bmp
    }

    // ---------- установка ----------

    private fun signature(ctx: Context, date: LocalDate, lessons: List<ResolvedLesson>): String {
        val sb = StringBuilder()
        sb.append(date.toString()).append('|').append(SchoolSettings.klass(ctx)).append('|')
            .append(SchoolSettings.group(ctx)).append('|')
        for (l in lessons) sb.append(l.n).append(l.subject).append(l.room).append(l.start).append(';')
        return sb.toString().hashCode().toString()
    }

    private fun applyBitmap(ctx: Context, bmp: Bitmap): Boolean {
        return try {
            val wm = WallpaperManager.getInstance(ctx)
            if (Build.VERSION.SDK_INT >= 24) {
                val target = SchoolSettings.i(ctx, "wp_target", 0)
                val flags = when (target) {
                    1 -> WallpaperManager.FLAG_SYSTEM
                    2 -> WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
                    else -> WallpaperManager.FLAG_LOCK
                }
                wm.setBitmap(bmp, null, true, flags)
            } else {
                wm.setBitmap(bmp)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Ставит обои прямо сейчас. Возвращает короткий итог для экрана. */
    fun applyNow(ctx: Context): String {
        val data = SchoolData.load(ctx)
        val rel = SchoolData.anyRelevantDay(ctx, data)
        if (rel == null) {
            return "Нет уроков в расписании. Введите уроки на вкладке «Школа»."
        }
        val ok = applyBitmap(ctx, render(ctx, rel.first, rel.second))
        return if (ok) {
            SchoolSettings.setS(ctx, "wp_last_sig", signature(ctx, rel.first, rel.second))
            EventLog.add(ctx, "Обои: поставлено расписание на ${SchoolData.dayTitle(rel.first)}")
            "Обои поставлены: " + SchoolData.dayTitle(rel.first)
        } else {
            "Система не дала поставить обои"
        }
    }

    fun reset(ctx: Context): String {
        return try {
            if (Build.VERSION.SDK_INT >= 24) {
                val target = SchoolSettings.i(ctx, "wp_target", 0)
                val flags = when (target) {
                    1 -> WallpaperManager.FLAG_SYSTEM
                    2 -> WallpaperManager.FLAG_LOCK or WallpaperManager.FLAG_SYSTEM
                    else -> WallpaperManager.FLAG_LOCK
                }
                WallpaperManager.getInstance(ctx).clear(flags)
            } else {
                WallpaperManager.getInstance(ctx).clear()
            }
            SchoolSettings.setS(ctx, "wp_last_sig", "")
            "Обои сброшены"
        } catch (e: Exception) {
            "Не удалось сбросить обои"
        }
    }

    // ---------- автоматическая проверка ----------

    /** Если расписание изменилось — спрашиваем (уведомлением) или сразу ставим обои. */
    fun autoCheck(ctx: Context, data: SchoolDataSet, now: Long) {
        if (!enabled(ctx) || SchoolData.testActive(ctx)) return
        val rel = SchoolData.relevantDay(ctx, data, now) ?: return
        val sig = signature(ctx, rel.first, rel.second)
        if (sig == SchoolSettings.s(ctx, "wp_last_sig", "")) return

        if (SchoolSettings.i(ctx, "wp_mode", 0) == 1) {
            if (applyBitmap(ctx, render(ctx, rel.first, rel.second))) {
                SchoolSettings.setS(ctx, "wp_last_sig", sig)
                EventLog.add(ctx, "Обои: поставлено расписание на ${SchoolData.dayTitle(rel.first)}")
            }
        } else if (sig != SchoolSettings.s(ctx, "wp_offered_sig", "")) {
            offer(ctx, rel.first)
            SchoolSettings.setS(ctx, "wp_offered_sig", sig)
        }
    }

    private fun offer(ctx: Context, date: LocalDate) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "Предложение обоев", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val applyPi = PendingIntent.getBroadcast(
            ctx, 11, Intent(ctx, WallpaperReceiver::class.java).setAction(ACTION_APPLY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            ctx, 12, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_tile), "Поставить", applyPi
        ).build()
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Новое расписание: " + SchoolData.dayTitle(date))
            .setContentText("Поставить его на обои?")
            .addAction(action)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(ID_OFFER, n)
        } catch (e: Exception) {
        }
    }

    fun cancelOffer(ctx: Context) {
        try {
            ctx.getSystemService(NotificationManager::class.java).cancel(ID_OFFER)
        } catch (e: Exception) {
        }
    }
}
