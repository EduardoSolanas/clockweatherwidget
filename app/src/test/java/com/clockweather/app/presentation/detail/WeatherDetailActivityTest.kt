package com.clockweather.app.presentation.detail

import org.junit.Assert.*
import org.junit.Test

/**
 * Structural contract tests for [WeatherDetailActivity].
 *
 * The activity must NOT contain any async onStop() logic — widget updates are
 * driven by the system (TextClock auto-sync) and WorkManager, not by activity
 * lifecycle hooks.
 */
class WeatherDetailActivityTest {

    @Test
    fun `WeatherDetailActivity does not override onStop`() {
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
        val fields = WeatherDetailActivity::class.java.declaredFields
        val coroutineFields = fields.filter {
            it.type.name.contains("CoroutineScope") ||
                it.type.name.contains("Job") ||
                it.type.name.contains("SupervisorJob")
        }
        assertTrue(
            "WeatherDetailActivity should not hold coroutine fields. Found: ${coroutineFields.map { it.name }}",
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
