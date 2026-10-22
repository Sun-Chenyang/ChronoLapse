package com.chronolapse.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "chronolapse_settings")

/**
 * Single source of truth for all settings. Both the UI (collected as Flow)
 * and the recording service (snapshot) read from here, so there is exactly
 * one copy of every value and no camera/settings duplication.
 */
class SettingsRepository(private val context: Context) {

    private object K {
        val mode = stringPreferencesKey("rec_mode")
        val cameraIndex = intPreferencesKey("camera_index")
        val intervalMs = intPreferencesKey("interval_ms")
        val playbackFps = intPreferencesKey("playback_fps")
        val frameWidth = intPreferencesKey("frame_width")
        val frameHeight = intPreferencesKey("frame_height")
        val jpegQuality = intPreferencesKey("jpeg_quality")
        val videoBitrate = intPreferencesKey("video_bitrate")
        val watermark = booleanPreferencesKey("watermark")
        val flash = booleanPreferencesKey("flash")
        val grid = booleanPreferencesKey("grid")
        val muteShutter = booleanPreferencesKey("mute_shutter")
        val exposureComp = intPreferencesKey("exposure_comp")
        val zoom = intPreferencesKey("zoom")
        val initialDelayMs = intPreferencesKey("initial_delay_ms")
        val cameraInitDelayMs = intPreferencesKey("camera_init_delay_ms")
        val triggerDelayMs = intPreferencesKey("trigger_delay_ms")
        val stopAfterMin = intPreferencesKey("stop_after_min")
        val stopOnLowBattery = booleanPreferencesKey("stop_on_low_battery")
        val stopOnLowStorage = booleanPreferencesKey("stop_on_low_storage")
        val scheduleEnabled = booleanPreferencesKey("schedule_enabled")
        val scheduleTimeMs = longPreferencesKey("schedule_time_ms")
        val restEnabled = booleanPreferencesKey("rest_enabled")
        val restPort = intPreferencesKey("rest_port")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun snapshot(): AppSettings = flow.first()

    private fun androidx.datastore.preferences.core.Preferences.toSettings(): AppSettings {
        val mode = if (this[K.mode] == RecMode.PHOTO_LAPSE.name) RecMode.PHOTO_LAPSE else RecMode.VIDEO_LAPSE
        return AppSettings(
            recMode = mode,
            cameraIndex = this[K.cameraIndex] ?: 0,
            intervalMs = this[K.intervalMs] ?: 5000,
            playbackFps = this[K.playbackFps] ?: 30,
            frameWidth = this[K.frameWidth] ?: 1920,
            frameHeight = this[K.frameHeight] ?: 1080,
            jpegQuality = this[K.jpegQuality] ?: 90,
            videoBitrate = this[K.videoBitrate] ?: 0,
            watermark = this[K.watermark] ?: false,
            flash = this[K.flash] ?: false,
            grid = this[K.grid] ?: false,
            muteShutter = this[K.muteShutter] ?: false,
            exposureComp = this[K.exposureComp] ?: 0,
            zoom = this[K.zoom] ?: 0,
            initialDelayMs = this[K.initialDelayMs] ?: 1000,
            cameraInitDelayMs = this[K.cameraInitDelayMs] ?: 500,
            triggerDelayMs = this[K.triggerDelayMs] ?: 1000,
            stopAfterMin = this[K.stopAfterMin] ?: AppSettings.AUTO_STOP_MAX,
            stopOnLowBattery = this[K.stopOnLowBattery] ?: true,
            stopOnLowStorage = this[K.stopOnLowStorage] ?: true,
            scheduleEnabled = this[K.scheduleEnabled] ?: false,
            scheduleTimeMs = this[K.scheduleTimeMs] ?: 0L,
            restEnabled = this[K.restEnabled] ?: false,
            restPort = this[K.restPort] ?: 8085,
        )
    }

    private suspend fun set(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { p ->
            val updated = transform(p.toSettings())
            p[K.mode] = updated.recMode.name
            p[K.cameraIndex] = updated.cameraIndex
            p[K.intervalMs] = updated.intervalMs
            p[K.playbackFps] = updated.playbackFps
            p[K.frameWidth] = updated.frameWidth
            p[K.frameHeight] = updated.frameHeight
            p[K.jpegQuality] = updated.jpegQuality
            p[K.videoBitrate] = updated.videoBitrate
            p[K.watermark] = updated.watermark
            p[K.flash] = updated.flash
            p[K.grid] = updated.grid
            p[K.muteShutter] = updated.muteShutter
            p[K.exposureComp] = updated.exposureComp
            p[K.zoom] = updated.zoom
            p[K.initialDelayMs] = updated.initialDelayMs
            p[K.cameraInitDelayMs] = updated.cameraInitDelayMs
            p[K.triggerDelayMs] = updated.triggerDelayMs
            p[K.stopAfterMin] = updated.stopAfterMin
            p[K.stopOnLowBattery] = updated.stopOnLowBattery
            p[K.stopOnLowStorage] = updated.stopOnLowStorage
            p[K.scheduleEnabled] = updated.scheduleEnabled
            p[K.scheduleTimeMs] = updated.scheduleTimeMs
            p[K.restEnabled] = updated.restEnabled
            p[K.restPort] = updated.restPort
        }
    }

    suspend fun setRecMode(mode: RecMode) = set { it.copy(recMode = mode) }
    suspend fun setCameraIndex(index: Int) = set { it.copy(cameraIndex = index) }
    suspend fun setInterval(ms: Int) = set { it.copy(intervalMs = ms) }
    suspend fun setPlaybackFps(fps: Int) = set { it.copy(playbackFps = fps) }
    suspend fun setFrameSize(width: Int, height: Int) = set { it.copy(frameWidth = width, frameHeight = height) }
    suspend fun setJpegQuality(quality: Int) = set { it.copy(jpegQuality = quality) }
    suspend fun setVideoBitrate(bitrate: Int) = set { it.copy(videoBitrate = bitrate) }
    suspend fun setWatermark(on: Boolean) = set { it.copy(watermark = on) }
    suspend fun setFlash(on: Boolean) = set { it.copy(flash = on) }
    suspend fun setGrid(on: Boolean) = set { it.copy(grid = on) }
    suspend fun setMuteShutter(on: Boolean) = set { it.copy(muteShutter = on) }
    suspend fun setExposureComp(value: Int) = set { it.copy(exposureComp = value) }
    suspend fun setZoom(value: Int) = set { it.copy(zoom = value) }
    suspend fun setInitialDelay(ms: Int) = set { it.copy(initialDelayMs = ms) }
    suspend fun setCameraInitDelay(ms: Int) = set { it.copy(cameraInitDelayMs = ms) }
    suspend fun setTriggerDelay(ms: Int) = set { it.copy(triggerDelayMs = ms) }
    suspend fun setStopAfterMin(min: Int) = set { it.copy(stopAfterMin = min) }
    suspend fun setStopOnLowBattery(on: Boolean) = set { it.copy(stopOnLowBattery = on) }
    suspend fun setStopOnLowStorage(on: Boolean) = set { it.copy(stopOnLowStorage = on) }
    suspend fun setSchedule(enabled: Boolean, timeMs: Long) = set { it.copy(scheduleEnabled = enabled, scheduleTimeMs = timeMs) }
    suspend fun setRestEnabled(on: Boolean) = set { it.copy(restEnabled = on) }
    suspend fun setRestPort(port: Int) = set { it.copy(restPort = port) }
}
