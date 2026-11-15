package com.example.sleeptile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val planned = SleepTimer.plannedEnd(context)
        SleepTimer.onFired(context)
        val result = goAsync()
        AudioStopper.stop(context, "Таймер сна", planned) { result.finish() }
    }
}
