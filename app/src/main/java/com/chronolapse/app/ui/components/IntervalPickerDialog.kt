package com.chronolapse.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chronolapse.app.R
import com.chronolapse.app.ui.theme.Amber
import com.chronolapse.app.ui.theme.GlassFill
import com.chronolapse.app.ui.theme.InkSurface
import com.chronolapse.app.ui.theme.TextDim
import com.chronolapse.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Free-form capture interval with three scroll wheels (h / m / s),
 * 1s to 23:59:59. The value centered in each wheel is the selection;
 * tap an off-center value to scroll it into place. Invisible padding
 * items at both ends let the first/last values reach the center.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun IntervalPickerDialog(
    initialMs: Long,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val hours = remember { (0..23).toList() }
    val minutes = remember { (0..59).toList() }
    val seconds = remember { (0..59).toList() }

    val rowHeight = 40.dp
    val wheelHeight = 200.dp
    val pad = 2 // padding rows on each side, = (wheel/row - 1) / 2

    val initialH = (initialMs / 3_600_000L).toInt()
    val initialM = ((initialMs % 3_600_000L) / 60_000L).toInt()
    val initialS = ((initialMs % 60_000L) / 1_000L).toInt()

    val hState = rememberLazyListState(initialFirstVisibleItemIndex = initialH + pad)
    val mState = rememberLazyListState(initialFirstVisibleItemIndex = initialM + pad)
    val sState = rememberLazyListState(initialFirstVisibleItemIndex = initialS + pad)

    val density = LocalDensity.current
    val centerOffsetPx = with(density) { ((wheelHeight - rowHeight) / 2).toPx().toInt() }

    // center the initial values on first layout; the negative offset places
    // the item's top below the viewport top and the padding rows cover the
    // gap above
    LaunchedEffect(Unit) {
        hState.scrollToItem(initialH + pad, -centerOffsetPx)
        mState.scrollToItem(initialM + pad, -centerOffsetPx)
        sState.scrollToItem(initialS + pad, -centerOffsetPx)
    }

    val h by remember { derivedStateOf { hours[pickedIndex(hState, pad, hours.lastIndex)] } }
    val m by remember { derivedStateOf { minutes[pickedIndex(mState, pad, minutes.lastIndex)] } }
    val s by remember { derivedStateOf { seconds[pickedIndex(sState, pad, seconds.lastIndex)] } }
    val totalMs = h * 3_600_000L + m * 60_000L + s * 1_000L

    val scope = rememberCoroutineScope()

    fun center(state: LazyListState, list: List<Int>, value: Int) {
        scope.launch {
            state.animateScrollToItem(list.indexOf(value) + pad, -centerOffsetPx)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(InkSurface)
                .padding(20.dp),
        ) {
            Text(stringResource(R.string.sheet_interval), color = TextPrimary, fontSize = 17.sp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                // center highlight band
                Box(
                    Modifier
                        .matchParentSize()
                        .padding(vertical = (wheelHeight - rowHeight) / 2)
                        .clip(RoundedCornerShape(10.dp))
                        .background(GlassFill)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Wheel(hours, hState, pad, rowHeight, wheelHeight) { center(hState, hours, it) }
                    Wheel(minutes, mState, pad, rowHeight, wheelHeight) { center(mState, minutes, it) }
                    Wheel(seconds, sState, pad, rowHeight, wheelHeight) { center(sState, seconds, it) }
                }

                // fade top and bottom edges
                Column(Modifier.matchParentSize()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(InkSurface.copy(alpha = 0.95f), Color.Transparent)
                                )
                            )
                    )
                    Spacer(Modifier.height(80.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, InkSurface.copy(alpha = 0.95f))
                                )
                            )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(stringResource(R.string.interval_hour), color = TextDim, fontSize = 11.sp,
                    modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                Text(stringResource(R.string.interval_minute), color = TextDim, fontSize = 11.sp,
                    modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
                Text(stringResource(R.string.interval_second), color = TextDim, fontSize = 11.sp,
                    modifier = Modifier.width(72.dp), textAlign = TextAlign.Center)
            }

            Text(
                "= " + com.chronolapse.app.ui.fmtDuration(totalMs),
                color = Amber,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 6.dp),
            )

            Spacer(Modifier.height(16.dp))
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
                    color = if (totalMs >= 1_000) MaterialTheme.colorScheme.onPrimary else TextDim,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (totalMs >= 1_000) Amber else GlassFill)
                        .clickable(enabled = totalMs >= 1_000) { onSave(totalMs) }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** real index selected by the wheel: centered padded index minus padding */
private fun pickedIndex(state: LazyListState, pad: Int, max: Int): Int {
    val info = state.layoutInfo
    if (info.visibleItemsInfo.isEmpty()) return 0
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    val padded = info.visibleItemsInfo
        .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
        ?.index ?: pad
    return (padded - pad).coerceIn(0, max)
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Wheel(
    values: List<Int>,
    state: LazyListState,
    pad: Int,
    rowHeight: Dp,
    wheelHeight: Dp,
    onCenter: (Int) -> Unit,
) {
    val padded = remember(values, pad) {
        List(pad) { -1 } + values + List(pad) { -1 }
    }
    val centeredPadded by remember {
        derivedStateOf {
            val info = state.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf pad
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo
                .minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }
                ?.index ?: pad
        }
    }
    val selectedReal by remember {
        derivedStateOf { values[(centeredPadded - pad).coerceIn(0, values.lastIndex)] }
    }

    // subtle tick when the centered value changes while scrolling
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var hapticArmed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(selectedReal) {
        if (hapticArmed) haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
        hapticArmed = true
    }

    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
        modifier = Modifier
            .height(wheelHeight)
            .width(72.dp),
    ) {
        itemsIndexed(padded) { index, value ->
            val real = value - 0
            val centered = value >= 0 && value == selectedReal
            Box(
                modifier = Modifier
                    .height(rowHeight)
                    .clickable(enabled = value >= 0) { onCenter(real) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (value >= 0) "$value" else "",
                    color = if (centered) TextPrimary else TextDim,
                    fontSize = if (centered) 24.sp else 16.sp,
                    fontWeight = if (centered) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}
