package com.chronolapse.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Start / stop helpers for the recording service and the schedule alarm. */
object RecordingController {

    fun start(ctx: Context) {
        val intent = Intent(ctx, RecordingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(intent)
        } else {
            ctx.startService(intent)
        }
    }

    fun stop(ctx: Context) {
        val intent = Intent(ctx, RecordingService::class.java).setAction(RecordingService.ACTION_STOP)
        ctx.startService(intent)
    }

    fun armSchedule(ctx: Context, atMs: Long) {
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, Receivers.ScheduleReceiver::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pi)
    }

    fun cancelSchedule(ctx: Context) {
        val alarm = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, Receivers.ScheduleReceiver::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        alarm.cancel(pi)
    }
}
