package com.chronolapse.app.ui.camera

import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronolapse.app.R
import com.chronolapse.app.data.AppSettings
import com.chronolapse.app.data.RecMode
import com.chronolapse.app.data.RecStatus
import com.chronolapse.app.data.ServiceLocator
import com.chronolapse.app.service.RecordingController
import com.chronolapse.app.ui.components.GlassChip
import com.chronolapse.app.ui.components.MediaThumbImage
import com.chronolapse.app.ui.components.ModeUnderline
import com.chronolapse.app.ui.components.ParamChip
import com.chronolapse.app.ui.components.RecDot
import com.chronolapse.app.ui.components.ScheduleDialog
import com.chronolapse.app.ui.fmtDuration
import com.chronolapse.app.ui.fmtElapsed
import com.chronolapse.app.ui.fmtInterval
import com.chronolapse.app.ui.theme.Amber
import com.chronolapse.app.ui.theme.GlassFill
import com.chronolapse.app.ui.theme.InkGlass
import com.chronolapse.app.ui.theme.RecRed
import com.chronolapse.app.ui.theme.TextDim
import com.chronolapse.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import kotlin.math.roundToInt
import java.util.Date
import java.util.Locale

/**
 * Camera style home screen: fullscreen viewfinder, photo/video lapse modes,
 * quick capture settings, live recording state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    granted: Boolean,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val repo = ServiceLocator.settings
    val settings by repo.flow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val rec by RecStatus.state.collectAsStateWithLifecycle()
    val capturing = rec.running && !rec.waitingForSchedule
    val now = ticker()

    // viewfinder camera handle + manual controls (idle only)
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var linearZoom by remember { mutableFloatStateOf(settings.zoom / 100f) }
    var evOpen by remember { mutableStateOf(false) }
    var evIndex by remember { mutableIntStateOf(settings.exposureComp) }
    var evRange by remember { mutableStateOf(IntRange(0, 0)) }
    var focusRing by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var hasFlashUnit by remember { mutableStateOf(true) }

    // keep the screen on while the camera screen is visible
    DisposableEffect(Unit) {
        val window = (context as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var sheetOpen by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var flashOn by remember(settings.flash) { mutableStateOf(settings.flash) }
    var gridOn by remember(settings.grid) { mutableStateOf(settings.grid) }
    var watermarkOn by remember(settings.watermark) { mutableStateOf(settings.watermark) }

    fun toggleFlash() = scope.launch { repo.setFlash(!flashOn) }
    fun toggleGrid() = scope.launch { repo.setGrid(!gridOn) }
    fun toggleWatermark() = scope.launch { repo.setWatermark(!watermarkOn) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ---------------- viewfinder ----------------
        Viewfinder(
            granted = granted,
            cameraIndex = settings.cameraIndex,
            capturing = capturing,
            linearZoom = linearZoom,
            initialEv = settings.exposureComp,
            onCamera = { cam ->
                camera = cam
                if (cam != null) {
                    hasFlashUnit = cam.cameraInfo.hasFlashUnit()
                    val es = cam.cameraInfo.exposureState
                    val r = es.exposureCompensationRange
                    evRange = r.lower..r.upper
                    evIndex = es.exposureCompensationIndex
                }
            },
            onZoom = { linear, ratio ->
                linearZoom = linear
                zoomRatio = ratio
            },
            onFocusTap = { pt ->
                focusRing = pt
                scope.launch { delay(1200); if (focusRing == pt) focusRing = null }
            },
        )

        if (gridOn && !capturing) {
            GridOverlay(Modifier.fillMaxSize())
        }

        // focus ring
        val focusPoint = focusRing
        if (focusPoint != null) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val ring = 56.dp.toPx()
                        androidx.compose.ui.unit.IntOffset(
                            Math.round(focusPoint.x - ring),
                            Math.round(focusPoint.y - ring),
                        )
                    }
                    .size(112.dp)
                    .border(2.dp, Amber, CircleShape)
            )
        }

        // manual controls (idle only)
        if (!capturing && granted) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // zoom chip is always present and fixed-width so the column
                // layout never jumps when the percentage grows to "100%"
                val zoomed = zoomRatio > 1.05f || linearZoom > 0.02f
                GlassChip(
                    text = when {
                        zoomRatio > 1.05f -> String.format(Locale.US, "%.1f×", zoomRatio)
                        linearZoom > 0.02f -> "${(linearZoom * 100).toInt()}%"
                        else -> "1×"
                    },
                    icon = null,
                    on = zoomed,
                    fixedWidth = 40.dp,
                    contentDescription = stringResource(R.string.cam_zoom),
                    onClick = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        camera?.cameraControl?.setLinearZoom(0f)
                        linearZoom = 0f
                        zoomRatio = 1f
                    },
                )
                Spacer(Modifier.height(12.dp))

                // vertical zoom slider (dedicated fixed region, no rotation)
                VerticalZoomSlider(
                    value = linearZoom,
                    onValueChange = {
                        linearZoom = it
                        camera?.cameraControl?.setLinearZoom(it)
                    },
                )

                Spacer(Modifier.height(12.dp))

                if (evRange.first < evRange.last) {
                    GlassChip(
                        text = "AE" + (if (evIndex != 0) " ${if (evIndex > 0) "+" else ""}$evIndex" else ""),
                        icon = null,
                        on = evOpen || evIndex != 0,
                        fixedWidth = 44.dp,
                        contentDescription = stringResource(R.string.cam_ev),
                        onClick = { evOpen = !evOpen },
                    )
                }
            }

            if (evOpen && evRange.first < evRange.last) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 56.dp, end = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(InkGlass)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.cam_ev),
                            color = TextPrimary, fontSize = 11.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            if (evIndex > 0) "+$evIndex" else evIndex.toString(),
                            color = Amber, fontSize = 11.sp,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                        Text(
                            "0",
                            color = if (evIndex == 0) Amber else TextDim,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .clickable {
                                    camera?.cameraControl?.setExposureCompensationIndex(0)
                                    evIndex = 0
                                    scope.launch { repo.setExposureComp(0) }
                                },
                        )
                    }
                    androidx.compose.material3.Slider(
                        value = evIndex.toFloat(),
                        onValueChange = {
                            evIndex = it.toInt()
                            camera?.cameraControl?.setExposureCompensationIndex(evIndex)
                        },
                        onValueChangeFinished = { scope.launch { repo.setExposureComp(evIndex) } },
                        valueRange = evRange.first.toFloat()..evRange.last.toFloat(),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = TextPrimary,
                            activeTrackColor = Amber,
                            inactiveTrackColor = Color(0x14FFFFFF),
                        ),
                    )
                }
            }

            // persist zoom after the pinch settles
            LaunchedEffect(linearZoom) {
                delay(600)
                repo.setZoom((linearZoom * 100).toInt())
            }
        }

        // watermark preview, mimics the burned-in stamp position
        if (watermarkOn && !capturing) {
            Text(
                text = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()) },
                color = Color.White,
                fontSize = 11.sp,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 4f)
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 230.dp),
            )
        }

        // ---------------- top area ----------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xA6000000), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp)
                .padding(bottom = 26.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (hasFlashUnit) {
                        GlassChip(
                            text = stringResource(R.string.cam_flash),
                            icon = Icons.Outlined.FlashOff,
                            on = flashOn,
                            onClick = { toggleFlash() },
                        )
                    }
                    GlassChip(
                        text = null,
                        icon = Icons.Outlined.GridOn,
                        on = gridOn,
                        contentDescription = stringResource(R.string.cam_grid),
                        onClick = { toggleGrid() },
                    )
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip(
                        text = stringResource(R.string.cam_watermark),
                        icon = Icons.Outlined.Schedule,
                        on = watermarkOn,
                        onClick = { toggleWatermark() },
                    )
                    GlassChip(
                        text = null,
                        icon = Icons.Outlined.MoreVert,
                        on = false,
                        contentDescription = stringResource(R.string.cam_more),
                        onClick = onOpenSettings,
                    )
                }
            }

            if (rec.running) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(15.dp))
                        .background(InkGlass)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (capturing) {
                        Text(
                            text = stringResource(R.string.rec_pill, fmtElapsed(now - rec.startTime)),
                            color = TextPrimary,
                            fontSize = 13.sp,
                        )
                    } else {
                        RecDot()
                        Text(
                            text = " " + stringResource(
                                R.string.rec_waiting,
                                android.text.format.DateFormat.format("HH:mm", recScheduleTime(settings)).toString()
                            ),
                            color = TextPrimary,
                            fontSize = 13.sp,
                        )
                    }
                }
                if (capturing) {
                    Text(
                        stringResource(R.string.rec_bg_hint),
                        color = TextDim,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 6.dp),
                    )
                }
            }
        }

        // flip camera
        Icon(
            Icons.Outlined.FlipCameraAndroid,
            contentDescription = stringResource(R.string.cam_flip),
            tint = TextPrimary,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(InkGlass)
                .padding(10.dp)
                .clickable(enabled = !capturing) {
                    scope.launch { repo.setCameraIndex((settings.cameraIndex + 1) % cameraCount(context, settings)) }
                },
        )

        // ---------------- bottom area ----------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xB3000000)))
                )
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            // recording stats
            if (capturing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Schedule, null,
                        tint = Amber, modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.rec_next_frame,
                            context.getString(R.string.fmt_sec_short, nextFrameText(rec, now))
                        ),
                        color = TextPrimary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Text(
                    text = stringResource(R.string.rec_frames, rec.frameCount) + " · " +
                        stringResource(R.string.rec_est, fmtDuration(rec.frameCount * 1000L / rec.playbackFps)),
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
            }

            if (!capturing) {
                Text(
                    fmtInterval(settings.intervalMs) + " · " + sizeLabel(settings) + " · " + settings.playbackFps + "fps",
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(13.dp))
                        .background(InkGlass)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }

            // mode selector
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeTab(
                    stringResource(R.string.mode_photo),
                    selected = settings.recMode == RecMode.PHOTO_LAPSE,
                    enabled = !capturing,
                ) { scope.launch { repo.setRecMode(RecMode.PHOTO_LAPSE) } }
                Box(
                    Modifier
                        .padding(horizontal = 6.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(TextDim)
                )
                ModeTab(
                    stringResource(R.string.mode_video),
                    selected = settings.recMode == RecMode.VIDEO_LAPSE,
                    enabled = !capturing,
                ) { scope.launch { repo.setRecMode(RecMode.VIDEO_LAPSE) } }
            }

            // control row: library thumb / shutter / params
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val thumb by produceState<android.net.Uri?>(null, rec.running) {
                    value = com.chronolapse.app.ui.components.io {
                        com.chronolapse.app.data.MediaRepository.latestThumb(context).uri
                    }
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22FFFFFF))
                        .clickable { onOpenLibrary() },
                    contentAlignment = Alignment.Center,
                ) {
                    val t = thumb
                    if (t != null) MediaThumbImage(t, video = false, size = 128, modifier = Modifier.fillMaxSize())
                }

                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color(0x14000000))
                            .border(3.dp, Color.White, CircleShape)
                            .clickable {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                if (RecStatus.state.value.running) RecordingController.stop(context)
                                else if (granted) RecordingController.start(context)
                                else Toast.makeText(context, R.string.error_missing_permission, Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (capturing) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(RecRed)
                            )
                        } else {
                            Box(Modifier.size(54.dp).clip(CircleShape).background(Color.White))
                        }
                    }
                }

                Icon(
                    Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.cam_params),
                    tint = if (capturing) TextDim else TextPrimary,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(InkGlass)
                        .padding(13.dp)
                        .clickable(enabled = !capturing) { sheetOpen = true },
                )
            }
        }
    }

    if (sheetOpen) {
        CaptureSheet(
            settings = settings,
            onDismiss = { sheetOpen = false },
            onInterval = { scope.launch { repo.setInterval(it) } },
            onFps = { scope.launch { repo.setPlaybackFps(it) } },
            onSize = { w, h -> scope.launch { repo.setFrameSize(w, h) } },
            onWatermark = { scope.launch { repo.setWatermark(it) } },
            onSchedule = {
                sheetOpen = false
                scheduleOpen = true
            },
        )
    }

    if (scheduleOpen) {
        ScheduleDialog(
            initialEnabled = settings.scheduleEnabled,
            initialTimeMs = settings.scheduleTimeMs,
            onDismiss = { scheduleOpen = false },
            onSave = { enabled, timeMs ->
                scheduleOpen = false
                scope.launch {
                    repo.setSchedule(enabled, timeMs)
                    if (enabled && timeMs > System.currentTimeMillis()) {
                        RecordingController.start(context)
                        Toast.makeText(context, R.string.schedule_set, Toast.LENGTH_SHORT).show()
                    } else {
                        RecordingController.stop(context)
                        Toast.makeText(context, R.string.schedule_cleared, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

// ---------------------------------------------------------------- viewfinder

@Composable
private fun Viewfinder(
    granted: Boolean,
    cameraIndex: Int,
    capturing: Boolean,
    linearZoom: Float,
    initialEv: Int,
    onCamera: (Camera?) -> Unit,
    onZoom: (linear: Float, ratio: Float) -> Unit,
    onFocusTap: (androidx.compose.ui.geometry.Offset) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity ?: return
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val scope = rememberCoroutineScope()
    var camera by remember { mutableStateOf<Camera?>(null) }
    val currentLinear by androidx.compose.runtime.rememberUpdatedState(linearZoom)

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )

    if (capturing) {
        // camera belongs to the recording service: no live viewfinder
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }

    // idle preview binding: the service owns the camera while capturing
    DisposableEffect(granted, cameraIndex, capturing) {
        var bound: androidx.camera.core.Preview? = null
        if (granted) {
            val preview = androidx.camera.core.Preview.Builder().build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            bound = preview
            scope.launch {
                runCatching {
                    val cam = com.chronolapse.app.recorder.CameraEngine.bind(
                        context, lifecycleOwner, cameraIndex, listOf(preview)
                    )
                    camera = cam
                    onCamera(cam)
                    // apply the persisted manual settings to the live camera
                    cam.cameraControl.setLinearZoom(currentLinear.coerceIn(0f, 1f))
                    val es = cam.cameraInfo.exposureState
                    if (es.isExposureCompensationSupported) {
                        val r = es.exposureCompensationRange
                        val idx = initialEv.coerceIn(r.lower, r.upper)
                        cam.cameraControl.setExposureCompensationIndex(idx)
                    }
                }
            }
        }
        onDispose {
            bound?.let { com.chronolapse.app.recorder.CameraEngine.unbind(it) }
            camera = null
            onCamera(null)
        }
    }

    // single gesture detector: pinch = zoom, tap = focus
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(granted, capturing, camera) {
                if (!granted || capturing || camera == null) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val tapPos = down.position
                    var isZoom = false
                    var moved = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (!event.changes.any { it.pressed }) break
                        val zoomChange = event.calculateZoom()
                        if (event.changes.size > 1 || zoomChange != 1f) {
                            isZoom = true
                            val cam = camera ?: continue
                            val linear = (currentLinear * zoomChange).coerceIn(0f, 1f)
                            cam.cameraControl.setLinearZoom(linear)
                            val ratio = cam.cameraInfo.zoomState?.value?.zoomRatio ?: 1f
                            onZoom(linear, ratio)
                            event.changes.forEach { change ->
                                if (change.positionChanged()) change.consume()
                            }
                        } else if (event.changes.first().positionChanged()) {
                            moved = true
                        }
                    }
                    if (!isZoom && !moved) {
                        val cam = camera ?: return@awaitEachGesture
                        val point = previewView.meteringPointFactory.createPoint(tapPos.x, tapPos.y)
                        cam.cameraControl.startFocusAndMetering(
                            androidx.camera.core.FocusMeteringAction.Builder(
                                point,
                                androidx.camera.core.FocusMeteringAction.FLAG_AF or
                                    androidx.camera.core.FocusMeteringAction.FLAG_AE
                            ).disableAutoCancel().build()
                        )
                        onFocusTap(tapPos)
                    }
                }
            }
    )
}

@Composable
private fun GridOverlay(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.5f * density
        for (i in 1..2) {
            drawLine(
                Color(0x59FFFFFF),
                androidx.compose.ui.geometry.Offset(w * i / 3f, 0f),
                androidx.compose.ui.geometry.Offset(w * i / 3f, h),
                strokeWidth = stroke,
            )
            drawLine(
                Color(0x59FFFFFF),
                androidx.compose.ui.geometry.Offset(0f, h * i / 3f),
                androidx.compose.ui.geometry.Offset(w, h * i / 3f),
                strokeWidth = stroke,
            )
        }
    }
}

/**
 * Vertical zoom control: fixed 48x170dp region containing track, fill and
 * thumb. Drawing and touch are strictly bounded by the box, so dragging
 * never affects elements outside it.
 */
