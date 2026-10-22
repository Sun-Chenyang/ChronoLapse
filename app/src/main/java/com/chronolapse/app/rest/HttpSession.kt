package com.chronolapse.app.rest

import android.annotation.SuppressLint
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.chronolapse.app.BuildConfig
import com.chronolapse.app.R
import com.chronolapse.app.data.MediaRepository
import com.chronolapse.app.data.RecStatus
import com.chronolapse.app.data.ServiceLocator
import com.chronolapse.app.service.RecordingController
import com.chronolapse.app.service.RecordingService
import kotlinx.coroutines.runBlocking
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One HTTP session on its own thread: tiny GET router serving the built in
 * web page, status / control endpoints and the captured media.
 */
@SuppressLint("SimpleDateFormat")
class HttpSession(private val socket: Socket, private val service: RestServer) :
    Thread("HttpSession") {

    private val out = BufferedOutputStream(socket.getOutputStream())

    override fun run() {
        try {
            process()
            out.flush()
        } catch (e: Exception) {
            Log.w(TAG, "session error", e)
        } finally {
            close()
            service.removeSession(this)
        }
    }

    private fun process() {
        val reader = socket.getInputStream().bufferedReader()
        val request = reader.readLine() ?: return
        val parts = request.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val url = parts[1]
        Log.d(TAG, "Request: $method $url")
        if (method != "GET") return sendText(404, "Not found")

        when {
            url == "/" -> replyRaw(R.raw.index, "text/html")
            url == "/rest" -> replyRaw(R.raw.help, "text/plain")
            url == "/favicon.ico" -> replyRaw(R.raw.favicon, "image/x-icon")
            url.startsWith("/1/device/battery") -> sendText(200, batteryLevel().toString())
            url.startsWith("/1/ctrl/") -> control(url.substringAfter("/1/ctrl/"))
            url.startsWith("/1/current/") -> current(url.substringAfter("/1/current/"))
            url.startsWith("/1/img/") -> media(url.substringAfter("/1/img/"))
            else -> sendText(404, "File not found")
        }
    }

    // ------------------------------------------------------------- endpoints

    private fun control(command: String) {
        val ctx = service
        when (command.substringBefore('?')) {
            "status" -> sendText(200, if (RecordingService.isRunning) "running" else "stopped")
            "start" -> {
                RecordingController.start(ctx)
                sendText(200, "ok")
            }
            "stop" -> {
                RecordingController.stop(ctx)
                sendText(200, "ok")
            }
            "param" -> {
                val s = runBlocking { ServiceLocator.settings.snapshot() }
                sendText(200, s.toString())
            }
            else -> sendText(404, "Not found")
        }
    }

    private fun current(command: String) {
        when (command) {
            "imgcount" -> {
                sendText(200, RecStatus.state.value.frameCount.toString())
                return
            }
            "lastimg" -> {
                val uri = MediaRepository.photoProjects(service).firstOrNull()?.cover
                if (uri == null) {
                    sendText(200, "null")
                } else {
                    sendText(200, uri.lastPathSegment ?: "unknown")
                }
                return
            }
            "img" -> {
                val uri = MediaRepository.photoProjects(service).firstOrNull()?.cover
                if (uri == null) {
                    sendText(404, "No image yet")
                } else {
                    sendUri(uri)
                }
                return
            }
            else -> sendText(404, "Not found")
        }
    }

    private fun media(path: String) {
        if (path.contains("..")) return sendText(403, "Forbidden")
        val base = Environment.DIRECTORY_PICTURES + "/" + MediaRepository.PROJECT_NAME
        val folder = path.substringBeforeLast('/').trimEnd('/')

        if (path.endsWith("/list") || path.endsWith("/listhtml")) {
            val html = path.endsWith("/listhtml")
            val target = folder.removeSuffix("/list").removeSuffix("/listhtml")
            val relPrefix = "$base/$target/"
            val entries = listMedia(relPrefix)
            if (entries.isEmpty()) return sendText(404, "Directory does not exist")
            header(200, if (html) "text/html" else "text/plain")
            if (html) sendLine("<html><head><title>ChronoLapse</title></head><body><h1>$relPrefix</h1>")
            for ((name, isDir) in entries) {
                val href = "/1/img/$target/$name" + if (isDir) "/listhtml" else ""
                if (html) sendLine("<a href=\"$href\">$name</a><br>")
                else sendLine(name + if (isDir) "/" else "")
            }
            if (html) sendLine("</body>")
            sendLine("")
            return
        }

        // file download: look up by display name under Pictures/ChronoLapse
        val name = path.substringAfterLast('/')
        val uri = findByName("$base/$folder", name)
        if (uri == null) sendText(404, "File not found")
        else sendUri(uri)
    }

    private fun listMedia(relPrefix: String): List<Pair<String, Boolean>> {
        val proj = arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val result = LinkedHashMap<String, Boolean>()
        runCatching {
            // files directly inside the folder
            service.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(relPrefix), null
            )?.use { c ->
                val nameCol = 0
                while (c.moveToNext()) result[c.getString(nameCol)] = false
            }
            // sub folders
            service.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf("$relPrefix%"),
                null
            )?.use { c ->
                val pathCol = 0
                while (c.moveToNext()) {
                    val rel = c.getString(pathCol).removePrefix(relPrefix).substringBefore('/')
                    if (rel.isNotEmpty()) result.putIfAbsent(rel, true)
                }
            }
        }
        return result.map { (k, v) -> k to v }
    }

    private fun findByName(relPath: String, name: String): Uri? {
        val proj = arrayOf(MediaStore.MediaColumns._ID)
        return runCatching {
            service.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj,
                "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf("$relPath/", name), null
            )?.use { c ->
                if (c.moveToFirst()) MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                    .appendPath(c.getLong(0).toString()).build()
                else null
            }
        }.getOrNull()
    }

    private fun batteryLevel(): Int {
        val intent = service.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 1) ?: 1
        return if (level < 0) 0 else (level * 100f / scale).toInt()
    }

    // -------------------------------------------------------------- plumbing

    private fun replyRaw(resId: Int, contentType: String) {
        header(200, contentType)
        service.resources.openRawResource(resId).use { it.copyTo(out, 8192) }
    }

    private fun sendUri(uri: Uri) {
        val stream: InputStream = service.contentResolver.openInputStream(uri)
            ?: return sendText(404, "File not found")
        header(200, "application/octet-stream")
        stream.use { it.copyTo(out, 8192) }
    }

    private fun sendText(code: Int, text: String) {
        header(code, "text/plain")
        sendLine(text)
        sendLine("")
    }

    private fun header(code: Int, contentType: String) {
        val date = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).format(Date())
        sendLine("HTTP/1.1 $code ${if (code == 200) "OK" else if (code == 403) "Forbidden" else "Not Found"}")
        sendLine("Date: $date")
        sendLine("Server: ChronoLapse/${BuildConfig.VERSION_NAME} (Android)")
        sendLine("Content-Type: $contentType")
        sendLine("")
    }

    private fun sendLine(line: String) {
        out.write((line + "\r\n").toByteArray(Charsets.ISO_8859_1))
    }

    fun close() {
        runCatching { socket.close() }
    }

    companion object {
        private const val TAG = "HttpSession"
    }
}
