package com.datathrottle.ui.home.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
    val thumbOffset by animateDpAsState(
        targetValue = if (isRunning) maxOffset else padding,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "thumbOffset"
    )

    val backgroundBrush = if (isRunning) {
        Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF2563EB), // Royal Blue
                Color(0xFF7C3AED)  // Electric Violet
            )
        )
    } else {
        if (isDark) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF334155), // Dark Slate Gray
                    Color(0xFF1E293B)
                )
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFE2E8F0), // Light Cool Gray
                    Color(0xFFCBD5E1)
                )
            )
        }
    }

    Box(
        modifier = modifier
            .width(switchWidth)
            .height(switchHeight)
            .clip(CircleShape)
            .background(backgroundBrush)
            .testTag("service_toggle")
            .semantics { contentDescription = "Service Toggle" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle(!isRunning)
            },
        contentAlignment = Alignment.CenterStart
    ) {
        // Glowing animated Thumb
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(elevation = if (isRunning) 10.dp else 4.dp, shape = CircleShape)
                .background(Color.White, CircleShape)
        )
    }
}
