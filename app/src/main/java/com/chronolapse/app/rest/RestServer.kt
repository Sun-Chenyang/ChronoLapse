package com.chronolapse.app.rest

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chronolapse.app.MainActivity
import com.chronolapse.app.R
import com.chronolapse.app.data.ServiceLocator
import com.chronolapse.app.service.RecordingController
import com.chronolapse.app.service.RecordingService
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.util.Collections

/**
 * Minimal HTTP server for LAN remote control, published as a special use
 * foreground service. Accept loop runs directly on the server thread.
 */
class RestServer : Service(), Runnable {

    private var shouldExit = false
    private var listenSocket: ServerSocket? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val sessions = Collections.synchronizedList(ArrayList<HttpSession>())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        setupNotification()
        if (serverThread?.isAlive == true) return START_STICKY

        shouldExit = false
        serverThread = Thread(this, "RestServer").apply { start() }
        return START_STICKY
    }

    override fun run() {
        if (!isConnectedToLocalNetwork()) {
            Log.w(TAG, "No local network, bailing out")
            stopSelf()
            return
        }
        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port()))
            listenSocket = socket
        } catch (e: IOException) {
            Log.w(TAG, "Cannot open port", e)
            stopSelf()
            return
        }

        takeWakeLock()
        Log.i(TAG, "HTTP server up on port ${port()}")

        while (!shouldExit) {
            val socket = try {
                listenSocket!!.accept()
            } catch (e: Exception) {
                break
            }
            val session = HttpSession(socket, this)
            sessions.add(session)
            session.start()
        }

        synchronized(sessions) { sessions.forEach { it.close() } }
        runCatching { listenSocket?.close() }
        wakeLock?.let { if (it.isHeld) it.release() }
        stopSelf()
    }

    override fun onDestroy() {
        shouldExit = true
        runCatching { listenSocket?.close() }
        serverThread?.interrupt()
        serverThread = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // user removed the app from recents: restart in a moment
        val restart = PendingIntent.getService(
            this, 1, Intent(this, this::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        (getSystemService(ALARM_SERVICE) as AlarmManager).set(
            AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 2000, restart
        )
    }

    fun removeSession(session: HttpSession) {
        sessions.remove(session)
    }

    private fun port(): Int =
        runBlocking { ServiceLocator.settings.snapshot() }.restPort
            .coerceIn(1024, 65535).let { if (it == 0) 8085 else it }

    private fun takeWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ChronoLapse:rest")
        }
        wakeLock?.acquire()
    }

    private fun isConnectedToLocalNetwork(): Boolean {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        if (caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        ) return true

        // hotspot or usb tethering
        runCatching {
            for (nif in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (nif.displayName.startsWith("rndis") || nif.displayName.startsWith("ap")) return true
                if (nif.isUp && nif.displayName.contains("wlan") && nif.inetAddresses.hasMoreElements()) {
                    for (addr in Collections.list(nif.inetAddresses)) {
                        if (addr is Inet4Address && !addr.isLoopbackAddress) return true
                    }
                }
            }
        }
        return false
    }

    private fun setupNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "REST API", NotificationManager.IMPORTANCE_LOW)
        )
        val notification = notificationBuilder(getString(R.string.notif_restapi)).build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notificationBuilder(text: String): NotificationCompat.Builder {
        val pi = PendingIntent.getActivity(
            this, NOTIF_ID,
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .setSmallIcon(R.drawable.ic_camera)
            .setOngoing(true)
            .setContentText(text)
            .setContentTitle(getString(R.string.app_name))
    }

    companion object {
        private const val TAG = "RestServer"
        private const val CHANNEL_ID = "rest"
        private const val NOTIF_ID = 1002

        @Volatile
        private var serverThread: Thread? = null

        fun isRunning(): Boolean = serverThread?.isAlive == true

        /** first local IPv4 on wlan / eth, for the connection info row */
        fun localIp(): String? = runCatching {
            for (nif in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.name.matches(Regex("^(eth|wlan).*"))) continue
                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        return addr.hostAddress
                    }
                }
            }
            null
        }.getOrNull()

        fun start(ctx: Context) {
            val intent = Intent(ctx, RestServer::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(intent)
            else ctx.startService(intent)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, RestServer::class.java))
        }
    }
}
