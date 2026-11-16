package com.example.sleeptile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Нажатие «Поставить» в уведомлении с предложением обоев. */
class WallpaperReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                SchoolWallpaper.applyNow(app)
                SchoolWallpaper.cancelOffer(app)
            } finally {
                result.finish()
            }
        }.start()
    }
}
