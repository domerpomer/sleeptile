package com.example.sleeptile

import android.content.Context
import android.content.SharedPreferences

object SchoolSettings {
    const val DEFAULT_BASE_URL = "https://raw.githubusercontent.com/domerpomer/sleeptile-data/main/"

    val CLASSES = listOf(
        "5а", "5б", "5в", "5г", "5д",
        "6а", "6б", "6в", "6г", "6д",
        "7а", "7б", "7в", "7г", "7д",
        "8а", "8б", "8в", "8г",
        "9а", "9б", "9в", "9г",
        "10а", "10б", "11а", "11б"
    )

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("school", Context.MODE_PRIVATE)

    // ----- универсальные доступы (для экрана настроек) -----
    fun b(ctx: Context, key: String, def: Boolean): Boolean = prefs(ctx).getBoolean(key, def)
    fun setB(ctx: Context, key: String, v: Boolean) { prefs(ctx).edit().putBoolean(key, v).apply() }
    fun i(ctx: Context, key: String, def: Int): Int = prefs(ctx).getInt(key, def)
    fun setI(ctx: Context, key: String, v: Int) { prefs(ctx).edit().putInt(key, v).apply() }
    fun s(ctx: Context, key: String, def: String): String = prefs(ctx).getString(key, def) ?: def
    fun setS(ctx: Context, key: String, v: String) { prefs(ctx).edit().putString(key, v).apply() }

    // ----- основное -----
    fun enabled(ctx: Context): Boolean = b(ctx, "enabled", false)
    fun setEnabled(ctx: Context, v: Boolean) = setB(ctx, "enabled", v)

    fun klass(ctx: Context): String = s(ctx, "klass", "")
    fun setKlass(ctx: Context, v: String) = setS(ctx, "klass", v)

    fun group(ctx: Context): Int = i(ctx, "group", 1)
    fun setGroup(ctx: Context, v: Int) = setI(ctx, "group", v)

    // ----- уведомления -----
    fun notify(ctx: Context): Boolean = b(ctx, "notify", true)
    fun notifyPre(ctx: Context): Boolean = b(ctx, "notify_pre", true)
    fun preMinutes(ctx: Context): Int = i(ctx, "pre_minutes", 30)
    fun notifyStart(ctx: Context): Boolean = b(ctx, "notify_start", true)
    fun notifyBreak(ctx: Context): Boolean = b(ctx, "notify_break", true)
    fun notifyEnd(ctx: Context): Boolean = b(ctx, "notify_end", true)

    // ----- звук на уроках -----
    fun silent(ctx: Context): Boolean = b(ctx, "silent", true)
    fun setSilent(ctx: Context, v: Boolean) = setB(ctx, "silent", v)
    fun ringMode(ctx: Context): Int = i(ctx, "ring_mode", 0)   // 0 — беззвучный, 1 — вибрация
    fun dnd(ctx: Context): Boolean = b(ctx, "dnd", true)
    fun setDnd(ctx: Context, v: Boolean) = setB(ctx, "dnd", v)
    fun keepGapMinutes(ctx: Context): Int = i(ctx, "keep_gap", 0) // не возвращать звук на переменах до N минут

    // ----- данные -----
    fun baseUrl(ctx: Context): String = s(ctx, "base_url", DEFAULT_BASE_URL)
    fun setBaseUrl(ctx: Context, v: String) = setS(ctx, "base_url", v)

    fun lastRun(ctx: Context): Long = prefs(ctx).getLong("last_run", 0L)
    fun setLastRun(ctx: Context, v: Long) { prefs(ctx).edit().putLong("last_run", v).apply() }

    fun lastFetchAt(ctx: Context): Long = prefs(ctx).getLong("last_fetch_at", 0L)
    fun lastStatus(ctx: Context): String = s(ctx, "last_status", "")
    fun setLastFetch(ctx: Context, at: Long, status: String) {
        prefs(ctx).edit().putLong("last_fetch_at", at).putString("last_status", status).apply()
    }
}
