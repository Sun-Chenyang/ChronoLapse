package com.chronolapse.app.data

/** The two capture modes of the app; normal video recording was removed. */
enum class RecMode { PHOTO_LAPSE, VIDEO_LAPSE }

/**
 * All user facing settings in one immutable snapshot, persisted through
 * [SettingsRepository]. High frequency capture parameters (mode, interval,
 * fps, size, watermark) are edited on the camera screen, the rest lives in
 * the settings screen.
 */
data class AppSettings(
    val recMode: RecMode = RecMode.VIDEO_LAPSE,
    val cameraIndex: Int = 0,
    val intervalMs: Int = 5000,
    val playbackFps: Int = 30,
    val frameWidth: Int = 1920,
    val frameHeight: Int = 1080,
    val jpegQuality: Int = 90,
    /** 0 = let the encoder decide */
    val videoBitrate: Int = 0,
    val watermark: Boolean = false,
    val flash: Boolean = false,
    val grid: Boolean = false,
    val muteShutter: Boolean = false,
    val exposureComp: Int = 0,
    /** 0..100 percent */
    val zoom: Int = 0,
    val initialDelayMs: Int = 1000,
    val cameraInitDelayMs: Int = 500,
    val triggerDelayMs: Int = 1000,
    /** auto stop after N minutes; >= AUTO_STOP_MAX means infinite */
    val stopAfterMin: Int = AUTO_STOP_MAX,
    val stopOnLowBattery: Boolean = true,
    val stopOnLowStorage: Boolean = true,
    val scheduleEnabled: Boolean = false,
    val scheduleTimeMs: Long = 0L,
    val restEnabled: Boolean = false,
    val restPort: Int = 8085,
) {
    /** negative value disables the auto stop limit */
    val stopAfterMs: Long
        get() = if (stopAfterMin >= AUTO_STOP_MAX) -1L else stopAfterMin * 60_000L

    /** intervals above the threshold switch to the alarm driven power saving mode */
    val powerSaving: Boolean
        get() = intervalMs > POWER_SAVE_THRESHOLD_MS

    val schedulePending: Boolean
        get() = scheduleEnabled && scheduleTimeMs > System.currentTimeMillis()

    companion object {
        const val AUTO_STOP_MAX = 47 * 60
        const val POWER_SAVE_THRESHOLD_MS = 10_000
    }
}
