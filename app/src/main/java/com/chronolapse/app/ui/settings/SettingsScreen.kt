package com.chronolapse.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chronolapse.app.BuildConfig
import com.chronolapse.app.R
import com.chronolapse.app.data.AppSettings
import com.chronolapse.app.data.ServiceLocator
import com.chronolapse.app.rest.RestServer
import com.chronolapse.app.sensor.CameraCapabilities
import com.chronolapse.app.ui.components.ParamChip
import com.chronolapse.app.ui.components.ScheduleDialog
import com.chronolapse.app.ui.components.SectionCard
import com.chronolapse.app.ui.components.SettingRow
import com.chronolapse.app.ui.components.SliderRow
import com.chronolapse.app.ui.components.SwitchRow
import com.chronolapse.app.ui.theme.Amber
import com.chronolapse.app.ui.theme.InkBg
import com.chronolapse.app.ui.theme.TextDim
import com.chronolapse.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Settings screen. Everything editable on the camera (mode, interval, fps,
 * size, watermark, flash, grid) intentionally does NOT appear here; this
 * screen only owns the low frequency options.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = ServiceLocator.settings
    val settings by repo.flow.collectAsStateWithLifecycle(AppSettings())
    val capabilities = remember { CameraCapabilities(context) }

    var scheduleOpen by remember { mutableStateOf(false) }
    var autostopOpen by remember { mutableStateOf(false) }
    var bitrateOpen by remember { mutableStateOf(false) }
    var portOpen by remember { mutableStateOf(false) }
    var licenseOpen by remember { mutableStateOf(false) }
    var cameraMenuOpen by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(InkBg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.gallery_back),
                tint = TextPrimary,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { onBack() }
                    .padding(8.dp),
            )
            Text(
                stringResource(R.string.settings_title),
                color = TextPrimary, fontSize = 17.sp,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(38.dp))
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------------------------------------------------- camera
            SectionCard(stringResource(R.string.set_camera_section)) {
                val cams = capabilities.enumerate()
                val backLabel = stringResource(R.string.set_cam_back)
                val frontLabel = stringResource(R.string.set_cam_front)
                Box {
                    SettingRow(
                        title = stringResource(R.string.set_camera),
                        value = cameraLabel(settings.cameraIndex, cams.size, backLabel, frontLabel),
                        onClick = { cameraMenuOpen = !cameraMenuOpen },
                    )
                    if (cameraMenuOpen) {
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(com.chronolapse.app.ui.theme.InkCard)
                                .padding(4.dp),
                        ) {
                            for (i in cams.indices) {
                                Text(
                                    cameraLabel(i, cams.size, backLabel, frontLabel),
                                    color = if (i == settings.cameraIndex) Amber else TextPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            cameraMenuOpen = false
                                            scope.launch { repo.setCameraIndex(i) }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }

                SwitchRow(
                    title = stringResource(R.string.set_mute_shutter),
                    subtitle = stringResource(R.string.set_mute_shutter_hint),
                    checked = settings.muteShutter,
                ) { on ->
                    if (on) {
                        val nm = context.getSystemService(android.app.NotificationManager::class.java)
                        if (nm?.isNotificationPolicyAccessGranted != true) {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                        }
                    }
                    scope.launch { repo.setMuteShutter(on) }
                }

                Text(
                    stringResource(R.string.set_camera_hint),
                    color = TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // ------------------------------------------------ automation
            SectionCard(stringResource(R.string.set_auto_section)) {
                SettingRow(
                    title = stringResource(R.string.set_schedule),
                    value = if (settings.schedulePending)
                        android.text.format.DateFormat.format("yyyy-MM-dd HH:mm", settings.scheduleTimeMs).toString()
                    else stringResource(R.string.sheet_off),
                    onClick = { scheduleOpen = true },
                )
                SettingRow(
                    title = stringResource(R.string.set_autostop),
                    value = if (settings.stopAfterMin >= AppSettings.AUTO_STOP_MAX)
                        stringResource(R.string.set_infinite)
                    else stringResource(R.string.set_minutes_fmt, settings.stopAfterMin),
                    onClick = { autostopOpen = true },
                )
                SwitchRow(
                    title = stringResource(R.string.set_low_battery),
                    checked = settings.stopOnLowBattery,
                ) { scope.launch { repo.setStopOnLowBattery(it) } }
                SwitchRow(
                    title = stringResource(R.string.set_low_storage),
                    checked = settings.stopOnLowStorage,
                ) { scope.launch { repo.setStopOnLowStorage(it) } }
                SliderRow(
                    title = stringResource(R.string.set_initial_delay),
                    valueText = if (settings.initialDelayMs >= 1000)
                        stringResource(R.string.set_sec_fmt, settings.initialDelayMs / 1000)
                    else stringResource(R.string.set_ms_fmt, settings.initialDelayMs),
                    value = settings.initialDelayMs.toFloat(),
                    valueRange = 0f..10000f,
                    steps = 9,
                ) { scope.launch { repo.setInitialDelay(((it + 250) / 500) * 500) } }
            }

            // -------------------------------------------------- advanced
            SectionCard(
                title = stringResource(R.string.set_advanced),
                trailing = {
                    Icon(
                        if (advancedOpen) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        null, tint = TextDim, modifier = Modifier.size(18.dp),
                    )
                },
                onHeaderClick = { advancedOpen = !advancedOpen },
            ) {
                if (advancedOpen) {
                    SliderRow(
                        title = stringResource(R.string.set_cam_init_delay),
                        valueText = stringResource(R.string.set_ms_fmt, settings.cameraInitDelayMs),
                        value = settings.cameraInitDelayMs.toFloat(),
                        valueRange = 0f..2000f,
                        steps = 19,
                    ) { scope.launch { repo.setCameraInitDelay(((it + 50) / 100) * 100) } }
                    SliderRow(
                        title = stringResource(R.string.set_trigger_delay),
                        valueText = stringResource(R.string.set_ms_fmt, settings.triggerDelayMs),
                        value = settings.triggerDelayMs.toFloat(),
                        valueRange = 0f..2000f,
                        steps = 19,
                    ) { scope.launch { repo.setTriggerDelay(((it + 50) / 100) * 100) } }
                    SliderRow(
                        title = stringResource(R.string.set_jpeg_quality),
                        valueText = "${settings.jpegQuality}%",
                        value = settings.jpegQuality.toFloat(),
                        valueRange = 50f..100f,
                        steps = 9,
                    ) { scope.launch { repo.setJpegQuality(((it + 2) / 5) * 5) } }
                    SettingRow(
                        title = stringResource(R.string.set_video_bitrate),
                        value = if (settings.videoBitrate == 0) stringResource(R.string.set_bitrate_auto)
                        else stringResource(R.string.set_mbps_fmt, settings.videoBitrate / 1_000_000),
                        onClick = { bitrateOpen = true },
                    )
                }
            }

            // ----------------------------------------------------- remote
            SectionCard(stringResource(R.string.set_remote_section)) {
                SwitchRow(
                    title = stringResource(R.string.set_rest),
                    subtitle = stringResource(R.string.set_rest_hint),
                    checked = settings.restEnabled,
                ) { scope.launch { repo.setRestEnabled(it) } }
                SettingRow(
                    title = stringResource(R.string.set_port),
                    value = settings.restPort.toString(),
                    onClick = { portOpen = true },
                )
                SettingRow(
                    title = stringResource(R.string.set_connection),
                    value = "http://${RestServer.localIp() ?: "?.?.?.?"}:${settings.restPort}",
                )
            }

            // ------------------------------------------------------ about
            SectionCard(stringResource(R.string.set_about_section)) {
                SettingRow(title = stringResource(R.string.set_version), value = BuildConfig.VERSION_NAME)
                SettingRow(
                    title = stringResource(R.string.set_github),
                    value = "github.com/Sun-Chenyang/ChronoLapse",
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sun-Chenyang/ChronoLapse"))
                        )
                    },
                )
                SettingRow(
                    title = stringResource(R.string.set_license),
                    value = "GPL-3.0",
                    onClick = { licenseOpen = true },
                )
            }
        }
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
                        com.chronolapse.app.service.RecordingController.start(context)
                    } else {
                        com.chronolapse.app.service.RecordingController.stop(context)
                    }
                }
            },
        )
    }

    if (autostopOpen) {
        ChoiceDialog(stringResource(R.string.set_autostop)) { close ->
            autostopOpen = false
            close?.let { min ->
                scope.launch { repo.setStopAfterMin(min) }
            }
        }
    }

    if (bitrateOpen) {
        BitrateDialog { chosen ->
            bitrateOpen = false
            chosen?.let { scope.launch { repo.setVideoBitrate(it) } }
        }
    }

    if (portOpen) {
        PortDialog(settings.restPort) { newPort ->
            portOpen = false
            newPort?.let { scope.launch { repo.setRestPort(it) } }
        }
    }

    if (licenseOpen) {
        AlertDialog(
            onDismissRequest = { licenseOpen = false },
            containerColor = com.chronolapse.app.ui.theme.InkSurface,
            title = { Text(stringResource(R.string.set_license), color = TextPrimary) },
            text = {
                Text(stringResource(R.string.set_license_text), color = TextDim, fontSize = 13.sp)
            },
            confirmButton = {
                TextButton(onClick = { licenseOpen = false }) {
                    Text(stringResource(R.string.schedule_cancel), color = Amber)
                }
            },
        )
    }
}

