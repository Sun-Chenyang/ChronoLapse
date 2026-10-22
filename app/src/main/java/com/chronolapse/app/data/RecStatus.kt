package com.chronolapse.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live state of the recording session, written by the service / recorders
 * and collected by the camera UI. Exactly one session runs per process.
 */
data class RecState(
    val running: Boolean = false,
    val waitingForSchedule: Boolean = false,
    /** wall clock when capturing started, 0 while waiting */
    val startTime: Long = 0L,
    val frameCount: Int = 0,
    /** wall clock time of the most recent captured frame */
    val lastFrameAt: Long = 0L,
    val captureRateMs: Int = 2000,
    val playbackFps: Int = 30,
    val frameWidth: Int = 1920,
    val frameHeight: Int = 1080,
    val videoMode: Boolean = true,
    val watermark: Boolean = false,
)

object RecStatus {

    private val _state = MutableStateFlow(RecState())
    val state: StateFlow<RecState> = _state

    fun update(transform: (RecState) -> RecState) {
        _state.value = transform(_state.value)
    }

    fun beginCapture(
        captureRateMs: Int,
        playbackFps: Int,
        frameWidth: Int,
        frameHeight: Int,
        videoMode: Boolean,
        watermark: Boolean,
    ) {
        update {
            RecState(
                running = true,
                startTime = System.currentTimeMillis(),
                captureRateMs = captureRateMs.coerceAtLeast(100),
                playbackFps = if (playbackFps > 0) playbackFps else 30,
                frameWidth = frameWidth,
                frameHeight = frameHeight,
                videoMode = videoMode,
                watermark = watermark,
            )
        }
    }

    fun onFrameCaptured() {
        update {
            it.copy(frameCount = it.frameCount + 1, lastFrameAt = System.currentTimeMillis())
        }
    }

    fun stop() {
        update { it.copy(running = false, waitingForSchedule = false) }
    }

    fun waiting() {
        update { it.copy(running = true, waitingForSchedule = true) }
    }
}
