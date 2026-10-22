package com.chronolapse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chronolapse.app.R
import com.chronolapse.app.ui.theme.Amber
import com.chronolapse.app.ui.theme.InkSurface
import com.chronolapse.app.ui.theme.TextDim
import com.chronolapse.app.ui.theme.TextPrimary
import java.util.Calendar

/**
 * Schedule editor shared by the camera sheet and the settings screen:
 * enable switch + date picker + time picker, saved as one timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDialog(
    initialEnabled: Boolean,
    initialTimeMs: Long,
    onSave: (enabled: Boolean, timeMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val cal = Calendar.getInstance().apply {
        if (initialEnabled && initialTimeMs > System.currentTimeMillis()) {
            timeInMillis = initialTimeMs
        } else {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.HOUR_OF_DAY, 1)
        }
    }
    var enabled by remember { mutableStateOf(initialEnabled && initialTimeMs > System.currentTimeMillis()) }

    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = cal.timeInMillis,
    )
    val timeState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
        is24Hour = true,
    )

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(InkSurface)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.schedule_title), color = TextPrimary, fontSize = 17.sp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.schedule_enable),
                    color = if (enabled) TextPrimary else TextDim,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Amber,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }

            if (enabled) {
                DatePicker(
                    state = dateState,
                    showModeToggle = false,
                    title = null,
                    headline = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                )
                Spacer(Modifier.height(4.dp))
                TimePicker(
                    state = timeState,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.schedule_cancel),
                    color = TextDim,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.schedule_save),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Amber)
                        .clickable {
                            val picked = dateState.selectedDateMillis ?: cal.timeInMillis
                            val out = Calendar.getInstance().apply {
                                timeInMillis = picked
                                set(Calendar.HOUR_OF_DAY, timeState.hour)
                                set(Calendar.MINUTE, timeState.minute)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }
                            onSave(enabled, out.timeInMillis)
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}
