package com.chronolapse.app.ui.library

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronolapse.app.R
import com.chronolapse.app.data.MediaRepository
import com.chronolapse.app.ui.components.GlassChip
import com.chronolapse.app.ui.components.MediaThumbImage
import com.chronolapse.app.ui.fmtDuration
import com.chronolapse.app.ui.theme.Amber
import com.chronolapse.app.ui.theme.GlassFill
import com.chronolapse.app.ui.theme.InkBg
import com.chronolapse.app.ui.theme.InkCard
import com.chronolapse.app.ui.theme.RecRed
import com.chronolapse.app.ui.theme.TextDim
import com.chronolapse.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed class Entry {
    class Project(val p: MediaRepository.PhotoProject) : Entry()
    class Video(val v: MediaRepository.VideoItem) : Entry()

    /** stable key used by multi select */
    val key: String
        get() = when (this) {
            is Project -> "p:${p.dir}|${p.session}"
            is Video -> "v:${v.uri}"
        }

    val dateSec: Long
        get() = when (this) {
            is Project -> p.dateAddedSec
            is Video -> v.dateAddedSec
        }

    /** every media uri this entry owns (used by batch delete) */
    fun uris(): List<android.net.Uri> = when (this) {
        is Project -> p.frames
        is Video -> listOf(v.uri)
    }
}

/**
 * Media library: photo sessions aggregated into projects, time lapse videos
 * listed flat, merged newest first. Long press enters multi select for
 * batch deletion.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenProject: (dir: String, session: String, title: String) -> Unit,
    onOpenVideo: (uri: String, title: String, durationMs: Long) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var filter by remember { mutableIntStateOf(0) } // 0 all 1 photo 2 video
    var refresh by remember { mutableIntStateOf(0) }
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }

    val loaded by produceState<List<Entry>>(emptyList(), refresh) {
        value = withContext(Dispatchers.IO) {
            val merged = ArrayList<Entry>()
            MediaRepository.photoProjects(context).forEach { merged.add(Entry.Project(it)) }
            MediaRepository.videos(context).forEach { merged.add(Entry.Video(it)) }
            merged.sortedByDescending { it.dateSec }
        }
    }

    val shown = loaded.filter {
        when (filter) {
            1 -> it is Entry.Project
            2 -> it is Entry.Video
            else -> true
        }
    }

    fun toggleSelection(entry: Entry) {
        val key = entry.key
        if (!selected.remove(key)) selected.add(key)
    }

    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current

    fun exitSelection() {
        selectionMode = false
        selected.clear()
    }

    fun deleteSelected() {
        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        val targets = shown.filter { it.key in selected }
        val uris = targets.flatMap { it.uris() }
        Thread {
            val n = MediaRepository.delete(context, uris)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    context,
                    if (n > 0) R.string.viewer_deleted else R.string.viewer_delete_failed,
                    Toast.LENGTH_SHORT,
                ).show()
                refresh++
                exitSelection()
            }
        }.start()
    }

    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(InkBg)
            .statusBarsPadding()
    ) {
        // ---------------------------------------------------------- header
        if (selectionMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Close, stringResource(R.string.select_cancel),
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable { exitSelection() }
                        .padding(8.dp),
                )
                Text(
                    stringResource(R.string.select_count, selected.size),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Icon(
                    Icons.Outlined.SelectAll, stringResource(R.string.select_all),
                    tint = TextPrimary,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            shown.forEach { if (it.key !in selected) selected.add(it.key) }
                        }
                        .padding(8.dp),
                )
                Icon(
                    Icons.Outlined.Delete, stringResource(R.string.viewer_delete),
                    tint = if (selected.isEmpty()) TextDim else RecRed,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .clickable(enabled = selected.isNotEmpty()) {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            confirmDelete = true
                        }
                        .padding(8.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onBack() }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack, null,
                        tint = TextPrimary, modifier = Modifier.height(18.dp),
                    )
                    Text(
                        stringResource(R.string.gallery_back),
                        color = TextDim, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 2.dp),
                    )
                }
                Text(
                    stringResource(R.string.gallery_title),
                    color = TextPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(60.dp))
            }
        }

        // -------------------------------------------------------- filters
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            val labels = listOf(
                stringResource(R.string.filter_all),
                stringResource(R.string.filter_photo),
                stringResource(R.string.filter_video),
            )
            labels.forEachIndexed { i, label ->
                GlassChip(text = label, icon = null, on = filter == i, onClick = { filter = i })
            }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.gallery_empty), color = TextDim, fontSize = 13.sp)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown) { entry ->
                    val isSel = entry.key in selected
                    MediaCard(
                        entry = entry,
                        selected = selectionMode && isSel,
                        onClick = {
                            if (selectionMode) {
                                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                toggleSelection(entry)
                            } else when (entry) {
                                is Entry.Project -> onOpenProject(entry.p.dir, entry.p.session, entry.p.title)
                                is Entry.Video -> onOpenVideo(entry.v.uri.toString(), entry.v.name, entry.v.durationMs)
                            }
                        },
                        onLongClick = {
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            if (!selectionMode) selectionMode = true
                            toggleSelection(entry)
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = com.chronolapse.app.ui.theme.InkSurface,
            text = {
                Text(
                    stringResource(R.string.select_delete_confirm, selected.size),
                    color = TextPrimary, fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    deleteSelected()
                }) { Text(stringResource(R.string.viewer_delete), color = RecRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.schedule_cancel), color = TextDim)
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaCard(
    entry: Entry,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(InkCard)
            .then(
                if (selected) Modifier.border(2.dp, Amber, RoundedCornerShape(12.dp))
                else Modifier
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        ) {
            when (entry) {
                is Entry.Project -> entry.p.cover?.let {
                    MediaThumbImage(it, video = false, size = 400, modifier = Modifier.fillMaxSize())
                }
                is Entry.Video -> MediaThumbImage(entry.v.uri, video = true, size = 400, modifier = Modifier.fillMaxSize())
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(androidx.compose.ui.graphics.Color(0x73000000))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val (icon, label) = if (entry is Entry.Project) {
                    Icons.Outlined.Image to stringResource(R.string.badge_photo)
                } else {
                    Icons.Outlined.Videocam to stringResource(R.string.badge_video)
                }
                Icon(icon, null, tint = TextPrimary, modifier = Modifier.height(10.dp))
                Text(label, color = TextPrimary, fontSize = 10.sp, modifier = Modifier.padding(start = 3.dp))
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Amber),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check,
                        null,
                        tint = com.chronolapse.app.ui.theme.OnAmber,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            val title = when (entry) {
                is Entry.Project -> entry.p.title
                is Entry.Video -> entry.v.name
            }
            Text(title, color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = when (entry) {
                is Entry.Project -> stringResource(R.string.g_frames, entry.p.count) + " · " +
                    DateUtils.getRelativeTimeSpanString(entry.p.dateAddedSec * 1000).toString()
                is Entry.Video ->
                    (if (entry.v.durationMs > 0) fmtDuration(entry.v.durationMs) + " · " else "") +
                        DateUtils.getRelativeTimeSpanString(entry.v.dateAddedSec * 1000).toString()
            }
            Text(subtitle, color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 1)
        }
    }
}
