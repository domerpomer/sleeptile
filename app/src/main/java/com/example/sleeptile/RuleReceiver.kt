package com.example.sleeptile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RuleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(RuleScheduler.EXTRA_ID, -1)
        val kind = intent.getStringExtra(RuleScheduler.EXTRA_KIND)
        val rule = RuleStore.load(context).firstOrNull { it.id == id }
        if (rule == null || !rule.enabled || kind == null) return

        // Сразу ставим следующие срабатывания (на завтра и далее)
        RuleScheduler.scheduleAlarms(context, rule)

        if (kind == RuleScheduler.KIND_START) {
            if (rule.action == RuleAction.STOP_AUDIO) {
                val result = goAsync()
                AudioStopper.stop(context) { result.finish() }
            } else {
                RuleExecutor.start(context, rule)
            }
        } else {
            RuleExecutor.end(context, rule)
        }
    }
}
