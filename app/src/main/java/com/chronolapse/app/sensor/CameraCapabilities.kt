package com.chronolapse.app.sensor

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.util.Size

/**
 * Camera capability queries based on camera2 characteristics, cached in
 * memory per process.
 */
class CameraCapabilities(private val context: Context) {

    data class Entry(val id: String, val facing: Int)

    private val cameras: List<Entry> by lazy {
        val out = ArrayList<Entry>()
        try {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val front = ArrayList<Entry>()
            val back = ArrayList<Entry>()
            for (id in manager.cameraIdList) {
                val facing = manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING)
                    ?: continue
                val e = Entry(id, facing)
                if (facing == CameraMetadata.LENS_FACING_FRONT) front.add(e) else back.add(e)
            }
            val byId = compareBy<Entry> { it.id.toIntOrNull() ?: Int.MAX_VALUE }
            out.apply { addAll(back.sortedWith(byId)); addAll(front.sortedWith(byId)) }
        } catch (_: Exception) {
        }
        out
    }

    fun enumerate(): List<Entry> = cameras

    fun cameraIdForIndex(index: Int): String? =
        cameras.getOrNull(index)?.id

    private fun characteristics(camId: Int): CameraCharacteristics? {
        return try {
            val id = cameraIdForIndex(camId) ?: return null
            (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(id)
        } catch (_: Exception) {
            null
        }
    }

    /** output sizes for still capture (both modes assemble from stills) */
    fun pictureSizes(camId: Int): List<Size> {
        val map = characteristics(camId)?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(ImageFormat.JPEG) ?: return fallbackSizes(camId)
        return sizes.sortedWith(compareBy({ it.width }, { it.height }))
    }

    private fun fallbackSizes(camId: Int): List<Size> {
        val out = ArrayList<Size>()
        val id = cameraIdForIndex(camId)?.toIntOrNull() ?: return out
        for (quality in intArrayOf(
            CamcorderProfile.QUALITY_HIGH, CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P, CamcorderProfile.QUALITY_480P, CamcorderProfile.QUALITY_LOW
        )) {
            try {
                if (CamcorderProfile.hasProfile(id, quality)) {
                    val p = CamcorderProfile.get(id, quality)
                    out.add(Size(p.videoFrameWidth, p.videoFrameHeight))
                }
            } catch (_: Exception) {
            }
        }
        return out.distinctBy { it.width to it.height }.sortedWith(compareBy({ it.width }, { it.height }))
    }

    fun exposureRange(camId: Int): IntRange {
        val range = characteristics(camId)?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
        return if (range == null) 0..0 else range.lower..range.upper
    }

    /** zoom is expressed on a 0..100 percent scale */
    fun maxZoom(): Int = 100
}
