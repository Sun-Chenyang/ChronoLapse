package com.chronolapse.app.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.chronolapse.app.R
import com.chronolapse.app.data.AppSettings
import com.chronolapse.app.data.RecStatus
import com.chronolapse.app.data.ServiceLocator
import com.chronolapse.app.recorder.PowerSavingImageRecorder
import com.chronolapse.app.recorder.Recorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Foreground service owning one recording session: camera access, the wake
 * lock, schedule waiting and the power saving alarm ticks.
 */
class RecordingService : LifecycleService() {

    companion object {
        const val ACTION_STOP = "com.chronolapse.app.action.STOP"
        const val ACTION_CAPTURE = "com.chronolapse.app.action.CAPTURE"
        const val CHANNEL_ID = "recording"
        const val NOTIF_ID = 123

        @Volatile
        var isRunning = false
            private set
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recorder: Recorder? = null
    private var handlerThread: android.os.HandlerThread? = null
    private var handler: Handler? = null
    private var settings: AppSettings? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var waitingForSchedule = false

    /** set once the user stopped this service instance: late ticks of the
     * stopped session must not silently restart a capture */
    private var stoppedOnce = false

    /** stops the session on low battery / low storage while it runs */
    private val deviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val s = settings ?: return
            val shouldStop = (s.stopOnLowBattery && intent.action == Intent.ACTION_BATTERY_LOW) ||
                (s.stopOnLowStorage && intent.action == Intent.ACTION_DEVICE_STORAGE_LOW)
            if (shouldStop) mainHandler.post { stopRecording() }
        }
    }

    private val powerControl = object : PowerSavingImageRecorder.PowerControl {
        override fun acquireWake() = this@RecordingService.acquireWake()
        override fun releaseWake() = this@RecordingService.releaseWake()
    }

    override fun onCreate() {
        super.onCreate()
        // flash toggles apply to a running session immediately
        lifecycleScope.launch {
            ServiceLocator.settings.flow
                .map { it.flash }
                .distinctUntilChanged()
                .drop(1)
                .collect { on ->
                    (recorder as? com.chronolapse.app.recorder.ImageRecorder)?.setFlashNow(on)
                }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP -> {
                RecordingController.cancelSchedule(this)
                stopRecording()
                return START_NOT_STICKY
            }
            ACTION_CAPTURE -> {
                // power saving tick: wake up, grab the single frame, sleep again
                val rec = recorder as? com.chronolapse.app.recorder.ImageRecorder
                when {
                    rec != null -> {
                        acquireWake()
                        rec.captureNow()
                    }
                    stoppedOnce -> {
                        // stale tick of a session the user just stopped
                    }
                    else -> {
                        // the process had been killed between two frames and
                        // the alarm cold started us: recover the session (and
                        // satisfy the startForeground obligation of a
                        // startForegroundService wake-up)
                        initNotification()
                        startRecording()
                    }
                }
                return START_STICKY
            }
            else -> {
                initNotification()
                startRecording()
            }
        }
        return START_STICKY
    }

    private fun startRecording() {
        if (isRunning && recorder != null) return

        val s = runBlocking { ServiceLocator.settings.snapshot() }
        settings = s

        if (recorder == null && s.schedulePending) {
            // armed but waiting: keep the foreground service up, no camera yet
            RecordingController.armSchedule(this, s.scheduleTimeMs)
            waitingForSchedule = true
            RecStatus.waiting()
            isRunning = true
            updateNotification(getString(R.string.notif_waiting, formatTime(s.scheduleTimeMs)))
            return
        }

        waitingForSchedule = false
        RecordingController.cancelSchedule(this)
        beginCapture(s)
    }

    private fun beginCapture(s: AppSettings) {
        cancelStalePowerSavingAlarm()
        acquireWake()
        RecStatus.beginCapture(
            captureRateMs = s.intervalMs,
            playbackFps = s.playbackFps,
            frameWidth = s.frameWidth,
            frameHeight = s.frameHeight,
            videoMode = s.recMode == com.chronolapse.app.data.RecMode.VIDEO_LAPSE,
            watermark = s.watermark,
        )

        handlerThread = android.os.HandlerThread("recording").apply { start() }
        handler = Handler(handlerThread!!.looper)

        val rec = Recorder.create(s, this, this, handler!!, powerControl)
        rec.listener = object : Recorder.Listener {
            override fun onRecorderError(tag: String, msg: String) {
                mainHandler.post {
                    Toast.makeText(
                        this@RecordingService,
                        getString(R.string.recording_error, msg),
                        Toast.LENGTH_LONG
                    ).show()
                    stopRecording()
                }
            }

            override fun onRecorderSuccess() {
                mainHandler.post { stopRecording() }
            }
        }
        recorder = rec
        rec.start()

        isRunning = true
        registerDeviceStatus()
        updateNotification(getString(R.string.notif_recording))
    }

    private fun stopRecording() {
        unregisterDeviceStatus()
        recorder?.stop()
        recorder = null
        stoppedOnce = true
        releaseWake()
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        waitingForSchedule = false
        RecStatus.stop()
        isRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerDeviceStatus() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
        }
        registerReceiver(deviceStatusReceiver, filter)
    }

    private fun unregisterDeviceStatus() {
        runCatching { unregisterReceiver(deviceStatusReceiver) }
    }

    private fun acquireWake() {
        if (wakeLock?.isHeld != true) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChronoLapse:recording")
            wakeLock?.acquire()
        }
    }

    /**
     * A previous session killed together with its process may have left a
     * power saving alarm armed: it would cold start ghost sessions next to
     * the new one
     */
    private fun cancelStalePowerSavingAlarm() {
        val pi = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, Receivers.PowerSavingReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pi != null) {
            (getSystemService(ALARM_SERVICE) as AlarmManager).cancel(pi)
        }
    }

    private fun releaseWake() {
        val wl = wakeLock
        if (wl != null && wl.isHeld) wl.release()
    }

    override fun onDestroy() {
        unregisterDeviceStatus()
        super.onDestroy()
    }

    private fun formatTime(ms: Long): String =
        android.text.format.DateFormat.format("MM-dd HH:mm", ms).toString()

    // ------------------------------------------------------------ notification

    private fun initNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = buildNotification(getString(R.string.notif_preparing))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, NOTIF_ID,
            Intent(this, com.chronolapse.app.MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setSmallIcon(R.drawable.ic_camera)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentText(text)
            .setContentTitle(getString(R.string.app_name))
            .build()
    }
}
