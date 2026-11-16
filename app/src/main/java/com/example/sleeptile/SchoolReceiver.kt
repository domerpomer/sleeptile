package com.example.sleeptile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SchoolReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                SchoolScheduler.tick(app)
            } finally {
                result.finish()
            }
        }.start()
    }
}