@Composable
private fun VerticalZoomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val current by androidx.compose.runtime.rememberUpdatedState(value)
    val trackLength = 142.dp // 170dp minus 14dp inset on each side

    Box(
        modifier = Modifier
            .width(48.dp)
            .height(170.dp)
            .pointerInput(Unit) {
                val insetPx = 14.dp.toPx()
                val trackPx = (size.height - 2 * insetPx).coerceAtLeast(1f)
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val newValue = (current - dragAmount / trackPx).coerceIn(0f, 1f)
                        if (newValue != current) {
                            // tick at each quarter of the range
                            val before = (current * 4).toInt()
                            val after = (newValue * 4).toInt()
                            if (before != after) {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                )
                            }
                            onValueChange(newValue)
                        }
                    }
                )
            },
    ) {
        val fill = value.coerceIn(0f, 1f)

        // track
        Box(
            Modifier
                .align(Alignment.Center)
                .width(4.dp)
                .fillMaxHeight()
                .padding(vertical = 14.dp)
                .background(GlassFill, RoundedCornerShape(2.dp))
        )

        // amber fill growing from the bottom of the track
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp)
                .width(4.dp)
                .height(trackLength * fill)
                .background(Amber, RoundedCornerShape(2.dp))
        )

        // thumb: centered horizontally, positioned by fill from track bottom
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset {
                    androidx.compose.ui.unit.IntOffset(
                        ((48.dp - 24.dp) / 2).roundToPx(),
                        (14.dp + trackLength * (1f - fill) - 12.dp).roundToPx(),
                    )
                }
                .size(24.dp)
                .background(TextPrimary, CircleShape)
        )
    }
}

