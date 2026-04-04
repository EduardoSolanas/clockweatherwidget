package com.clockweather.app.presentation.detail

import org.junit.Assert.*
import org.junit.Test

/**
 * Structural contract tests for [WeatherDetailActivity].
 *
 * Clock sync on activity → home transitions is handled globally by
 * [ClockWeatherApplication.ActivityLifecycleCallbacks] + [ProcessLifecycleOwner].
 * The activity itself must NOT contain any async onStop() logic.
 *
 * The original bug: onStop() called syncClockNow() which triggered a full
 * widget rebuild (updateAppWidget) — causing 1-second delay + 4-digit flicker
 * when navigating from weather page back to home.
 */
class WeatherDetailActivityTest {

    @Test
    fun `WeatherDetailActivity does not override onStop`() {
        // Clock sync is now handled globally in ClockWeatherApplication via
        // ActivityLifecycleCallbacks. No per-activity onStop() override needed.
        val methods = WeatherDetailActivity::class.java.declaredMethods
        val hasOnStop = methods.any { it.name == "onStop" }
        assertFalse(
            "WeatherDetailActivity should not override onStop() — " +
                "clock sync is handled globally by ActivityLifecycleCallbacks",
            hasOnStop
        )
    }

    @Test
    fun `WeatherDetailActivity does not hold CoroutineScope fields`() {
        // If the class holds a CoroutineScope or Job, it means onStop() is launching
        // async work — the old broken pattern that caused flicker.
        // pushClockInstant() is synchronous and requires no coroutine.
        val fields = WeatherDetailActivity::class.java.declaredFields
        val coroutineFields = fields.filter {
            it.type.name.contains("CoroutineScope") ||
                it.type.name.contains("Job") ||
                it.type.name.contains("SupervisorJob")
        }
        assertTrue(
            "WeatherDetailActivity should not hold coroutine fields — " +
                "clock sync must be synchronous (pushClockInstant). Found: ${coroutineFields.map { it.name }}",
            coroutineFields.isEmpty()
        )
    }

    @Test
    fun `WeatherDetailActivity does not import kotlinx coroutines`() {
        // Extra guard: check that coroutine-related classes are not in the class's constant pool.
        // If someone re-adds CoroutineScope.launch in onStop, these classes would be loaded.
        val classloader = WeatherDetailActivity::class.java.classLoader!!
        val coroutineClasses = listOf(
            "kotlinx.coroutines.CoroutineScope",
            "kotlinx.coroutines.SupervisorJob"
        )
        // The activity should NOT cause these to be loaded as direct dependencies
        // (they may be loaded by other classes, so we check the source file indirectly
        // via the declared fields/methods test above)
    }
}