private fun cameraLabel(index: Int, count: Int, back: String, front: String): String =
    "$index · ${if (index < count / 2) back else front}"

/** simple single choice list rendered as chips */
@Composable
private fun ChoiceDialog(title: String, onPick: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onPick(null) },
        containerColor = com.chronolapse.app.ui.theme.InkSurface,
        title = { Text(title, color = TextPrimary, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    15 to stringResource(R.string.set_minutes_fmt, 15),
                    30 to stringResource(R.string.set_minutes_fmt, 30),
                    60 to stringResource(R.string.set_minutes_fmt, 60),
                    120 to stringResource(R.string.set_hours_fmt, 2),
                    360 to stringResource(R.string.set_hours_fmt, 6),
                    720 to stringResource(R.string.set_hours_fmt, 12),
                    AppSettings.AUTO_STOP_MAX to stringResource(R.string.set_infinite),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.take(4).forEach { (min, label) ->
                            ParamChip(label, false) { onPick(min) }
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.drop(4).forEach { (min, label) ->
                            ParamChip(label, false) { onPick(min) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onPick(null) }) {
                Text(stringResource(R.string.schedule_cancel), color = TextDim)
            }
        },
    )
}

@Composable
private fun BitrateDialog(onPick: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onPick(null) },
        containerColor = com.chronolapse.app.ui.theme.InkSurface,
        title = { Text(stringResource(R.string.set_video_bitrate), color = TextPrimary, fontSize = 16.sp) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to stringResource(R.string.set_bitrate_auto), 4_000_000 to "4 Mbps", 8_000_000 to "8 Mbps", 16_000_000 to "16 Mbps")
                    .forEach { (bps, label) ->
                        ParamChip(label, false) { onPick(bps) }
                    }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { onPick(null) }) {
                Text(stringResource(R.string.schedule_cancel), color = TextDim)
            }
        },
    )
}

@Composable
private fun PortDialog(current: Int, onDone: (Int?) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = com.chronolapse.app.ui.theme.InkSurface,
        title = { Text(stringResource(R.string.set_port), color = TextPrimary, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { ch -> ch.isDigit() }.take(5) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = Amber,
                    unfocusedBorderColor = com.chronolapse.app.ui.theme.FaintStroke,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val port = text.toIntOrNull()
                if (port in 1024..65535) onDone(port!!)
            }) { Text(stringResource(R.string.schedule_save), color = Amber) }
        },
        dismissButton = {
            TextButton(onClick = { onDone(null) }) {
                Text(stringResource(R.string.schedule_cancel), color = TextDim)
            }
        },
    )
}
