package com.chronolapse.app.recorder

import android.content.Context
import android.os.Handler
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.lifecycle.LifecycleOwner
import com.chronolapse.app.data.AppSettings
import com.chronolapse.app.data.MediaRepository
import java.io.File

/**
 * Time lapse video: frames are captured into app specific storage (keeping
 * the gallery clean) and encoded into a single MP4 when the session ends.
 * The finished video is published to Movies/ChronoLapse/.
 */
class VideoTimeLapseRecorder(
    settings: AppSettings,
    context: Context,
    owner: LifecycleOwner,
    handler: Handler,
) : ImageRecorder(settings, context, owner, handler) {

    private val frames = ArrayList<File>()
    private var assembling = false
    private var sessionDir: File? = null

    override fun buildOutputOptions(): ImageCapture.OutputFileOptions {
        val dir = sessionDir ?: File(File(context.getExternalFilesDir(null), "sessions"),
            java.lang.Long.toHexString(sessionId)).apply { mkdirs(); sessionDir = this }
        val f = File(dir, "${MediaRepository.PROJECT_NAME}_${frames.size}.jpg")
        return ImageCapture.OutputFileOptions.Builder(f).build()
    }

    override fun onFrameSaved(file: File?) {
        if (file != null) frames.add(file)
    }

    override fun stop() {
        handler.removeCallbacks(this)
        if (!assembling && frames.isNotEmpty()) {
            // user stopped the session: encode in the background, there is no
            // service notification afterwards
            assembling = true
            assemble(null)
        }
        super.stop()
    }

    override fun success() {
        // natural end: encode first, then report success so the service stays
        // alive while post processing runs
        if (assembling) return
        if (frames.isEmpty()) {
            notifySuccess()
            return
        }
        assembling = true
        handler.removeCallbacks(this)
        assemble { notifySuccess() }
    }

    private fun assemble(done: Runnable?) {
        val ctx = context
        val snapshot = ArrayList(frames)
        val dir = sessionDir
        val temp = File(dir, "output.mp4")
        val name = "${MediaRepository.PROJECT_NAME}_%x.mp4".format(sessionId)
        val fps = if (settings.playbackFps > 0) settings.playbackFps else 30
        val width = settings.frameWidth
        val height = settings.frameHeight
        val handlerRef = handler
        val tag = this::class.java.simpleName

        Thread {
            try {
                JpegSequenceAssembler().assemble(snapshot, temp, fps, width, height)
                MediaRepository.publishVideo(ctx, temp, name)
                dir?.let { deleteRecursive(it) }
                done?.let { r -> handlerRef.post(r) }
            } catch (e: Exception) {
                Log.e(tag, "assemble failed", e)
                handleError(tag, e.message ?: "encode failed")
            }
        }.start()
    }

    private fun deleteRecursive(dir: File) {
        dir.listFiles()?.forEach { if (it.isDirectory) deleteRecursive(it) else it.delete() }
        dir.delete()
    }
}
