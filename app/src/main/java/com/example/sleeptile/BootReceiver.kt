package com.example.sleeptile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** После перезагрузки, обновления приложения или смены времени восстанавливаем расписание. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        RuleScheduler.syncAll(context)
    }
}
