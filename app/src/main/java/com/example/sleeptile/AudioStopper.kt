package com.example.sleeptile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Останавливает любое аудио двумя способами сразу:
 * 1) шлёт системе кнопку «Пауза» (её получает плеер с активной медиасессией);
 * 2) забирает аудиофокус — послушные приложения сами встают на паузу.
 * Пока это происходит, держит телефон «не спящим» (на несколько секунд), и пишет результат в журнал.
 */
object AudioStopper {

    fun stop(ctx: Context, source: String, plannedAt: Long = 0L, onDone: () -> Unit = {}) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sleeptile:stop")
        try {
            wakeLock.acquire(10_000L)
        } catch (e: Exception) {
        }

        val am = ctx.getSystemService(AudioManager::class.java)
        sendPause(am)
        val (focus, result) = requestFocus(am)

        val planned = if (plannedAt != 0L) {
            "план " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(plannedAt)) + ", "
        } else {
            ""
        }
        val focusText = when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "фокус получен"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "фокус отложен"
            else -> "фокус не получен"
        }
        EventLog.add(ctx, "$source: $planned${EventLog.screenState(ctx)}, $focusText")

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            sendPause(am)
            handler.postDelayed({
                sendPause(am)
                if (focus != null) {
                    try {
                        am.abandonAudioFocusRequest(focus)
                    } catch (e: Exception) {
                    }
                }
                try {
                    if (wakeLock.isHeld) wakeLock.release()
                } catch (e: Exception) {
                }
                onDone()
            }, 2000)
        }, 1000)
    }

    private fun sendPause(am: AudioManager) {
        try {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (e: Exception) {
        }
    }

    private fun requestFocus(am: AudioManager): Pair<AudioFocusRequest?, Int> {
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener { })
                .build()
            Pair(req, am.requestAudioFocus(req))
        } catch (e: Exception) {
            Pair(null, AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        }
    }
}
