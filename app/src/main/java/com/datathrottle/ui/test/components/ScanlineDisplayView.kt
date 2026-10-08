package com.datathrottle.ui.test.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.datathrottle.R
import com.datathrottle.core.TestState

@Composable
fun ScanlineDisplayView(
    state: TestState,
    modifier: Modifier = Modifier
) {
    val bitmap = state.imageBitmap
    val progress = state.progress

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F141C))
            .border(1.dp, Color(0xFF263238), RoundedCornerShape(12.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Background Grid Lines
            val gridSpacing = 24.dp.toPx()
            var y = gridSpacing
            while (y < canvasHeight) {
                drawLine(
                    color = Color(0xFF1E293B),
                    start = Offset(0f, y),
                    end = Offset(canvasWidth, y),
                    strokeWidth = 1f
                )
                y += gridSpacing
            }

            // Draw image clipped to progress
            if (bitmap != null && progress > 0f) {
                // A partial decode is a top band (full width × progress rows).
                // Recover the full-image aspect from it and anchor the band to
                // the top edge, so the reveal grows top-to-bottom like a real
                // scanline — fit-centering the band itself made it look like
                // the middle of the image was rendering outwards.
                val partial = state.partialImage
                val fullWidth = bitmap.width
                val fullHeight = if (partial && progress > 0f) {
                    (bitmap.height / progress).toInt().coerceAtLeast(bitmap.height)
                } else bitmap.height

                val scale = minOf(
                    canvasWidth / fullWidth.toFloat(),
                    canvasHeight / fullHeight.toFloat()
                )
                val dstWidth = fullWidth * scale
                val fullDstHeight = fullHeight * scale
                val bandDstHeight = if (partial) fullDstHeight * progress else fullDstHeight
                val dstX = (canvasWidth - dstWidth) / 2f
                val dstY = if (partial) 0f else (canvasHeight - fullDstHeight) / 2f

                drawImage(
                    image = bitmap,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
                    dstSize = IntSize(dstWidth.toInt(), bandDstHeight.toInt())
                )

                // Draw Scanline Laser Beam riding the band's bottom edge
                if (progress < 1.0f) {
                    val laserY = dstY + bandDstHeight
                    // Glow beam
                    drawLine(
                        color = Color(0x6600E5FF),
                        start = Offset(0f, laserY),
                        end = Offset(canvasWidth, laserY),
                        strokeWidth = 8f
                    )
                    // Sharp laser line
                    drawLine(
                        color = Color(0xFF00E5FF),
                        start = Offset(0f, laserY),
                        end = Offset(canvasWidth, laserY),
                        strokeWidth = 3f
                    )
                }
            }
        }

        // HUD Overlay Badges
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${(progress * 100).toInt()} %",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        ),
                        color = if (progress >= 1f) Color(0xFF00E676) else Color(0xFF00E5FF),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    color = Color(0xCC000000),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = stringResource(R.string.test_speed_format, state.currentSpeedKbps),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
