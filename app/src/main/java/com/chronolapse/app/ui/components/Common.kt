package com.chronolapse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chronolapse.app.ui.theme.Amber
import com.chronolapse.app.ui.theme.FaintStroke
import com.chronolapse.app.ui.theme.GlassFill
import com.chronolapse.app.ui.theme.InkGlass
import com.chronolapse.app.ui.theme.InkSurface
import com.chronolapse.app.ui.theme.TextDim
import com.chronolapse.app.ui.theme.TextPrimary

/** translucent status chip on the camera viewfinder; [on] switches to accent.
 *  All chips share one height, icon-only chips are square. */
@Composable
fun GlassChip(
    text: String?,
    icon: ImageVector?,
    on: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String = text ?: "",
    fixedWidth: Dp? = null,
    onClick: (() -> Unit)? = null,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val bg = if (on) MaterialTheme.colorScheme.primaryContainer else InkGlass
    val border = if (on) Amber.copy(alpha = 0.55f) else FaintStroke
    val tint = if (on) Amber else TextDim
    val square = text == null && fixedWidth == null
    Row(
        modifier = modifier
            .height(30.dp)
            .then(
                when {
                    fixedWidth != null -> Modifier.width(fixedWidth)
                    square -> Modifier.width(30.dp)
                    else -> Modifier
                }
            )
            .clip(RoundedCornerShape(15.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(15.dp))
            .clickable(enabled = onClick != null) {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick?.invoke()
            }
            .padding(horizontal = if (text != null && fixedWidth == null) 10.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (text == null || fixedWidth != null) Arrangement.Center else Arrangement.Start,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
        }
        if (text != null) {
            Text(
                text,
                color = tint,
                fontSize = 11.sp,
                modifier = Modifier.padding(start = if (icon != null) 5.dp else 0.dp),
                maxLines = 1,
            )
        }
    }
}

/** selectable parameter chip used in the capture sheet and filters */
@Composable
fun ParamChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Amber else GlassFill)
            .clickable {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 11.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

/** rounded card wrapping one settings section */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    onHeaderClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(InkSurface)
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let { m -> if (onHeaderClick != null) m.clickable { onHeaderClick() } else m }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Amber, fontSize = 12.sp, modifier = Modifier.weight(1f))
            if (trailing != null) trailing()
        }
        content()
    }
}

@Composable
fun SettingRow(
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            if (!value.isNullOrEmpty()) {
                Text(value, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun SwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Boolean) -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    SettingRow(
        title = title,
        value = subtitle,
        modifier = modifier,
        onClick = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            onChange(!checked)
        },
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Amber,
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    }
}

@Composable
fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            Text(valueText, color = Amber, fontSize = 13.sp)
        }
        Slider(
            value = value,
            onValueChange = { onChange(it.toInt()) },
            valueRange = valueRange,
            steps = steps,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = TextPrimary,
                activeTrackColor = Amber,
                inactiveTrackColor = GlassFill,
            ),
        )
    }
}

/** small accent dot used as a recording indicator */
@Composable
fun RecDot(modifier: Modifier = Modifier, color: Color = com.chronolapse.app.ui.theme.RecRed) {
    Box(modifier.size(8.dp).clip(CircleShape).background(color))
}

/** thin 3dp accent underline */
@Composable
fun ModeUnderline(visible: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(top = 2.dp)
            .width(16.dp)
            .height(3.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (visible) Amber else Color.Transparent)
    )
}
