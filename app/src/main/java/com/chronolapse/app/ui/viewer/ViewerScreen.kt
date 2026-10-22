package com.chronolapse.app.ui.viewer

import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronolapse.app.R
import com.chronolapse.app.data.AppSettings
import com.chronolapse.app.data.MediaRepository
import com.chronolapse.app.data.ServiceLocator
import com.chronolapse.app.ui.fmtClock
import com.chronolapse.app.ui.fmtDuration
import com.chronolapse.app.ui.theme.Amber
import com.chronolapse.app.ui.theme.GlassFill
import com.chronolapse.app.ui.theme.InkGlass
import com.chronolapse.app.ui.theme.InkSurface
import com.chronolapse.app.ui.theme.OnAmber
import com.chronolapse.app.ui.theme.RecRed
import com.chronolapse.app.ui.theme.TextDim
import com.chronolapse.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Project viewer: swipe through a photo sequence, play it as a time lapse,
 * export MP4, share and delete; or play a time lapse video.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    type: String,
    dir: String?,
    session: String?,
    videoUri: String?,
    title: String,
    durationMs: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val photos = type == "photos"

    val settings by ServiceLocator.settings.flow.collectAsStateWithLifecycle(AppSettings())
    val fps = settings.playbackFps.coerceAtLeast(1)

    var frames by remember { mutableStateOf<List<Uri>>(emptyList()) }
    LaunchedEffect(dir, session) {
        if (photos) {
            frames = withContext(Dispatchers.IO) {
                MediaRepository.photoProjects(context)
                    .firstOrNull { it.dir == dir && it.session == session }?.frames
                    ?: emptyList()
            }
        }
    }

    val pagerState = rememberPagerState { if (photos) frames.size else 0 }
    var page by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page = it }
    }

    var photoPlaying by remember { mutableStateOf(false) }
    var videoPlaying by remember { mutableStateOf(false) }
    val playing = if (photos) photoPlaying else videoPlaying

    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var videoPos by remember { mutableIntStateOf(0) }
    var videoDur by remember { mutableIntStateOf(durationMs.toInt()) }

    var exporting by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // photo sequence playback
    LaunchedEffect(photoPlaying, frames) {
        while (photoPlaying && frames.isNotEmpty()) {
            val next = pagerState.currentPage + 1
            if (next >= frames.size) {
                photoPlaying = false
                break
            }
            pagerState.scrollToPage(next)
            delay((1000L / fps).coerceAtLeast(33))
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (photos) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
                PhotoPage(frames[index])
            }
        } else {
            videoUri?.let {
                VideoSurface(
                    uri = it,
                    onReady = { videoDur = it },
                    onView = { videoView = it },
                    onEnded = { videoPlaying = false },
                )
            }
        }

        // ---------------------------------------------------------- top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color(0xA6000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.gallery_back),
                tint = TextPrimary,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(9.dp),
            )
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                Text(
                    if (photos) stringResource(R.string.viewer_subtitle_photos, frames.size)
                    else fmtDuration(videoDur.toLong()),
                    color = TextDim, fontSize = 11.sp,
                )
            }
        }

        // ------------------------------------------------------ bottom panel
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            if (photos) {
                // scrubbing updates the pager live; steps snap to whole frames
                Slider(
                    value = page.toFloat(),
                    onValueChange = { v ->
                        val target = v.toInt().coerceIn(0, frames.lastIndex)
                        if (target != page) {
                            page = target
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            scope.launch { pagerState.scrollToPage(target) }
                        }
                    },
                    valueRange = 0f..(frames.size.coerceAtLeast(1) - 1).toFloat(),
                    steps = (frames.size - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary, activeTrackColor = Amber, inactiveTrackColor = GlassFill,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.viewer_frames, page + 1, frames.size),
                        color = TextPrimary, fontSize = 11.sp,
                    )
                    Spacer(Modifier.width(10.dp))

                    // single-frame stepping for fine inspection
                    StepButton(Icons.Outlined.ChevronLeft, enabled = page > 0) {
                        val target = (page - 1).coerceAtLeast(0)
                        page = target
                        scope.launch { pagerState.scrollToPage(target) }
                    }
                    StepButton(Icons.Outlined.ChevronRight, enabled = page < frames.lastIndex) {
                        val target = (page + 1).coerceAtMost(frames.lastIndex)
                        page = target
                        scope.launch { pagerState.scrollToPage(target) }
                    }

                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.viewer_fps_est, fps, fmtDuration(frames.size * 1000L / fps)),
                        color = TextDim, fontSize = 11.sp,
                    )
                }
            } else {
                LaunchedEffect(Unit) {
                    while (true) {
                        videoPos = videoPosPoll
                        delay(200)
                    }
                }
                var slider by remember(videoDur) { mutableFloatStateOf(videoPos.toFloat()) }
                Slider(
                    value = slider,
                    onValueChange = { slider = it },
                    onValueChangeFinished = {
                        videoView?.seekTo(slider.toInt())
                        videoPos = slider.toInt()
                    },
                    valueRange = 0f..videoDur.coerceAtLeast(1).toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = TextPrimary, activeTrackColor = Amber, inactiveTrackColor = GlassFill,
                    ),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(fmtClock(videoPos.toLong()), color = TextPrimary, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(fmtClock(videoDur.toLong()), color = TextDim, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Amber)
                        .clickable(enabled = if (photos) frames.isNotEmpty() else videoView != null) {
                            if (photos) {
                                if (photoPlaying) photoPlaying = false
                                else {
                                    if (page >= frames.size - 1) page = 0
                                    scope.launch { pagerState.scrollToPage(page) }
                                    photoPlaying = true
                                }
                            } else {
                                videoView?.let { v ->
                                    if (v.isPlaying) {
                                        v.pause()
                                        videoPlaying = false
                                    } else {
                                        v.start()
                                        videoPlaying = true
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        null, tint = OnAmber, modifier = Modifier.size(15.dp),
                    )
                    Text(
                        stringResource(if (playing) R.string.viewer_pause else R.string.viewer_play),
                        color = OnAmber, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (photos) {
                    ActionIcon(Icons.Outlined.Download, stringResource(R.string.viewer_export)) { exporting = true }
                }
                ActionIcon(Icons.Outlined.Share, stringResource(R.string.viewer_share)) {
                    val uri = if (photos) frames.getOrNull(page) else videoUri?.let(Uri::parse)
                    uri?.let {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            setType(if (photos) "image/jpeg" else "video/mp4")
                            putExtra(Intent.EXTRA_STREAM, it)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.viewer_share)))
                    }
                }
                ActionIcon(Icons.Outlined.Delete, stringResource(R.string.viewer_delete)) { confirmDelete = true }
            }
        }

        if (exporting) {
            ExportOverlay(frames, title, fps) { exporting = false }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                containerColor = InkSurface,
                text = {
                    Text(
                        if (photos) stringResource(R.string.viewer_delete_project, frames.size, title)
                        else stringResource(R.string.viewer_delete_video),
                        color = TextPrimary, fontSize = 14.sp,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        val uris = if (photos) frames else listOfNotNull(videoUri?.let(Uri::parse))
                        Thread {
                            val n = MediaRepository.delete(context, uris)
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context,
                                    if (n > 0) R.string.viewer_deleted else R.string.viewer_delete_failed,
                                    Toast.LENGTH_SHORT,
                                ).show()
                                if (n > 0) onBack()
                            }
                        }.start()
                    }) { Text(stringResource(R.string.g_menu_delete), color = RecRed) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDelete = false }) {
                        Text(stringResource(R.string.schedule_cancel), color = TextDim)
                    }
                },
            )
        }
    }
}