@Composable
private fun ModeTab(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
            ) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text,
            color = if (selected) TextPrimary else TextDim,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        ModeUnderline(selected)
    }
}

// ------------------------------------------------------------------- sheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaptureSheet(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onInterval: (Int) -> Unit,
    onFps: (Int) -> Unit,
    onSize: (Int, Int) -> Unit,
    onWatermark: (Boolean) -> Unit,
    onSchedule: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var intervalOpen by remember { mutableStateOf(false) }

    if (intervalOpen) {
        com.chronolapse.app.ui.components.IntervalPickerDialog(
            initialMs = settings.intervalMs.toLong(),
            onDismiss = { intervalOpen = false },
            onSave = {
                intervalOpen = false
                onInterval(it.toInt())
            },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.chronolapse.app.ui.theme.InkSurface,
    ) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
            SheetLabel(stringResource(R.string.sheet_interval))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ParamChip(
                    com.chronolapse.app.ui.fmtInterval(settings.intervalMs),
                    selected = true,
                ) { intervalOpen = true }
                Text(
                    stringResource(R.string.sheet_interval_custom),
                    color = Color(0x73FFFFFF),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                stringResource(R.string.sheet_power_hint),
                color = Color(0x73FFFFFF),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
            )

            SheetLabel(stringResource(R.string.sheet_fps))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15, 24, 30, 60).forEach { fps ->
                    ParamChip(fps.toString(), settings.playbackFps == fps) { onFps(fps) }
                }
            }

            SheetLabel(stringResource(R.string.sheet_size))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Triple(1280, 720, "720p"),
                    Triple(1920, 1080, "1080p"),
                    Triple(3840, 2160, "4K"),
                ).forEach { (w, h, label) ->
                    ParamChip(label, settings.frameWidth == w && settings.frameHeight == h) { onSize(w, h) }
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sheet_watermark), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.watermark,
                    onCheckedChange = onWatermark,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Amber,
                        checkedThumbColor = com.chronolapse.app.ui.theme.OnAmber,
                    ),
                )
            }

            Row(
                Modifier.fillMaxWidth().clickable { onSchedule() }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.sheet_schedule), color = TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(
                    if (settings.schedulePending)
                        android.text.format.DateFormat.format("MM-dd HH:mm", settings.scheduleTimeMs).toString()
                    else stringResource(R.string.sheet_off),
                    color = if (settings.schedulePending) Amber else TextDim,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text,
        color = TextDim,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

// ------------------------------------------------------------------- helpers

/** wall clock updated four times a second, drives the REC counters */
@Composable
private fun ticker(): Long {
    var value by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            value = System.currentTimeMillis()
            delay(250)
        }
    }
    return value
}

private fun nextFrameText(rec: com.chronolapse.app.data.RecState, nowMs: Long): String {
    val toNext = if (rec.lastFrameAt > 0)
        (rec.lastFrameAt + rec.captureRateMs - nowMs).coerceAtLeast(0)
    else rec.captureRateMs.toLong()
    // second-level countdown, rounded up so it never shows 0s early
    val sec = ((toNext + 999) / 1000).coerceAtLeast(1)
    return sec.toString()
}

private fun recScheduleTime(s: AppSettings): Long =
    if (s.schedulePending) s.scheduleTimeMs else System.currentTimeMillis()

private fun sizeLabel(s: AppSettings): String = when (s.frameHeight) {
    720 -> "720p"
    1080 -> "1080p"
    2160 -> "4K"
    else -> "${s.frameWidth}x${s.frameHeight}"
}

private fun cameraCount(context: android.content.Context, s: AppSettings): Int =
    com.chronolapse.app.sensor.CameraCapabilities(context).enumerate().size.coerceAtLeast(1)
