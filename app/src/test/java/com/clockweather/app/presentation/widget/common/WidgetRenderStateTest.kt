package com.clockweather.app.presentation.widget.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Queue step 4 of docs/TIME_WEATHER_SYNC_REVIEW.md.
 *
 * onUpdate pushes an unbound placeholder layout to every widget before it reads the cache,
 * which blanks widgets that were already showing weather. The placeholder exists because a
 * widget with no content at all shows "Can't load widget" on some launchers, so it cannot
 * simply be deleted — it has to be limited to widgets that have nothing to preserve.
 *
 * Whether a widget is already showing something has to survive process death: the launcher
 * keeps displaying the last published RemoteViews long after the process that sent them is
 * gone, so an in-memory flag would claim "nothing rendered" and blank a populated widget on
 * the first callback after every restart.
 */
@RunWith(RobolectricTestRunner::class)
class WidgetRenderStateTest {

    private val context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `a widget that has never been rendered needs the placeholder`() {
        assertTrue(WidgetRenderState.needsPlaceholder(context, appWidgetId = 41))
    }

    @Test
    fun `a widget that has published content does not need the placeholder`() {
        WidgetRenderState.markRendered(context, appWidgetId = 42)

        assertFalse(
            "a populated widget must keep its content through a routine refresh",
            WidgetRenderState.needsPlaceholder(context, appWidgetId = 42)
        )
    }

    @Test
    fun `each widget is tracked separately`() {
        WidgetRenderState.markRendered(context, appWidgetId = 43)

        assertTrue(WidgetRenderState.needsPlaceholder(context, appWidgetId = 44))
    }

    /** A removed widget leaves no content behind, so a reused id must start over. */
    @Test
    fun `a removed widget needs the placeholder again`() {
        WidgetRenderState.markRendered(context, appWidgetId = 45)
        WidgetRenderState.clear(context, appWidgetId = 45)

        assertTrue(WidgetRenderState.needsPlaceholder(context, appWidgetId = 45))
    }

    /**
     * The record has to outlive the process. Robolectric keeps one backing store per test, so
     * reading through a second call path stands in for the restart the launcher survives.
     */
    @Test
    fun `the record is persistent rather than in-memory`() {
        WidgetRenderState.markRendered(context, appWidgetId = 46)

        val stored = context
            .getSharedPreferences(WidgetRenderState.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
            .all
            .keys

        assertTrue(
            "the marker must be written to persistent storage, not held in the provider",
            stored.any { it.contains("46") }
        )
    }
}
