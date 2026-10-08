package com.datathrottle.ui.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * "Obsidian" home surfaces: a near-black field with two slow blue glows drifting
 * from the lower-left corner, plus glass cards that blur the glow passing behind
 * them. The two periods (9 s and 12 s) match the reference design, so the waves
 * never visibly loop.
 */
object Obsidian {
    /** Base colour of the field. */
    val Base = Color(0xFF000000)

    /** Primary wave (reference: 50% 44% at 12% 88%, #3B82F6). */
    val WavePrimary = Color(0xFF3B82F6)

    /** Secondary wave (reference: 31% 26% at 10% 90%, #2563EB). */
    val WaveSecondary = Color(0xFF2563EB)

    val GlassFill = Color.White.copy(alpha = 0.025f)
    val GlassBorder = Color.White.copy(alpha = 0.08f)
    val GlassRadius: Dp = 16.dp

    /**
     * The home field is dark by design, so the home screen uses this scheme
     * regardless of the app theme (a light scheme would put dark text on black).
     */
    val colorScheme = darkColorScheme(
        primary = WavePrimary,
        onPrimary = Color.White,
        secondary = WaveSecondary,
        background = Base,
        onBackground = Color(0xFFF4F4F5),
        surface = Color(0xFF0A0A0A),
        onSurface = Color(0xFFF4F4F5),
        surfaceVariant = Color(0xFF121212),
        onSurfaceVariant = Color(0xFFA1A1AA),
        outline = Color(0xFF3F3F46)
    )
}

/**
 * The animated field. Drawn twice on the home screen: once full bleed, once
 * (blurred) inside the glass card with the layers aligned by window position.
 */
@Composable
fun ObsidianAmbientBackdrop(
    modifier: Modifier = Modifier,
    /** false for the copy inside a glass card: it must not repaint the black field. */
    paintBase: Boolean = true,
    onWindowPosition: (IntOffset) -> Unit = {}
) {
    val primary = rememberInfiniteTransition(label = "obsidian-primary")
    val pPhase by primary.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pPhase"
    )
    val pScaleX by primary.scaledKeyframes(9_000, listOf(1f to 0, 1.12f to 3_000, 0.95f to 6_000))
    val pScaleY by primary.scaledKeyframes(9_000, listOf(1f to 0, 0.94f to 3_000, 1.10f to 6_000))
    val pRotate by primary.scaledKeyframes(9_000, listOf(0f to 0, 3f to 3_000, -2f to 6_000))
    val pAlpha by primary.scaledKeyframes(9_000, listOf(1f to 0, 0.88f to 3_000, 0.95f to 6_000))

    val secondary = rememberInfiniteTransition(label = "obsidian-secondary")
    val sPhase by secondary.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sPhase"
    )
    val sScaleX by secondary.scaledKeyframes(12_000, listOf(1f to 0, 1.18f to 4_800, 0.92f to 9_000))
    val sScaleY by secondary.scaledKeyframes(12_000, listOf(1f to 0, 1.05f to 4_800, 1.14f to 9_000))
    val sRotate by secondary.scaledKeyframes(12_000, listOf(0f to 0, -4f to 4_800, 4f to 9_000))
    val sAlpha by secondary.scaledKeyframes(12_000, listOf(0.9f to 0, 1f to 4_800, 0.8f to 9_000))

    Box(
        modifier = modifier
            .then(if (paintBase) Modifier.background(Obsidian.Base) else Modifier)
            .onGloballyPositioned { onWindowPosition(windowOffsetOf(it.positionInWindow())) }
    ) {
        // Wave 1 — wide, slow swell. The gradient sits at 12%/88% of a 140% box
        // that starts at -20% (the reference's `inset: -20%`), i.e. just off the
        // left edge and just below the bottom, and the transform origin is that
        // box's lower-left corner — TransformOrigin(-0.2, 1.2) in layer units.
        WaveLayer(
            transformOrigin = TransformOrigin(0f, 1f),
            translateXFraction = primaryWaveX(pPhase),
            translateYFraction = primaryWaveY(pPhase),
            scaleX = pScaleX,
            scaleY = pScaleY,
            rotationZ = pRotate,
            alpha = pAlpha,
            brush = { w, h ->
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Obsidian.WavePrimary.copy(alpha = 0.17f),
                        0.45f to Obsidian.WavePrimary.copy(alpha = 0.06f),
                        1f to Color.Transparent
                    ),
                    center = Offset(w * (OVERSIZE * 0.12f + INSET_X), h * (OVERSIZE * 0.88f + INSET_Y)),
                    radius = maxOf(w * OVERSIZE * 0.5f, h * OVERSIZE * 0.44f)
                )
            }
        )
        // Wave 2 — tighter ripple on a 12 s period, so the pair never loops.
        WaveLayer(
            transformOrigin = TransformOrigin(0f, 1f),
            translateXFraction = secondaryWaveX(sPhase),
            translateYFraction = secondaryWaveY(sPhase),
            scaleX = sScaleX,
            scaleY = sScaleY,
            rotationZ = sRotate,
            alpha = sAlpha,
            brush = { w, h ->
                Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Obsidian.WaveSecondary.copy(alpha = 0.08f),
                        0.85f to Color.Transparent
                    ),
                    center = Offset(w * (OVERSIZE * 0.10f + INSET_X), h * (OVERSIZE * 0.90f + INSET_Y)),
                    radius = maxOf(w * OVERSIZE * 0.31f, h * OVERSIZE * 0.26f)
                )
            }
        )
    }
}

