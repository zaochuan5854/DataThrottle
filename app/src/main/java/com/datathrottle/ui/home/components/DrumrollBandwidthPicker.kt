package com.datathrottle.ui.home.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datathrottle.R
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrumrollBandwidthPicker(
    currentLimit: Float,
    onUpdateLimit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = remember {
        val list = mutableListOf<Float>()
        // 1Mbps以下: 0.1刻み (0.1 ~ 1.0)
        for (i in 1..10) {
            list.add(Math.round(i * 0.1f * 10f) / 10f)
        }
        // 10Mbps以下: 0.5刻み (1.5 ~ 10.0)
        for (i in 3..20) {
            list.add(Math.round(i * 0.5f * 10f) / 10f)
        }
        // 20Mbps以下: 1.0刻み (11.0 ~ 20.0)
        for (i in 11..50) {
            list.add(i.toFloat())
        }
        list.distinct()
    }

    val itemHeight = 92.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    val initialIndex = remember {
        val idx = items.indexOfFirst { kotlin.math.abs(it - currentLimit) < 0.05f }
        if (idx >= 0) idx else 9
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index in items.indices) {
                    onUpdateLimit(items[index])
                }
            }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.label_bandwidth_limit),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(2.dp))

        Box(
            modifier = Modifier
                .height(itemHeight)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .testTag("bandwidth_picker_list")
                    .semantics { contentDescription = "Bandwidth Picker" }
            ) {
                itemsIndexed(items) { index, value ->
                    val layoutInfo = listState.layoutInfo
                    val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                    val offset = visibleItem?.let {
                        val itemCenter = it.offset + it.size / 2f
                        val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                        (itemCenter - viewportCenter) / itemHeightPx
                    } ?: 0f

                    val distance = kotlin.math.abs(offset)
                    val alpha = (1f - (distance * 2.5f)).coerceIn(0f, 1f)
                    val scale = (1.15f - (distance * 0.3f)).coerceIn(0.85f, 1.15f)
                    val rotationX = offset * -30f

                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                                this.rotationX = rotationX
                                cameraDistance = 16f * density
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            // Invisible balance text to ensure the number's center is exactly at the screen center
                            Text(
                                text = "Mbps",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = Color.Transparent,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (value < 1.0f) String.format("%.1f", value) else if (value % 1.0f == 0f) String.format("%.0f", value) else String.format("%.1f", value),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 90.sp,
                                    letterSpacing = (-2).sp,
                                    lineHeight = 90.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Mbps",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                ),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
