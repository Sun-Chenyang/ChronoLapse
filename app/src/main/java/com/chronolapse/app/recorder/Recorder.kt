package com.chronolapse.app.recorder

import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.SystemClock
import android.text.format.DateFormat
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.ExposureState
import androidx.lifecycle.LifecycleOwner
import com.chronolapse.app.data.AppSettings
import com.chronolapse.app.data.MediaRepository
import com.chronolapse.app.sensor.MuteShutter
import com.chronolapse.app.sensor.OrientationSensor
import java.io.File

/**
 * Base of the recording pipeline. Owns the session id, the output directory,
 * the orientation sensor and the mute shutter helpers; subclasses implement
 * the actual capture.
 *
 * @param owner lifecycle of the recording service; all camera bindings are
 *   registered under it so they can be released selectively
 */
abstract class Recorder(
    protected val settings: AppSettings,
    protected val context: Context,
    protected val owner: LifecycleOwner,
    protected val handler: Handler,
) {

    interface Listener {
        fun onRecorderError(tag: String, msg: String)
        fun onRecorderSuccess()
    }

    var listener: Listener? = null

    /** unique per session, used for collision free media names */
    protected val sessionId = System.currentTimeMillis()

    @Volatile
    protected var stopped = false

    private var orientation: OrientationSensor? = OrientationSensor(context)
    private var mute: MuteShutter? = MuteShutter(context)

    private var fileIndex = 0

    /** used for pre Android 10 output and the media scan broadcast */
    val outputDir: File = File(
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            MediaRepository.PROJECT_NAME
        ),
        DateFormat.format("yyyy-MM-dd", sessionId).toString()
    )

    init {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            Log.e(TAG, "Failed to make directory: $outputDir")
        }
    }

    fun start() {
        orientation?.enable()
        val t0 = SystemClock.elapsedRealtime()
        handler.post {
            try {
                prepareRecord()
            } catch (e: Exception) {
                Log.e(TAG, "prepareRecord failed", e)
                handleError(this::class.java.simpleName, e.message ?: "prepare failed")
                return@post
            }
            val delay = settings.initialDelayMs - (SystemClock.elapsedRealtime() - t0)
            if (delay <= 0) handler.post { runRecord() }
            else handler.postDelayed({ runRecord() }, delay)
        }
    }

    open fun stop() {
        stopped = true
        orientation?.disable()
        orientation = null
        releaseCamera()
        unmuteShutter()
    }

    /** release everything this recorder bound to the camera */
    protected open fun releaseCamera() {
    }

    protected open fun success() {
        notifySuccess()
    }

    /** subclasses may delay this, e.g. video assembly after the last frame */
    protected open fun notifySuccess() {
        listener?.onRecorderSuccess()
    }

    protected fun handleError(tag: String, msg: String) {
        if (stopped) {
            Log.e(tag, "Error after stop (ignored): $msg")
            return
        }
        Log.e(tag, "Error: $msg")
        listener?.onRecorderError(tag, msg)
    }

    protected fun nextFileIndex(): Int = fileIndex++

    protected fun muteShutterNow() {
        if (settings.muteShutter) mute?.muteShutter()
    }

    protected fun unmuteShutter() {
        if (settings.muteShutter) mute?.unmuteShutter()
    }

    protected fun dateSubDir(): String = DateFormat.format("yyyy-MM-dd", sessionId).toString()

    protected fun toSurfaceRotation(degrees: Int): Int = when ((degrees % 360 + 360) % 360) {
        90 -> android.view.Surface.ROTATION_90
        180 -> android.view.Surface.ROTATION_180
        270 -> android.view.Surface.ROTATION_270
        else -> android.view.Surface.ROTATION_0
    }

    protected fun cameraRotationDegrees(camId: Int): Int =
        orientation?.getCameraRotation(camId) ?: 0

    protected fun handlerExecutor(): java.util.concurrent.Executor =
        java.util.concurrent.Executor { r -> handler.post(r) }

    protected fun applyCameraControls(camera: Camera?) {
        camera ?: return
        val control: CameraControl = camera.cameraControl
        val es: ExposureState = camera.cameraInfo.exposureState
        if (es.isExposureCompensationSupported) {
            val index = settings.exposureComp.coerceIn(
                es.exposureCompensationRange.lower,
                es.exposureCompensationRange.upper
            )
            control.setExposureCompensationIndex(index)
        }
        if (settings.zoom > 0) control.setLinearZoom(minOf(1f, settings.zoom / 100f))
    }

    protected abstract fun prepareRecord()

    protected abstract fun doRecord()

    private fun runRecord() {
        try {
            doRecord()
        } catch (e: Exception) {
            Log.e(TAG, "doRecord failed", e)
            handleError(TAG, e.message ?: "capture failed")
        }
    }

    companion object {
        private const val TAG = "Recorder"

        fun create(
            settings: AppSettings,
            context: Context,
            owner: LifecycleOwner,
            handler: Handler,
            powerControl: PowerSavingImageRecorder.PowerControl,
        ): Recorder = when (settings.recMode) {
            com.chronolapse.app.data.RecMode.PHOTO_LAPSE ->
                if (settings.powerSaving) {
                    PowerSavingImageRecorder(settings, context, owner, handler, powerControl)
                } else {
                    ImageRecorder(settings, context, owner, handler)
                }
            com.chronolapse.app.data.RecMode.VIDEO_LAPSE ->
                VideoTimeLapseRecorder(settings, context, owner, handler)
        }
    }
}
