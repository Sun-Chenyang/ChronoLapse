package com.chronolapse.app.recorder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.SystemClock
import androidx.lifecycle.LifecycleOwner
import com.chronolapse.app.data.AppSettings
import com.chronolapse.app.service.Receivers

/**
 * Interval capture driven by system alarm clock wakeups: the camera is
 * released between shots while the session wake lock stays held, so a long
 * unattended session survives aggressive battery saving. One recorder
 * instance lives for the whole session, so all frames stay inside a single
 * project.
 */
class PowerSavingImageRecorder(
    settings: AppSettings,
    context: Context,
    owner: LifecycleOwner,
    handler: Handler,
    private val powerControl: PowerControl,
) : ImageRecorder(settings, context, owner, handler) {

    interface PowerControl {
        fun acquireWake()
        fun releaseWake()
    }

    private val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun stop() {
        cancelAlarm()
        super.stop()
    }

    override fun scheduleNextPicture() {
        val alarmIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, Receivers.PowerSavingReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val delay = (settings.intervalMs - (SystemClock.elapsedRealtime() - startPreviewTime))
            .coerceAtLeast(0)
        alarmMgr.setAlarmClock(
            AlarmManager.AlarmClockInfo(System.currentTimeMillis() + delay, null),
            alarmIntent
        )

        // free the camera between shots, but keep the wake lock of the
        // foreground service held: with the CPU sleeping and no wake lock the
        // process is an easy kill for aggressive battery saving, and the next
        // alarm tick could not revive the session any more
        releaseCamera()
        // the camera was unbound: the alarm tick has to run prepareRecord
        // again before it can take the next picture
        prepared = false
    }

    private fun cancelAlarm() {
        val alarmIntent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, Receivers.PowerSavingReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr.cancel(alarmIntent)
        powerControl.releaseWake()
    }
}