/** Reference `inset: -20%`: the gradient layers are 140% of the field. */
private const val OVERSIZE = 1.4f
private const val INSET_X = -0.2f
private const val INSET_Y = -0.2f
private const val INSET_Y_PLUS_ONE = 1.2f

/**
 * One wave: the gradient is painted in [drawBehind] (so it always resolves
 * against the real layer size, no measurement state) and the same node carries
 * the animated transform, exactly like a CSS keyframed ::before/::after.
 */
@Composable
private fun WaveLayer(
    transformOrigin: TransformOrigin,
    translateXFraction: Float,
    translateYFraction: Float,
    scaleX: Float,
    scaleY: Float,
    rotationZ: Float,
    alpha: Float,
    brush: (width: Float, height: Float) -> Brush
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = brush(size.width, size.height))
            }
            .graphicsLayer {
                this.transformOrigin = transformOrigin
                translationX = translateXFraction * size.width
                translationY = translateYFraction * size.height
                this.scaleX = scaleX
                this.scaleY = scaleY
                this.rotationZ = rotationZ
                this.alpha = alpha
            }
    )
}

/**
 * Keyframe animation with eased segments. Linear segments made the glow read as
 * a pulse; the reference design uses ease-in-out between stops.
 */
@Composable
private fun androidx.compose.animation.core.InfiniteTransition.scaledKeyframes(
    durationMs: Int,
    stops: List<Pair<Float, Int>>
) = animateFloat(
    initialValue = stops.first().first,
    targetValue = stops.first().first,
    animationSpec = infiniteRepeatable(
        animation = keyframes {
            durationMillis = durationMs
            stops.dropLast(1).forEachIndexed { index, (value, at) ->
                value at at using FastOutSlowInEasing
            }
            stops.last().let { (value, at) -> value at at using FastOutSlowInEasing }
        }
    ),
    label = "kf$durationMs"
)

/**
 * Glass panel: hairline border, translucent fill, and a blurred copy of the
 * ambient field aligned to the window so the wave is visible through the glass.
 *
 * @param fieldAlignment `cardPositionInWindow - backdropPositionInWindow`, so the
 *   inner copy paints the same part of the field as the one behind the screen.
 */
@Composable
fun ObsidianGlassCard(
    modifier: Modifier = Modifier,
    fieldAlignment: IntOffset = IntOffset.Zero,
    contentPadding: Dp = 32.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(Obsidian.GlassRadius)
    // The blur needs samples outside the panel, so the inner copy overhangs it.
    val bleed = 72.dp
    // Sized from the card's own measurement: the card may sit in a scroll
    // container (unbounded height), where constraints cannot be widened.
    val bleedPx = with(LocalDensity.current) { bleed.roundToPx() }

    Box(
        modifier = modifier
            .clip(shape)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    ) {
        // Blurred copy of the field: aligned to the window and overhanging the
        // panel (so the blur has samples outside the rounded corners), but it
        // reports the panel's own size so it never stretches the card. The blur
        // is applied to this layer *before* the parent clips it, which is the
        // Compose equivalent of CSS backdrop-filter.
        ObsidianAmbientBackdrop(
            modifier = Modifier
                .fillMaxSize()
                .layout { measurable, constraints ->
                    val w = if (constraints.hasBoundedWidth) constraints.maxWidth else 0
                    val h = if (constraints.hasBoundedHeight) constraints.maxHeight else 0
                    val extra = bleedPx * 2
                    val placeable = measurable.measure(
                        Constraints.fixed(
                            (w + extra).coerceAtLeast(1),
                            (h + extra).coerceAtLeast(1)
                        )
                    )
                    layout(w, h) { placeable.place(-extra, -extra) }
                }
                .offset { fieldAlignment - IntOffset(bleedPx, bleedPx) }
                .blur(28.dp),
            paintBase = false
        )
        Box(modifier = Modifier.fillMaxSize().background(Obsidian.GlassFill))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
        Box(modifier = Modifier.fillMaxSize().border(1.dp, Obsidian.GlassBorder, shape))
    }
}

/** Reference keyframes, linearly interpolated between the three stops. */
private fun primaryWaveX(t: Float): Float = piecewise(t, 0f, 6f, -4f)
private fun primaryWaveY(t: Float): Float = piecewise(t, 0f, -5f, 4f)
private fun secondaryWaveX(t: Float): Float = piecewise(t, 0f, 10f, -6f, 0.4f, 0.75f)
private fun secondaryWaveY(t: Float): Float = piecewise(t, 0f, -8f, -3f, 0.4f, 0.75f)

private fun piecewise(
    t: Float,
    v0: Float,
    v1: Float,
    v2: Float,
    firstStop: Float = 0.3333f,
    secondStop: Float = 0.6666f
): Float = when {
    t < firstStop -> lerpStep(v0, v1, t / firstStop)
    t < secondStop -> lerpStep(v1, v2, (t - firstStop) / (secondStop - firstStop))
    else -> lerpStep(v2, v0, (t - secondStop) / (1f - secondStop))
}

private fun lerpStep(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction.coerceIn(0f, 1f)

/** Window position of a layout node as an [IntOffset]. */
fun windowOffsetOf(position: Offset): IntOffset =
    IntOffset(position.x.roundToInt(), position.y.roundToInt())
