package com.example.sleeptile

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Останавливает любое аудио двумя способами сразу:
 * 1) шлёт системе кнопку «Пауза» (её получает плеер с активной медиасессией);
 * 2) забирает аудиофокус — послушные приложения (видео, подкасты, браузер) сами встают на паузу.
 * Никаких разрешений не нужно.
 */
object AudioStopper {

    fun stop(ctx: Context, onDone: () -> Unit = {}) {
        val am = ctx.getSystemService(AudioManager::class.java)
        sendPause(am)
        val focus = requestFocus(am)

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            // повтор: «Пауза» не переключает, а только ставит на паузу, так что это безопасно
            sendPause(am)
            handler.postDelayed({
                if (focus != null) {
                    try {
                        am.abandonAudioFocusRequest(focus)
                    } catch (e: Exception) {
                    }
                }
                onDone()
            }, 1000)
        }, 1500)
    }

    private fun sendPause(am: AudioManager) {
        try {
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        } catch (e: Exception) {
        }
    }

    private fun requestFocus(am: AudioManager): AudioFocusRequest? {
        return try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener { })
                .build()
            am.requestAudioFocus(req)
            req
        } catch (e: Exception) {
            null
        }
    }
}
