package com.chronolapse.app.recorder

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.camera.core.Camera
import androidx.camera.core.CameraFilter
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.chronolapse.app.sensor.CameraCapabilities
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Process wide owner of the CameraX provider and of every use case binding.
 * The camera screen and the recording service both bind through this object,
 * and unbinding always targets specific use cases or owners - never
 * unbindAll(), which would tear down the other party's session (the classic
 * bug of the previous implementation).
 */
object CameraEngine {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var cached: ProcessCameraProvider? = null

    /** use case -> owning lifecycle, only accessed on the main thread */
    private val bound = LinkedHashMap<UseCase, LifecycleOwner>()

    private suspend fun provider(context: Context): ProcessCameraProvider {
        cached?.let { return it }
        val p = ProcessCameraProvider.getInstance(context.applicationContext).await()
        cached = p
        return p
    }

    private suspend fun <T> ListenableFuture<T>.await(): T =
        suspendCancellableCoroutine { cont ->
            addListener({
                try {
                    cont.resume(get())
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            }, java.util.concurrent.Executor { it.run() })
        }

    /**
     * Bind [cases] for [owner], replacing only the cases previously bound by
     * the same owner. Safe to call from any thread; runs on the main thread.
     */
    suspend fun bind(
        context: Context,
        owner: LifecycleOwner,
        cameraIndex: Int,
        cases: List<UseCase>,
    ): Camera {
        val p = provider(context)
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            unbindOwnerLocked(p, owner)
            val camera = p.bindToLifecycle(owner, selectorFor(context, cameraIndex), *cases.toTypedArray())
            for (c in cases) bound[c] = owner
            camera
        }
    }

    fun bindBlocking(
        context: Context,
        owner: LifecycleOwner,
        cameraIndex: Int,
        cases: List<UseCase>,
    ): Camera = kotlinx.coroutines.runBlocking { bind(context, owner, cameraIndex, cases) }

    /** Unbind every use case owned by [owner]. */
    fun unbindOwner(owner: LifecycleOwner) {
        val p = cached ?: return
        runOnMain {
            unbindOwnerLocked(p, owner)
        }
    }

    /** Unbind one specific use case, e.g. the live preview while capturing. */
    fun unbind(case: UseCase) {
        val p = cached ?: return
        runOnMain {
            if (bound.remove(case) != null) {
                runCatching { p.unbind(case) }
            }
        }
    }

    private fun unbindOwnerLocked(p: ProcessCameraProvider, owner: LifecycleOwner) {
        val mine = bound.filterValues { it === owner }.keys.toList()
        if (mine.isNotEmpty()) {
            runCatching { p.unbind(*mine.toTypedArray()) }
            mine.forEach { bound.remove(it) }
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    fun selectorFor(context: Context, index: Int): CameraSelector {
        val id = CameraCapabilities(context).cameraIdForIndex(index)
        return CameraSelector.Builder().addCameraFilter(object : CameraFilter {
            override fun filter(cameraInfos: MutableList<CameraInfo>): MutableList<CameraInfo> {
                if (id == null) return cameraInfos
                return cameraInfos.filter {
                    androidx.camera.camera2.interop.Camera2CameraInfo.from(it).cameraId == id
                }.toMutableList()
            }
        }).build()
    }
}