// ------------------------------------------------------------------ photos

@Composable
private fun PhotoPage(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<android.graphics.Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= 1024 && bounds.outHeight / (sample * 2) >= 1024) sample *= 2
                val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            }.getOrNull()
        }
    }
    val bitmap = bmp
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    } else {
        Box(Modifier.fillMaxSize())
    }
}

// ------------------------------------------------------------------- video

@Composable
private fun VideoSurface(
    uri: String,
    onReady: (Int) -> Unit,
    onView: (VideoView) -> Unit,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val view = remember { VideoView(context) }
    DisposableEffect(uri) {
        view.setVideoURI(Uri.parse(uri))
        view.setOnPreparedListener { _: MediaPlayer ->
            onReady(view.duration)
            view.start()
        }
        view.setOnCompletionListener {
            view.seekTo(0)
            onEnded()
        }
        onView(view)
        onDispose {
            view.stopPlayback()
        }
    }
    AndroidView(
        factory = {
            view.apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { onView(view) },
        modifier = Modifier.fillMaxSize(),
    )
    // position polling drives the slider / duration labels
    LaunchedEffect(view) {
        while (true) {
            if (view.isPlaying) videoPosPoll = view.currentPosition
            delay(250)
        }
    }
}

/** polled by [VideoSurface], read by the bottom panel; single viewer instance */
internal var videoPosPoll = 0

// ------------------------------------------------------------------ actions

/** small circular single-frame step button with a tick haptic */
@Composable
private fun androidx.compose.foundation.layout.RowScope.StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Icon(
        icon, null, tint = if (enabled) TextPrimary else TextDim,
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(InkGlass)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(6.dp),
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Icon(
        icon, description, tint = TextPrimary,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(InkGlass)
            .clickable { onClick() }
            .padding(9.dp),
    )
}

// ------------------------------------------------------------------ export

@Composable
private fun ExportOverlay(frames: List<Uri>, title: String, fps: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "export")
            cacheDir.deleteRecursively()
            try {
                val settings = ServiceLocator.settings.snapshot()
                val files = frames.mapIndexed { i, u ->
                    MediaRepository.copyToCache(context, u, cacheDir, "f%05d.jpg".format(i))
                }
                val tmp = File(cacheDir, "out.mp4")
                com.chronolapse.app.recorder.JpegSequenceAssembler().assemble(
                    files, tmp, fps, settings.frameWidth, settings.frameHeight
                )
                val safeTitle = title.replace(Regex("[^A-Za-z0-9._-]"), "_")
                MediaRepository.publishVideo(
                    context, tmp,
                    "${safeTitle}_%x.mp4".format(System.currentTimeMillis())
                )
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, R.string.viewer_export_done, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context, context.getString(R.string.viewer_export_fail, e.message ?: ""),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                cacheDir.deleteRecursively()
            }
        }
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xB3000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Amber)
            Text(
                stringResource(R.string.viewer_exporting),
                color = TextPrimary, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}
