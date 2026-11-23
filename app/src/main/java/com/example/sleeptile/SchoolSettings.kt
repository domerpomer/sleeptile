package com.example.sleeptile

import android.content.Context
import android.content.SharedPreferences

object SchoolSettings {

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("school", Context.MODE_PRIVATE)

    fun b(ctx: Context, key: String, def: Boolean): Boolean = prefs(ctx).getBoolean(key, def)
    fun setB(ctx: Context, key: String, v: Boolean) { prefs(ctx).edit().putBoolean(key, v).apply() }
    fun i(ctx: Context, key: String, def: Int): Int = prefs(ctx).getInt(key, def)
    fun setI(ctx: Context, key: String, v: Int) { prefs(ctx).edit().putInt(key, v).apply() }
    fun s(ctx: Context, key: String, def: String): String = prefs(ctx).getString(key, def) ?: def
    fun setS(ctx: Context, key: String, v: String) { prefs(ctx).edit().putString(key, v).apply() }

    // ----- основное -----
    fun enabled(ctx: Context): Boolean = b(ctx, "enabled", false)
    fun setEnabled(ctx: Context, v: Boolean) = setB(ctx, "enabled", v)

    // ----- уведомления -----
    fun notify(ctx: Context): Boolean = b(ctx, "notify", true)
    fun notifyPre(ctx: Context): Boolean = b(ctx, "notify_pre", true)
    fun preMinutes(ctx: Context): Int = 15
    fun notifyStart(ctx: Context): Boolean = b(ctx, "notify_start", true)
    fun notifyBreak(ctx: Context): Boolean = b(ctx, "notify_break", true)
    fun notifyEnd(ctx: Context): Boolean = b(ctx, "notify_end", true)

    // ----- звук на уроках -----
    fun silent(ctx: Context): Boolean = b(ctx, "silent", true)
    fun setSilent(ctx: Context, v: Boolean) = setB(ctx, "silent", v)
    fun ringMode(ctx: Context): Int = i(ctx, "ring_mode", 0)   // 0 — беззвучный, 1 — вибрация
    fun dnd(ctx: Context): Boolean = b(ctx, "dnd", true)
    fun setDnd(ctx: Context, v: Boolean) = setB(ctx, "dnd", v)
    fun keepGapMinutes(ctx: Context): Int = i(ctx, "keep_gap", 0)

    // ----- питание на обоях -----
    // 0: Ничего, 1: Только завтрак, 2: Только обед, 3: Завтрак и обед
    fun mealPlan(ctx: Context): Int = i(ctx, "meal_plan", 0)
    fun setMealPlan(ctx: Context, v: Int) = setI(ctx, "meal_plan", v)

    // ----- экстренная отмена уроков на 1 день («КОД КРАСНЫЙ») -----
    fun canceledDate(ctx: Context): String = s(ctx, "canceled_date", "")
    fun setCanceledDate(ctx: Context, date: String) = setS(ctx, "canceled_date", date)

    // ----- временное смещение звонков на 1 день -----
    fun customBellsDate(ctx: Context): String = s(ctx, "custom_bells_date", "")
    fun customBellsJson(ctx: Context): String = s(ctx, "custom_bells_json", "")
    fun setCustomBells(ctx: Context, date: String, json: String) {
        prefs(ctx).edit().putString("custom_bells_date", date).putString("custom_bells_json", json).apply()
    }
    fun clearCustomBells(ctx: Context) {
        prefs(ctx).edit().remove("custom_bells_date").remove("custom_bells_json").apply()
    }

    // Совместимость
    fun klass(ctx: Context): String = s(ctx, "klass", "")
    fun setKlass(ctx: Context, v: String) = setS(ctx, "klass", v)
    fun group(ctx: Context): Int = i(ctx, "group", 1)
    fun setGroup(ctx: Context, v: Int) = setI(ctx, "group", v)

    fun lastRun(ctx: Context): Long = prefs(ctx).getLong("last_run", 0L)
    fun setLastRun(ctx: Context, v: Long) { prefs(ctx).edit().putLong("last_run", v).apply() }
}
