package com.chronolapse.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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
            context.startService(service)
        }
    }
}
