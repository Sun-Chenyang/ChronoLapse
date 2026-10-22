package com.chronolapse.app.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.text.format.DateFormat
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Access to the app's published media in the gallery. Photo frames live in
 * Pictures/ChronoLapse/<date>/ named "ChronoLapse_<session>_<index>.jpg";
 * the session token groups the frames of one recording session into a
 * project. Time lapse videos are single files in Movies/ChronoLapse/<date>/.
 */
object MediaRepository {

    const val PROJECT_NAME = "ChronoLapse"

    data class PhotoProject(
        val dir: String,
        val session: String,
        val title: String,
        val count: Int,
        /** latest frame time, seconds since epoch */
        val dateAddedSec: Long,
        val cover: Uri?,
        /** frames in capture order (oldest first) */
        val frames: List<Uri>,
    )

    data class VideoItem(
        val uri: Uri,
        val name: String,
        val durationMs: Long,
        val dateAddedSec: Long,
    )

    data class Thumb(val uri: Uri?, val video: Boolean)

    fun photoProjects(ctx: Context): List<PhotoProject> {
        class Build(val dir: String, val session: String) {
            val framesByIdx = sortedMapOf<Int, Uri>()
            var count = 0
            var dateAdded = 0L
            var cover: Uri? = null

            fun add(index: Int, uri: Uri, added: Long) {
                framesByIdx[index] = uri
                count++
                if (added > dateAdded) {
                    dateAdded = added
                    cover = uri
                }
            }

            fun finish(): PhotoProject {
                val frames = framesByIdx.values.toList()
                return PhotoProject(
                    dir = dir,
                    session = session,
                    title = "",
                    count = count,
                    dateAddedSec = dateAdded,
                    cover = cover ?: frames.lastOrNull(),
                    frames = frames,
                )
            }
        }

        val projects = linkedMapOf<String, Build>()
        val prefix = "$PROJECT_NAME"

        query(ctx, images = true)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext()) {
                val name = c.getString(nameCol) ?: continue
                val path = c.getString(pathCol) ?: continue
                if (!name.startsWith("${prefix}_") || !name.endsWith(".jpg")) continue

                val mid = name.substring(prefix.length + 1, name.length - 4)
                val cut = mid.lastIndexOf('_')
                val session = if (cut > 0) mid.substring(0, cut) else mid
                val index = if (cut > 0) mid.substring(cut + 1).toIntOrNull() ?: 0 else 0

                val dir = File(path).parent ?: continue
                val key = "$dir|$session"
                val b = projects.getOrPut(key) { Build(dir, session) }
                b.add(index, ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)), c.getLong(addedCol))
            }
        }

        val list = projects.values.map { it.finish() }

        // title = date directory; number the sessions within one day
        val perDay = HashMap<String, Int>()
        for (p in list) perDay.merge(day(p), 1, Int::plus)
        val seen = HashMap<String, Int>()
        val titled = list.map { p ->
            val day = day(p)
            val total = perDay[day] ?: 1
            val n = if (total > 1) seen.merge(day, 1, Int::plus) else 0
            p.copy(title = if (total > 1) "$day #$n" else day)
        }

        return titled.sortedByDescending { it.dateAddedSec }
    }

    private fun day(p: PhotoProject): String = File(p.dir).name

    fun projectFrames(ctx: Context, project: PhotoProject): List<Uri> =
        photoProjects(ctx).firstOrNull { it.dir == project.dir && it.session == project.session }?.frames
            ?: emptyList()

    fun videos(ctx: Context): List<VideoItem> {
        val out = ArrayList<VideoItem>()
        query(ctx, images = false, withDuration = true)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            while (c.moveToNext()) {
                var duration = 0L
                val dCol = c.getColumnIndex(MediaStore.MediaColumns.DURATION)
                if (dCol >= 0 && !c.isNull(dCol)) duration = c.getLong(dCol)
                out += VideoItem(
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, c.getLong(idCol)),
                    name = c.getString(nameCol) ?: "",
                    durationMs = duration,
                    dateAddedSec = c.getLong(addedCol),
                )
            }
        }
        return out
    }

    private fun query(ctx: Context, images: Boolean, withDuration: Boolean = false): android.database.Cursor? {
        val collection =
            if (images) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = mutableListOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        if (!images && withDuration && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            proj += MediaStore.MediaColumns.DURATION
        }
        val (selection, args) =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val rel = if (images) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf("$rel/$PROJECT_NAME/%")
            } else {
                val base = Environment.getExternalStoragePublicDirectory(
                    if (images) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
                ).path
                "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("$base/$PROJECT_NAME/%")
            }
        val order = "${MediaStore.MediaColumns.DATE_ADDED} DESC"
        return ctx.contentResolver.query(collection, proj.toTypedArray(), selection, args, order)
    }

    fun latestThumb(ctx: Context): Thumb {
        photoProjects(ctx).firstOrNull()?.let { return Thumb(it.cover, video = false) }
        videos(ctx).firstOrNull()?.let { return Thumb(it.uri, video = true) }
        return Thumb(null, video = false)
    }

    suspend fun loadThumbnail(ctx: Context, uri: Uri, video: Boolean, size: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                ctx.contentResolver.loadThumbnail(uri, Size(size, size), null)
            } catch (e: Exception) {
                null
            }
        }

    /** delete media items, returns how many were removed; one retry covers
     *  transient MediaProvider states right after a capture */
    fun delete(ctx: Context, uris: List<Uri>): Int {
        val (first, ok) = deleteOnce(ctx, uris)
        val failed = uris.zip(ok).filter { !it.second }.map { it.first }
        if (failed.isEmpty()) return first
        SystemClock.sleep(200)
        return first + deleteOnce(ctx, failed).first
    }

    private fun deleteOnce(ctx: Context, uris: List<Uri>): Pair<Int, List<Boolean>> {
        var n = 0
        val ok = uris.map { uri ->
            try {
                val deleted = ctx.contentResolver.delete(uri, null, null) > 0
                if (deleted) n++
                deleted
            } catch (e: Exception) {
                android.util.Log.w("MediaRepository", "delete $uri failed", e)
                false
            }
        }
        return n to ok
    }

    fun copyToCache(ctx: Context, uri: Uri, dir: File, name: String): File {
        dir.mkdirs()
        val out = File(dir, name)
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output, 65536) }
        } ?: throw IllegalStateException("Cannot open $uri")
        return out
    }

    /** publish a finished MP4 into Movies/ChronoLapse/<date>/ */
    fun publishVideo(ctx: Context, temp: File, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val dateDir = DateFormat.format("yyyy-MM-dd", System.currentTimeMillis()).toString()
            val cv = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/" + PROJECT_NAME + "/" + dateDir)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), cv
            ) ?: throw IllegalStateException("MediaStore insert failed")

            try {
                FileInputStream(temp).use { input ->
                    ctx.contentResolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output, 65536)
                    } ?: throw IllegalStateException("Cannot open output")
                }
                cv.clear()
                cv.put(MediaStore.Video.Media.IS_PENDING, 0)
                ctx.contentResolver.update(uri, cv, null, null)
            } catch (e: Exception) {
                runCatching { ctx.contentResolver.delete(uri, null, null) }
                throw e
            }
            return
        }

        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), PROJECT_NAME)
        if (!dir.exists() && !dir.mkdirs()) throw IllegalStateException("Cannot create $dir")
        val dest = File(dir, name)
        FileInputStream(temp).use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output, 65536) }
        }
        temp.delete()
        MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null)
    }
}
