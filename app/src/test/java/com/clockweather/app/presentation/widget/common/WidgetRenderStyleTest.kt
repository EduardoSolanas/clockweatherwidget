package com.clockweather.app.presentation.widget.common

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clockweather.app.R
import com.clockweather.app.presentation.widget.compact.CompactWidgetProvider
import com.clockweather.app.presentation.widget.compact.CompactWidgetUpdater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Inflates the RemoteViews the widget actually ships and asserts the rendered view tree.
 *
 * Every other widget test stops at the RemoteViews object: it checks the actions queued onto it,
 * or the size it parcels to. None of them apply it, so none can tell a bound widget from the raw
 * layout. v1.0.191 shipped exactly that failure -- the process died before any binding ran, the
 * launcher kept the untouched layout, and the widget showed its XML placeholders ("Mon, Mar 9",
 * "17°C", light tiles with the hour digits bunched over the seam) while every existing test
 * stayed green.
 *
 * These assertions pin the rendered style instead. [RemoteViews.apply] performs the real
 * inflation and replays the real actions, so a regression that stops the updater styling the
 * tree fails here rather than on a home screen.
 *
 * No doubles: a real Robolectric Context, the real AppWidgetManager, and the real domain objects
 * from [WidgetTestFixtures].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetRenderStyleTest {

    private lateinit var context: Context
    private lateinit var updater: CompactWidgetUpdater

    /** The placeholders baked into the layouts. Shipping any of them means nothing bound. */
    private companion object {
        const val PLACEHOLDER_DATE = "Mon, Mar 9"
        const val PLACEHOLDER_TEMPERATURE = "17°C"
        const val PLACEHOLDER_HIGH_LOW = "20° / 11°"
    }

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        // Rendering never touches the Hilt graph, so this updater is built without one.
        updater = CompactWidgetUpdater(context, AppWidgetManager.getInstance(context))
    }

    private fun preferences(clockTheme: String = "dark"): Preferences = mutablePreferencesOf().apply {
        this[booleanPreferencesKey("use_24h_clock")] = true
        this[booleanPreferencesKey("show_date_in_widget")] = true
        this[stringPreferencesKey("temperature_unit")] = "CELSIUS"
        this[stringPreferencesKey("clock_theme")] = clockTheme
        this[stringPreferencesKey("clock_tile_size")] = "MEDIUM"
    }

    /** Builds the shipped RemoteViews, applies them, and lays the tree out at a real widget size. */
    private fun render(clockTheme: String = "dark"): View {
        val snapshot = WidgetRenderSnapshot(
            prefs = preferences(clockTheme),
            location = WidgetTestFixtures.london,
            weather = WidgetTestFixtures.weatherData(LocalDateTime.now()),
        )
        val views = updater.buildViews(appWidgetId = 1, snapshot = snapshot)
        val root = views.apply(context, FrameLayout(context))

        val density = context.resources.displayMetrics.density
        val widthPx = (320 * density).toInt()
        val heightPx = (200 * density).toInt()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.AT_MOST),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    private fun View.text(id: Int): String =
        requireNotNull(findViewById<TextView>(id)) { "view $id missing from the rendered tree" }
            .text.toString()

    @Test
    fun `the rendered date is today, not the layout placeholder`() {
        val rendered = render()

        val expected = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
        assertEquals(expected, rendered.text(R.id.widget_date))
        assertNotEquals(
            "The widget rendered its XML placeholder date, so nothing bound",
            PLACEHOLDER_DATE,
            rendered.text(R.id.widget_date),
        )
    }

    @Test
    fun `the rendered location and temperature come from the snapshot`() {
        val rendered = render()

        assertEquals("London", rendered.text(R.id.city_name_top))
        assertNotEquals(
            "The widget rendered its placeholder temperature, so nothing bound",
            PLACEHOLDER_TEMPERATURE,
            rendered.text(R.id.current_temp_top),
        )
        assertNotEquals(
            "The widget rendered its placeholder high/low, so nothing bound",
            PLACEHOLDER_HIGH_LOW,
            rendered.text(R.id.high_low_top),
        )
        // 15.0C from the fixture, however the binder chooses to format it.
        assertTrue(
            "Temperature '${rendered.text(R.id.current_temp_top)}' does not carry the fixture's 15C",
            rendered.text(R.id.current_temp_top).contains("15"),
        )
    }

    /**
     * The spanning clock carries both digits across two tiles, and only a runtime letterSpacing
     * pushes them apart. Without it they render bunched together over the gap between the tiles
     * -- the "broken clock style" seen on 1.0.191.
     */
    @Test
    fun `the hour and minute digits are spread across their tiles`() {
        val rendered = render()

        val hour = requireNotNull(rendered.findViewById<TextView>(R.id.clock_hour))
        val minute = requireNotNull(rendered.findViewById<TextView>(R.id.clock_minute))

        assertTrue(
            "clock_hour has letterSpacing ${hour.letterSpacing}: the digits render bunched over the tile seam",
            hour.letterSpacing > 0f,
        )
        assertTrue(
            "clock_minute has letterSpacing ${minute.letterSpacing}: the digits render bunched over the tile seam",
            minute.letterSpacing > 0f,
        )
    }

    /**
     * Spreading the digits makes the text as wide as the pair plus the tile gap, because the
     * letter spacing also trails the last glyph. A single-line TextView ellipsizes by default, and
     * POCO Launcher on Android 12 then truncated "13" to nothing: four blank tiles.
     */
    @Test
    fun `the spanning clock is never ellipsized`() {
        val rendered = render()

        listOf(R.id.clock_hour, R.id.clock_minute).forEach { id ->
            assertEquals(
                "view $id ellipsizes, so a spread that overflows by the tile gap blanks the digits",
                null,
                requireNotNull(rendered.findViewById<TextView>(id)).ellipsize,
            )
        }
    }

    /** On API 31+ the spanning clock is the one that shows; the clipped per-tile clocks stay hidden. */
    @Test
    fun `the api 31 clock path is the one rendered`() {
        val rendered = render()

        assertEquals(View.VISIBLE, rendered.findViewById<View>(R.id.clock_hour).visibility)
        assertEquals(View.GONE, rendered.findViewById<View>(R.id.tile_clock_h1).visibility)
        assertEquals(View.GONE, rendered.findViewById<View>(R.id.tile_clock_m2).visibility)
    }

    /**
     * The layout hardcodes the light palette, so a widget that never got styled renders light
     * whatever the user picked. Rendering with the dark theme must actually change the colour.
     */
    @Test
    fun `the selected clock theme colours the digits`() {
        val darkDigits = requireNotNull(render(clockTheme = "dark").findViewById<TextView>(R.id.clock_hour))
        val darkColor = darkDigits.currentTextColor

        val lightDefault = ContextCompat.getColor(context, R.color.flip_digit_text_light)
        assertNotEquals(
            "The dark theme rendered the light default colour, so the theme was never applied",
            lightDefault,
            darkColor,
        )

        val lightDigits = requireNotNull(render(clockTheme = "light").findViewById<TextView>(R.id.clock_hour))
        assertNotEquals(
            "Both themes rendered the same colour, so the theme choice does not reach the view",
            darkColor,
            lightDigits.currentTextColor,
        )
    }

    @Test
    fun `weather card and icon bottoms align with the bottom of location and temperature`() {
        val rendered = render()

        val weatherCard = requireNotNull(rendered.findViewById<View>(R.id.weather_card))
        val weatherIcon = requireNotNull(rendered.findViewById<View>(R.id.weather_icon))
        val locationTop = requireNotNull(rendered.findViewById<View>(R.id.weather_overlay_location_top))
        val tempTop = requireNotNull(rendered.findViewById<View>(R.id.weather_overlay_temp_top))

        val expectedOverlayBottom = maxOf(locationTop.bottom, tempTop.bottom)
        assertTrue(
            "weather_card height (${weatherCard.height}, top=${weatherCard.top}, bottom=${weatherCard.bottom}) should match location/temp bottom ($expectedOverlayBottom)",
            abs(weatherCard.height - expectedOverlayBottom) <= 1,
        )
        assertTrue(
            "weather_icon bottom (${weatherIcon.bottom}) should match weather_card height (${weatherCard.height})",
            abs(weatherIcon.bottom - weatherCard.height) <= 1,
        )
        assertEquals(0, weatherIcon.paddingBottom)
    }

    @Test
    fun `clock tiles keep portrait height and never shrink below small tile minimum even when device configuration is landscape`() {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetId = Shadows.shadowOf(appWidgetManager).createWidget(
            CompactWidgetProvider::class.java,
            R.layout.widget_compact,
        )
        val previousOrientation = context.resources.configuration.orientation
        try {
            context.resources.configuration.orientation = Configuration.ORIENTATION_LANDSCAPE

            val portraitTallOptions = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 95)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 190)
            }
            appWidgetManager.updateAppWidgetOptions(appWidgetId, portraitTallOptions)

            val snapshot = WidgetRenderSnapshot(
                prefs = preferences(),
                location = WidgetTestFixtures.london,
                weather = WidgetTestFixtures.weatherData(LocalDateTime.now()),
            )
            val tallViews = updater.buildViews(appWidgetId = appWidgetId, snapshot = snapshot)
            val tallRoot = tallViews.apply(context, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            tallRoot.measure(
                View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((200 * density).toInt(), View.MeasureSpec.AT_MOST),
            )
            tallRoot.layout(0, 0, tallRoot.measuredWidth, tallRoot.measuredHeight)

            val expectedMediumHeight = context.resources.getDimension(R.dimen.flip_digit_height_medium).toInt()
            val tallHour = requireNotNull(tallRoot.findViewById<View>(R.id.clock_hour))
            assertTrue(
                "Expected unshrunk medium height ($expectedMediumHeight) using portrait MAX_HEIGHT even when device configuration is landscape, got ${tallHour.layoutParams.height}",
                abs(tallHour.layoutParams.height - expectedMediumHeight) <= 1,
            )

            val tinyPortraitOptions = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 95)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110)
            }
            appWidgetManager.updateAppWidgetOptions(appWidgetId, tinyPortraitOptions)

            val tinyViews = updater.buildViews(appWidgetId = appWidgetId, snapshot = snapshot)
            val tinyRoot = tinyViews.apply(context, FrameLayout(context))
            tinyRoot.measure(
                View.MeasureSpec.makeMeasureSpec((320 * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((200 * density).toInt(), View.MeasureSpec.AT_MOST),
            )
            tinyRoot.layout(0, 0, tinyRoot.measuredWidth, tinyRoot.measuredHeight)

            val minSmallHeight = context.resources.getDimension(R.dimen.flip_digit_height_small).toInt()
            val tinyHour = requireNotNull(tinyRoot.findViewById<View>(R.id.clock_hour))
            assertTrue(
                "Clock height (${tinyHour.layoutParams.height}) must never shrink below small tile minimum ($minSmallHeight)",
                tinyHour.layoutParams.height >= minSmallHeight,
            )
        } finally {
            context.resources.configuration.orientation = previousOrientation
        }
    }
}
