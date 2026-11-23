package com.example.sleeptile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager

class SchoolReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        val pm = app.getSystemService(PowerManager::class.java)
        val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sleeptile:school_tick")
        try {
            wl?.acquire(5000L)
        } catch (e: Exception) {
        }

        Thread {
            try {
                SchoolScheduler.tick(app)
            } finally {
                try {
                    if (wl?.isHeld == true) wl.release()
                } catch (e: Exception) {
                }
                result.finish()
            }
        }.start()
    }
}
