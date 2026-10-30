package com.chronolapse.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/** System alarms land here; everything is forwarded to the service. */
class Receivers {

    /** fires at the user scheduled capture time */
    class ScheduleReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            RecordingController.start(context)
        }
    }

    /** fires for every shot in power saving mode */
    class PowerSavingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val service = Intent(context, RecordingService::class.java)
                .setAction(RecordingService.ACTION_CAPTURE)
            try {
                // normal tick: the foreground service of the running session
                // is still up, a plain start to it is allowed in the background
                context.startService(service)
            } catch (e: IllegalStateException) {
                // the process had been killed in the idle window between two
                // frames: the alarm clock temporary allowlist permits to start
                // the service as a foreground service from here
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(service)
                    } else {
                        context.startService(service)
                    }
                } catch (e2: Exception) {
                    Log.e("PowerSavingReceiver", "could not wake capture service", e2)
                }
            }
        }
    }
}
