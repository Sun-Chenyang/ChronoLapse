package com.chronolapse.app.sensor

import android.content.Context
import android.hardware.Camera
import android.view.OrientationEventListener

/** Tracks the device orientation for capture target rotation. */
@Suppress("DEPRECATION")
class OrientationSensor(context: Context) : OrientationEventListener(context) {

    private var orientation = 0

    override fun onOrientationChanged(orientation: Int) {
        if (orientation != ORIENTATION_UNKNOWN) this.orientation = orientation
    }

    fun getCameraRotation(cameraId: Int): Int {
        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)

        val rounded = (orientation + 45) / 90 * 90
        return if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            (info.orientation - rounded + 360) % 360
        } else {
            (info.orientation + rounded) % 360
        }
    }
}
