package com.example.sleeptile

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Скачивает schedule.json и bells.json по адресу из настроек и сохраняет их на телефоне. */
object SchoolFetcher {

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.useCaches = false
        c.setRequestProperty("Cache-Control", "no-cache")
        try {
            val code = c.responseCode
            if (code != 200) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun save(ctx: Context, name: String, text: String) {
        val tmp = File(ctx.filesDir, "$name.tmp")
        tmp.writeText(text, Charsets.UTF_8)
        tmp.renameTo(File(ctx.filesDir, name))
    }

    /** Возвращает короткий статус для показа на экране. */
    fun fetch(ctx: Context): String {
        var base = SchoolSettings.baseUrl(ctx).trim()
        if (!base.endsWith("/")) base += "/"
        val stamp = System.currentTimeMillis() / 60_000L

        val status = try {
            val sch = download("${base}schedule.json?t=$stamp")
            JSONObject(sch).getJSONObject("days") // проверка, что файл правильный
            save(ctx, SchoolData.FILE_SCHEDULE, sch)
            try {
                val bel = download("${base}bells.json?t=$stamp")
                JSONObject(bel).getJSONArray("mon")
                save(ctx, SchoolData.FILE_BELLS, bel)
            } catch (e: Exception) {
                // звонки не обновились — остаются прежние
            }
            "Обновлено в " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        } catch (e: Exception) {
            "Не удалось загрузить (" + (e.message ?: e.javaClass.simpleName) + "), используются сохранённые данные"
        }
        SchoolSettings.setLastFetch(ctx, System.currentTimeMillis(), status)
        return status
    }
}
