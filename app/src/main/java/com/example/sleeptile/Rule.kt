package com.example.sleeptile

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

const val ALL_DAYS = 127 // биты 0..6 = пн..вс

val DAY_SHORT = arrayOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")
val DAY_FULL = arrayOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")

enum class RuleAction(val title: String, val hasEnd: Boolean) {
    SILENT("Режим без звука", true),
    VIBRATE("Только вибрация", true),
    DND("Не беспокоить", true),
    STOP_AUDIO("Остановить всё аудио", false)
}

data class Rule(
    val id: Int,
    val action: RuleAction,
    val startMin: Int, // минуты от полуночи
    val endMin: Int,   // -1, если конца нет
    val days: Int,     // маска дней
    val enabled: Boolean
)

object RuleStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("rules", Context.MODE_PRIVATE)

    fun load(ctx: Context): MutableList<Rule> {
        val out = mutableListOf<Rule>()
        try {
            val arr = JSONArray(prefs(ctx).getString("list", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Rule(
                        o.getInt("id"),
                        RuleAction.valueOf(o.getString("action")),
                        o.getInt("start"),
                        o.getInt("end"),
                        o.getInt("days"),
                        o.getBoolean("enabled")
                    )
                )
            }
        } catch (e: Exception) {
            // повреждённые данные — начинаем с пустого списка
        }
        return out
    }

    fun save(ctx: Context, rules: List<Rule>) {
        val arr = JSONArray()
        for (r in rules) {
            arr.put(
                JSONObject()
                    .put("id", r.id)
                    .put("action", r.action.name)
                    .put("start", r.startMin)
                    .put("end", r.endMin)
                    .put("days", r.days)
                    .put("enabled", r.enabled)
            )
        }
        prefs(ctx).edit().putString("list", arr.toString()).apply()
    }

    fun nextId(ctx: Context): Int {
        val p = prefs(ctx)
        val id = p.getInt("next_id", 1)
        p.edit().putInt("next_id", id + 1).apply()
        return id
    }
}
