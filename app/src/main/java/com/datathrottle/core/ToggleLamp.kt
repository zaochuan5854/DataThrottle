package com.datathrottle.core

/**
 * Timing contract of the home switch ("lamp") animation.
 *
 * Switching **on** ramps slowly: arming a throttle is a deliberate act and the
 * delay reads as intentional. Switching **off** fades faster, but still eased,
 * so it reads as a fade rather than a cut. The asymmetry is easy to lose in a
 * refactor (one shared `tween` is the obvious implementation), so the numbers
 * live here and are unit tested.
 */
object ToggleLamp {

    const val LIGHT_UP_MS = 900
    const val DIM_DOWN_MS = 380

    /** Duration of the ramp towards [lit]. */
    fun durationMs(lit: Boolean): Int = if (lit) LIGHT_UP_MS else DIM_DOWN_MS

    /** Linear position of the knob for a ramp progress in 0..1. */
    fun knobFraction(lit: Float): Float = lit.coerceIn(0f, 1f)
}
