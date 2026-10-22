package com.chronolapse.app.sensor

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Mutes the audible streams around each capture to suppress the shutter
 * sound; requires notification policy access and restores the previous
 * state shortly after the last capture.
 */
@Suppress("DEPRECATION")
class MuteShutter(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val handler = Handler(Looper.getMainLooper())

    private var mode = 0
    private var ringerMode = 0
    private val streamVolumes = IntArray(STREAMS.size)

    @Volatile
    private var muted = false

    private fun storeSoundSettings() {
        mode = audioManager.mode
        ringerMode = audioManager.ringerMode
        for (i in STREAMS.indices) streamVolumes[i] = audioManager.getStreamVolume(STREAMS[i])
    }

    private fun recoverSoundSettings() {
        audioManager.mode = mode
        audioManager.ringerMode = ringerMode
        for (i in STREAMS.indices) {
            audioManager.setStreamVolume(
                STREAMS[i], streamVolumes[i],
                AudioManager.FLAG_ALLOW_RINGER_MODES
            )
        }
    }

    fun unmuteShutter() {
        synchronized(this) {
            if (!muted) return
            for (stream in STREAMS) {
                audioManager.setStreamMute(stream, false)
                audioManager.setStreamVolume(
                    stream, audioManager.getStreamMaxVolume(stream),
                    AudioManager.FLAG_ALLOW_RINGER_MODES
                )
            }
            recoverSoundSettings()
            muted = false
        }
    }

    fun muteShutter() {
        synchronized(this) {
            if (muted) {
                scheduleRecovery()
                return
            }
            storeSoundSettings()
            if (notificationManager.isNotificationPolicyAccessGranted) {
                for (stream in STREAMS) {
                    audioManager.setStreamVolume(
                        stream, 0,
                        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                    )
                    audioManager.setStreamMute(stream, true)
                }
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
            muted = true
            scheduleRecovery()
        }
    }

    private fun scheduleRecovery() {
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ unmuteShutter() }, 2000)
    }

    companion object {
        private val STREAMS = intArrayOf(
            AudioManager.STREAM_DTMF,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
        )
    }
}
