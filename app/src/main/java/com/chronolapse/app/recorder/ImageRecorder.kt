package com.chronolapse.app.recorder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import com.chronolapse.app.data.AppSettings
import com.chronolapse.app.data.MediaRepository
import com.chronolapse.app.data.RecStatus
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Time lapse photo capture on the CameraX ImageCapture use case. On Android
 * 10+ frames are written straight to the gallery through MediaStore; older
 * versions use direct file paths. Optionally keeps a preview use case bound
 * so the on-screen viewfinder stays live while capturing.
 */
open class ImageRecorder(
    settings: AppSettings,
    context: Context,
    owner: LifecycleOwner,
    handler: Handler,
) : Recorder(settings, context, owner, handler), Runnable {

    private val endTime: Long =
        if (settings.stopAfterMs > 0) System.currentTimeMillis() + settings.initialDelayMs + settings.stopAfterMs
        else 0L

    private var prepared = false
    protected var startPreviewTime = 0L
    private var captureWallTime = 0L

    protected var imageCapture: ImageCapture? = null
    protected var camera: Camera? = null

    override fun stop() {
        handler.removeCallbacks(this)
        muteShutterNow()
        super.stop()
    }

    /** external trigger, e.g. the power saving alarm tick */
    fun captureNow() {
        handler.post { run() }
    }

    /** apply a flash toggle to the already bound capture use case */
    fun setFlashNow(on: Boolean) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            imageCapture?.flashMode =
                if (on) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        }
    }

    protected open fun scheduleNextPicture() {
        val diffTime = SystemClock.elapsedRealtime() - startPreviewTime
        var delay = settings.intervalMs - diffTime
        // no live preview while capturing: free the camera between shots
        if (delay >= RELEASE_CAMERA_THRESHOLD && settings.intervalMs >= CONTINUOUS_CAPTURE_THRESHOLD) {
            releaseCamera()
            prepared = false
        }
        if (delay <= 0) handler.post(this)
        else handler.postDelayed(this, delay)
    }

    protected open fun buildOutputOptions(): ImageCapture.OutputFileOptions {
        val name = "${MediaRepository.PROJECT_NAME}_%x_%d.jpg".format(sessionId, nextFileIndex())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/" + MediaRepository.PROJECT_NAME + "/" + dateSubDir())
            }
            return ImageCapture.OutputFileOptions.Builder(
                context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv
            ).build()
        }
        val f = File(outputDir, name)
        return ImageCapture.OutputFileOptions.Builder(f).build()
    }

    override fun run() {
        if (endTime > 0 && endTime < System.currentTimeMillis()) {
            success()
            return
        }

        startPreviewTime = SystemClock.elapsedRealtime()
        try {
            if (!prepared) prepareRecord()
            muteShutterNow()
            captureWallTime = System.currentTimeMillis()
            imageCapture!!.takePicture(buildOutputOptions(), handlerExecutor(), callback)
        } catch (e: Exception) {
            Log.e(TAG, "capture failed", e)
            handleError(TAG, e.message ?: "capture failed")
        }
    }

    private val callback = object : ImageCapture.OnImageSavedCallback {
        override fun onImageSaved(results: ImageCapture.OutputFileResults) {
            if (stopped) return
            RecStatus.onFrameCaptured()

            val uri = results.savedUri
            var file: File? = null
            if (uri != null && "file" == uri.scheme) file = File(uri.path!!)
            currentRecordedImage = file

            if (settings.watermark) {
                try {
                    applyWatermark(uri, file, captureWallTime)
                } catch (e: Exception) {
                    Log.e("Watermark", "stamp failed", e)
                }
            }
            onFrameSaved(file)
            scheduleNextPicture()
        }

        override fun onError(exception: ImageCaptureException) {
            handleError(TAG, exception.message ?: "capture error")
        }
    }

    /** hook for subclasses, e.g. collecting session frames for assembly */
    protected open fun onFrameSaved(file: File?) {
    }

    /** stamp the current time onto the just saved image and write it back */
    private fun applyWatermark(uri: Uri?, file: File?, whenMs: Long) {
        val opts = BitmapFactory.Options()
        val bmp: Bitmap = if (uri != null) {
            val input: InputStream = context.contentResolver.openInputStream(uri)!!
            val decoded = input.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: throw Exception("Watermark: could not decode saved image")
            decoded
        } else {
            BitmapFactory.decodeFile(file!!.absolutePath, opts)
                ?: throw Exception("Watermark: could not decode saved image")
        }

        try {
            val degrees = try {
                val exif = if (uri != null) {
                    context.contentResolver.openInputStream(uri)!!.use {
                        ExifInterface(it)
                    }
                } else {
                    ExifInterface(file!!.absolutePath)
                }
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } catch (_: Exception) {
                0
            }

            var rotated = bmp
            if (degrees != 0) {
                val m = android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }
                val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (r !== bmp) bmp.recycle()
                rotated = r
            }

            // decoded bitmaps are immutable
            val mutable = rotated.copy(Bitmap.Config.ARGB_8888, true)
            if (mutable !== rotated) rotated.recycle()
            Watermark.draw(Canvas(mutable), whenMs, 0)

            val out: OutputStream = if (uri != null) {
                context.contentResolver.openOutputStream(uri, "wt")!!
            } else {
                FileOutputStream(file!!)
            }
            out.use { mutable.compress(Bitmap.CompressFormat.JPEG, settings.jpegQuality, it) }
        } finally {
            bmp.recycle()
        }
    }

    protected open fun prepareRecordImpl() {
        releaseCamera()

        val capture = ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder().setResolutionStrategy(
                    ResolutionStrategy(
                        Size(settings.frameWidth, settings.frameHeight),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                ).build()
            )
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(settings.jpegQuality)
            .build()
        imageCapture = capture

        // capture only: the viewfinder is not shown while recording
        camera = CameraEngine.bindBlocking(context, owner, settings.cameraIndex, listOf(capture))
        capture.targetRotation = toSurfaceRotation(cameraRotationDegrees(settings.cameraIndex))
        capture.flashMode =
            if (settings.flash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        applyCameraControls(camera)
        prepared = true
    }

    override fun releaseCamera() {
        imageCapture = null
        camera = null
        CameraEngine.unbindOwner(owner)
    }

    override fun prepareRecord() = prepareRecordImpl()

    override fun doRecord() = run()

    companion object {
        private const val TAG = "ImageRecorder"
        private const val CONTINUOUS_CAPTURE_THRESHOLD = 3000
        private const val RELEASE_CAMERA_THRESHOLD = 2000

        /** last saved frame, served by the REST API */
        @Volatile
        var currentRecordedImage: File? = null
            private set
    }
}
