package com.datathrottle.ui.home.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.datathrottle.core.ToggleLamp

/**
 * The service switch, lit like a lamp: switching on ramps the track up slowly
 * (the throttle is being armed, so the delay reads as intentional), switching
 * off fades it out faster. A single [progress] value (0 = off, 1 = on) drives
 * the thumb position, the track gradient, the glow and the shadow so every part
 * moves together.
 */
@Composable
fun GiantToggleSwitch(
    isRunning: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()
    val switchWidth = 150.dp
    val switchHeight = 78.dp
    val thumbSize = 64.dp
    val padding = 7.dp

    val maxOffset = switchWidth - thumbSize - padding

    val progress = remember { Animatable(if (isRunning) 1f else 0f) }
    LaunchedEffect(isRunning) {
        progress.animateTo(
            targetValue = if (isRunning) 1f else 0f,
            animationSpec = tween(
                durationMillis = ToggleLamp.durationMs(isRunning),
                easing = FastOutSlowInEasing
            )
        )
    }
    val lit = progress.value

    // Off palette: dark slate in dark mode, cool grey otherwise.
    val offStart = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val offEnd = if (isDark) Color(0xFF1E293B) else Color(0xFFCBD5E1)
    val onStart = Color(0xFF2563EB) // Royal Blue
    val onEnd = Color(0xFF7C3AED)   // Electric Violet

    val trackBrush = Brush.horizontalGradient(
        listOf(
            blend(offStart, onStart, lit),
            blend(offEnd, onEnd, lit)
        )
    )
    val thumbOffset = padding + (maxOffset - padding) * lit

    Box(
        modifier = modifier
            .width(switchWidth)
            .height(switchHeight),
        contentAlignment = Alignment.CenterStart
    ) {
        // Lamp glow: grows with the light-up and collapses faster on the way off.
        if (lit > 0.01f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .alpha(lit * 0.55f)
                    .blur(18.dp)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(onStart, onEnd)))
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(trackBrush)
                .testTag("service_toggle")
                .semantics { contentDescription = "Service Toggle" }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle(!isRunning)
                }
        )
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(elevation = (4f + 6f * lit).dp, shape = CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}

/** Colour interpolation towards the lit colour as the switch ramps up. */
private fun blend(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction
)
