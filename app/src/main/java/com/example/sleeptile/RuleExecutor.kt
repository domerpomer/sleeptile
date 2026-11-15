package com.example.sleeptile

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/** Применяет и откатывает действия правил. Запоминает, что было до включения, чтобы вернуть как было. */
object RuleExecutor {

    private fun state(ctx: Context) =
        ctx.getSharedPreferences("rule_state", Context.MODE_PRIVATE)

    fun start(ctx: Context, r: Rule) {
        if (r.action == RuleAction.STOP_AUDIO) return
        val p = state(ctx)
        if (p.contains("act_${r.id}")) return // уже включено

        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        try {
            val prev: Int = when (r.action) {
                RuleAction.SILENT -> {
                    val old = am.ringerMode
                    am.ringerMode = AudioManager.RINGER_MODE_SILENT
                    old
                }
                RuleAction.VIBRATE -> {
                    val old = am.ringerMode
                    am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                    old
                }
                RuleAction.DND -> {
                    val old = nm.currentInterruptionFilter
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    old
                }
                RuleAction.STOP_AUDIO -> return
            }
            p.edit()
                .putString("act_${r.id}", r.action.name)
                .putInt("prev_${r.id}", prev)
                .apply()
        } catch (e: SecurityException) {
            // нет доступа к «Не беспокоить» — выдайте его в приложении
        }
    }

    fun end(ctx: Context, r: Rule) = endById(ctx, r.id)

    fun endById(ctx: Context, id: Int) {
        val p = state(ctx)
        val act = p.getString("act_$id", null) ?: return
        val prev = p.getInt("prev_$id", -1)

        val am = ctx.getSystemService(AudioManager::class.java)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        try {
            when (RuleAction.valueOf(act)) {
                RuleAction.SILENT, RuleAction.VIBRATE ->
                    am.ringerMode = if (prev in 0..2) prev else AudioManager.RINGER_MODE_NORMAL
                RuleAction.DND ->
                    nm.setInterruptionFilter(
                        if (prev in 1..4) prev else NotificationManager.INTERRUPTION_FILTER_ALL
                    )
                RuleAction.STOP_AUDIO -> {}
            }
        } catch (e: Exception) {
        }
        p.edit().remove("act_$id").remove("prev_$id").apply()
    }
}
