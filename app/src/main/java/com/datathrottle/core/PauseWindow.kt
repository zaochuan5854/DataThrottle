package com.datathrottle.core

/**
 * State machine for the notification's 60 s "unlimit" window.
 *
 * The window is driven by a coroutine in the service, but its *contract* lives
 * here so it is testable without Android:
 *
 *  - every [start] hands out a token;
 *  - the countdown coroutine may only restore the cap while its token is still
 *    current ([isCurrent]); [stop] invalidates every outstanding token.
 *
 * That last rule is the S1-16 fix: when the service is torn down inside the
 * window the countdown must die *without* re-applying a cap, otherwise a
 * kernel limit survives a service that no longer exists.
 */
class PauseWindow(
    private val durationMs: Long,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    // Written from the service's IO scope, read when the notification is
    // rebuilt on the main thread -> keep the handoff explicit.
    @Volatile private var token: Long = INACTIVE
    @Volatile private var startedAtMs: Long = 0L

    val isActive: Boolean get() = token != INACTIVE

    /** Window length in whole seconds (what the notification counts down). */
    val durationSeconds: Int get() = (durationMs / 1000L).toInt()

    /** Begin a window and return its token. Supersedes any window already open. */
    fun start(): Long {
        token = if (token == Long.MAX_VALUE) 1L else token + 1L
        startedAtMs = clock()
        return token
    }

    /** Seconds left, rounded up so a fresh window reads as [durationSeconds]. */
    fun remainingSeconds(): Int {
        if (!isActive) return 0
        val left = durationMs - (clock() - startedAtMs)
        if (left <= 0L) return 0
        return ((left + 999L) / 1000L).toInt()
    }

    /** True once the window is spent (or was stopped). */
    fun isElapsed(): Boolean = remainingSeconds() <= 0

    /** Only the coroutine holding this token may restore the cap. */
    fun isCurrent(token: Long): Boolean = isActive && this.token == token

    /** End the window without touching the cap; invalidates every token. */
    fun stop() {
        token = INACTIVE
    }

    private companion object {
        const val INACTIVE = 0L
    }
}
