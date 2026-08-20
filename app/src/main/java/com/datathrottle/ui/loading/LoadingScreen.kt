package com.datathrottle.ui.loading

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.datathrottle.ui.theme.DataThrottleTheme
import kotlinx.coroutines.delay

/**
 * Minimal Loading Screen displaying only the animated speedometer icon (-100° to +60° in 1 second).
 */
@Composable
fun LoadingScreen(
    modifier: Modifier = Modifier,
    onLoadingComplete: () -> Unit = {}
) {
    val animAngle = remember { Animatable(-100f) }

    LaunchedEffect(Unit) {
        animAngle.animateTo(
            targetValue = 60f,
            animationSpec = tween(
                durationMillis = 700,
                easing = FastOutSlowInEasing
            )
        )
        delay(50)
        onLoadingComplete()
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            SpeedometerCanvas(
                logicalAngle = animAngle.value,
                modifier = Modifier.size(160.dp)
            )
        }
    }
}

@Composable
fun SpeedometerCanvas(
    logicalAngle: Float,
    modifier: Modifier = Modifier
) {
    val dialColor = Color(0xFFF97316)
    val needleColor = Color(0xFFF97316)

    // -45度のオフセット補正（anim_index.htmlの仕様）
    val appliedAngle = logicalAngle - 45f

    // SVG パスデータの定義 (viewBox: 0 0 24 24, ピボット: 12, 14)
    val dialPathString = "M 3.35,19 a 2,2 0,0 0,1.72,1 h 13.85 a 2,2 0,0 0,1.74,-1 A 10,10 0,1,0, 3.35,19 z M 5.07,18 A 8,8 0,1,1, 18.93,18 L 5.07,18 z"
    val needlePathString = "M10.59,15.41a2,2 0,0 0,2.83 0l5.66,-8.49 -8.49,5.66a2,2 0,0 0,0 2.83z"

    val dialPath = remember { PathParser().parsePathString(dialPathString).toPath() }
    val needlePath = remember { PathParser().parsePathString(needlePathString).toPath() }

    Canvas(modifier = modifier) {
        val scale = size.minDimension / 24f
        val pivotX = 12f * scale
        val pivotY = 14f * scale

        // 1. メーター外枠の描画（くり抜きマスク用レイヤー）
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                isAntiAlias = true
            }

            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)

            // メーター外枠描画
            val scaledDialPath = Path().apply {
                addPath(dialPath)
                transform(Matrix().apply { scale(scale, scale) })
            }
            drawPath(scaledDialPath, dialColor)

            // マスク用の円（Clearブレンドモードでくり抜き）
            val maskPaint = Paint().apply {
                blendMode = BlendMode.Clear
                isAntiAlias = true
            }

            canvas.save()
            canvas.translate(pivotX, pivotY)
            canvas.rotate(appliedAngle)
            canvas.translate(-pivotX, -pivotY)
            canvas.drawCircle(
                center = Offset(18.36f * scale, 7.64f * scale),
                radius = 1.8f * scale,
                paint = maskPaint
            )
            canvas.restore()

            canvas.restore()
        }

        // 2. 針の描画（ピボット中心で回転）
        rotate(degrees = appliedAngle, pivot = Offset(pivotX, pivotY)) {
            val scaledNeedlePath = Path().apply {
                addPath(needlePath)
                transform(Matrix().apply { scale(scale, scale) })
            }
            drawPath(scaledNeedlePath, needleColor)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoadingScreenPreview() {
    DataThrottleTheme(darkTheme = true) {
        LoadingScreen()
    }
}
