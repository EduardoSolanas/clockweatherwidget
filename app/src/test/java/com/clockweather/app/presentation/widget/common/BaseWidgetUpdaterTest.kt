package com.clockweather.app.presentation.widget.common

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.util.DisplayMetrics
import android.util.Log
import android.widget.RemoteViews
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clockweather.app.R
import com.clockweather.app.di.WidgetEntryPoint
import com.clockweather.app.domain.model.CurrentWeather
import com.clockweather.app.domain.model.DailyForecast
import com.clockweather.app.domain.model.Location
import com.clockweather.app.domain.model.TemperatureUnit
import com.clockweather.app.domain.model.WeatherCondition
import com.clockweather.app.domain.model.WeatherData
import com.clockweather.app.domain.model.WindDirection
import com.clockweather.app.domain.repository.LocationRepository
import com.clockweather.app.domain.repository.WeatherRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BaseWidgetUpdaterTest {

    private val realContext: Context = RuntimeEnvironment.getApplication()
    private lateinit var mockContext: Context
    private lateinit var mockResources: Resources
    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var entryPoint: WidgetEntryPoint
    private lateinit var locationRepo: LocationRepository
    private lateinit var weatherRepo: WeatherRepository
    private lateinit var updater: TestWidgetUpdater

    private val widgetId = 99

    private val prefs: Preferences = preferencesOf(
        booleanPreferencesKey("use_24h_clock") to true,
        booleanPreferencesKey("show_date_in_widget") to false,
        booleanPreferencesKey("flip_animation_enabled") to true,
        stringPreferencesKey("temperature_unit") to "CELSIUS",
        stringPreferencesKey("clock_theme") to "dark",
        stringPreferencesKey("clock_tile_size") to "MEDIUM",
    )

    private class TestWidgetUpdater(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entryPoint: WidgetEntryPoint,
    ) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {
        override val layoutResId = R.layout.widget_compact
        override val rootViewId = R.id.widget_root
        override val dateViewId = R.id.widget_date

        override fun bindExtra(
            views: RemoteViews,
            weather: WeatherData,
            tempUnit: TemperatureUnit,
            prefs: Preferences,
        ) {
        }
    }

    private class ForecastLikeWidgetUpdater(
        context: Context,
        appWidgetManager: AppWidgetManager,
        entryPoint: WidgetEntryPoint,
    ) : BaseWidgetUpdater(context, appWidgetManager, entryPoint) {
        override val layoutResId = R.layout.widget_forecast
        override val rootViewId = R.id.widget_root
        override val dateViewId = R.id.widget_date
        override val minimumFutureForecastDaysRequired = 7

        override fun bindExtra(
            views: RemoteViews,
            weather: WeatherData,
            tempUnit: TemperatureUnit,
            prefs: Preferences,
        ) {
        }
    }

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0

        appWidgetManager = mockk()
        every { appWidgetManager.updateAppWidget(any<Int>(), any()) } just Runs
        every { appWidgetManager.getAppWidgetOptions(any()) } returns Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
        }

        entryPoint = mockk(relaxed = true)

        val dataStore: DataStore<Preferences> = mockk()
        every { entryPoint.dataStore() } returns dataStore
        every { dataStore.data } returns flowOf(prefs)

        locationRepo = mockk()
        every { entryPoint.locationRepository() } returns locationRepo
        every { locationRepo.getSavedLocations() } returns flowOf(emptyList())
        coEvery { locationRepo.getCurrentLocation() } returns null
        every { locationRepo.getFallbackLocation() } returns Location(
            id = 0L,
            name = "London",
            country = "GB",
            latitude = 51.5074,
            longitude = -0.1278,
            isCurrentLocation = true,
        )
        coEvery { locationRepo.saveLocation(any()) } answers {
            firstArg<Location>().id.takeIf { it != 0L } ?: 1L
        }

        weatherRepo = mockk(relaxed = true)
        every { entryPoint.weatherRepository() } returns weatherRepo
        every { weatherRepo.getWeatherData(any()) } returns flowOf(null)

        mockResources = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)
        every { mockContext.packageName } returns realContext.packageName
        every { mockContext.applicationContext } returns realContext
        every { mockContext.resources } returns mockResources
        every { mockContext.getColor(R.color.flip_digit_text_light) } returns Color.BLACK
        every { mockContext.getColor(R.color.flip_digit_text_dark) } returns Color.WHITE
        every { mockContext.getString(R.string.widget_weather_unavailable_title) } returns "Weather"
        every { mockContext.getString(R.string.widget_weather_unavailable_condition) } returns "Updating weather"
        every { mockContext.getString(R.string.widget_weather_unavailable_temp) } returns "--°"
        every { mockResources.getDimension(any()) } returns 48f
        every { mockResources.displayMetrics } returns DisplayMetrics().apply {
            density = 2f
        }
        every { mockResources.configuration } returns Configuration().apply { fontScale = 1.5f }

        mockkConstructor(RemoteViews::class)
        every { anyConstructed<RemoteViews>().setViewVisibility(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewText(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setCharSequence(any<Int>(), any<String>(), any<CharSequence>()) } just Runs
        every { anyConstructed<RemoteViews>().setTextViewTextSize(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setTextColor(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setInt(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setFloat(any(), any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setOnClickPendingIntent(any(), any()) } just Runs
        every { anyConstructed<RemoteViews>().setViewLayoutHeight(any(), any(), any()) } just Runs

        mockkStatic(PendingIntent::class)
        every { PendingIntent.getActivity(any(), any(), any(), any()) } returns mockk()

        updater = TestWidgetUpdater(mockContext, appWidgetManager, entryPoint)
    }

    @After
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `updateWidget always uses updateAppWidget`() = runBlocking {
        updater.updateWidget(widgetId)

        verify(exactly = 1) { appWidgetManager.updateAppWidget(widgetId, any()) }
        verify(exactly = 1) { appWidgetManager.getAppWidgetOptions(widgetId) }
        confirmVerified(appWidgetManager)
    }

    @Test
    fun `missing current location uses fallback and refreshes weather`() = runBlocking {
        updater.updateWidget(widgetId)

        verify(exactly = 1) { locationRepo.getFallbackLocation() }
        coVerify(exactly = 1) { locationRepo.saveLocation(match { it.name == "London" }) }
        coVerify(exactly = 1) { weatherRepo.refreshWidgetWeatherData(match { it.name == "London" }) }
    }

    @Test
    fun `missing clock theme preference falls back to light widget styling`() = runBlocking {
        val prefsWithoutTheme: Preferences = preferencesOf(
            booleanPreferencesKey("use_24h_clock") to true,
            booleanPreferencesKey("show_date_in_widget") to false,
            booleanPreferencesKey("flip_animation_enabled") to true,
            stringPreferencesKey("temperature_unit") to "CELSIUS",
            stringPreferencesKey("clock_tile_size") to "MEDIUM",
        )

        val dataStore: DataStore<Preferences> = mockk()
        every { entryPoint.dataStore() } returns dataStore
        every { dataStore.data } returns flowOf(prefsWithoutTheme)

        TestWidgetUpdater(mockContext, appWidgetManager, entryPoint).updateWidget(widgetId)

        verify(exactly = 4) {
            anyConstructed<RemoteViews>().setInt(any(), "setBackgroundResource", R.drawable.flip_digit_bg_light)
        }
        verify(atLeast = 6) {
            anyConstructed<RemoteViews>().setTextColor(any(), Color.BLACK)
        }
    }

    @Test
    fun `dark clock theme applies dark tile background and text color`() = runBlocking {
        updater.updateWidget(widgetId)

        verify(exactly = 4) {
            anyConstructed<RemoteViews>().setInt(any(), "setBackgroundResource", R.drawable.flip_digit_bg)
        }
        verify(atLeast = 6) {
            anyConstructed<RemoteViews>().setTextColor(any(), Color.WHITE)
        }
    }

    @Test
    fun `clock heights scale with font settings`() = runBlocking {
        // getDimension(any()) returns 48f, fontScale=1.5, widgetTextScale=1.0
        // heightPx = 48 * 1.5 * 1.0 = 72
        val expectedHeight = 72f

        updater.updateWidget(widgetId)

        // weather_card is no longer constrained to flip-tile height —
        // it uses its natural height to avoid clipping location / temperature text.

        verify(atLeast = 1) {
            anyConstructed<RemoteViews>().setViewLayoutHeight(
                R.id.clock_hour,
                expectedHeight,
                android.util.TypedValue.COMPLEX_UNIT_PX,
            )
        }
        verify(atLeast = 1) {
            anyConstructed<RemoteViews>().setViewLayoutHeight(
                R.id.clock_minute,
                expectedHeight,
                android.util.TypedValue.COMPLEX_UNIT_PX,
            )
        }
    }

    @Test
    fun `dynamic letter spacing is applied to both TextClocks`() = runBlocking {
        updater.updateWidget(widgetId)

        verify(exactly = 1) {
            anyConstructed<RemoteViews>().setFloat(R.id.clock_hour, "setLetterSpacing", any())
        }
        verify(exactly = 1) {
            anyConstructed<RemoteViews>().setFloat(R.id.clock_minute, "setLetterSpacing", any())
        }
    }

    @Test
    fun `updateWidget refreshes cached weather when cached data is from a previous day`() = runBlocking {
        val location = Location(
            id = 7L,
            name = "London",
            country = "UK",
            latitude = 51.5072,
            longitude = -0.1276,
        )
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute,
        )
        every { locationRepo.getSavedLocations() } returns flowOf(listOf(location))
        every { weatherRepo.getWeatherData(location) } returns flowOf(
            sampleWeatherData(
                location = location,
                currentLastUpdated = LocalDateTime.of(2026, 4, 2, 23, 55),
                startDate = LocalDate.of(2026, 4, 2),
            ),
        )

        updater.updateWidget(widgetId)

        coVerify(exactly = 1) { weatherRepo.refreshWidgetWeatherData(location) }
    }

    @Test
    fun `forecast widget refreshes when cached data only contains six future days`() = runBlocking {
        val location = Location(
            id = 8L,
            name = "London",
            country = "UK",
            latitude = 51.5072,
            longitude = -0.1276,
        )
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute,
        )
        every { locationRepo.getSavedLocations() } returns flowOf(listOf(location))
        every { weatherRepo.getWeatherData(location) } returns flowOf(
            sampleWeatherData(
                location = location,
                currentLastUpdated = LocalDateTime.of(2026, 4, 3, 9, 0),
                startDate = LocalDate.of(2026, 4, 3),
                dayCount = 7,
            ),
        )

        ForecastLikeWidgetUpdater(mockContext, appWidgetManager, entryPoint).updateWidget(widgetId)

        coVerify(exactly = 1) { weatherRepo.refreshWidgetWeatherData(location) }
    }

    @Test
    fun `empty weather cache triggers refresh before showing unavailable state`() = runBlocking {
        val location = Location(
            id = 43L,
            name = "London",
            country = "UK",
            latitude = 51.5072,
            longitude = -0.1276,
        )
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute,
        )
        every { locationRepo.getSavedLocations() } returns flowOf(listOf(location))
        every { weatherRepo.getWeatherData(location) } returnsMany listOf(flowOf(null), flowOf(null))

        updater.updateWidget(widgetId)

        coVerify(exactly = 1) { weatherRepo.refreshWidgetWeatherData(location) }
        verify(exactly = 1) {
            anyConstructed<RemoteViews>().setTextViewText(R.id.condition_text, "Updating weather")
        }
        verify(exactly = 1) {
            anyConstructed<RemoteViews>().setViewVisibility(R.id.weather_card, android.view.View.VISIBLE)
        }
    }

    @Test
    fun `widget text px uses system base role multiplier and settings scale`() {
        val resources = mockk<Resources>()
        every { resources.displayMetrics } returns DisplayMetrics().apply {
            density = 2f
        }
        every { resources.configuration } returns Configuration().apply { fontScale = 1.5f }

        assertEquals(42f, widgetSystemBaseTextPx(resources), 0.01f)
        assertEquals(42f, widgetTextPx(resources, WidgetTextRole.BODY), 0.01f)
        assertEquals(33.6f, widgetTextPx(resources, WidgetTextRole.FORECAST_META), 0.01f)
        assertEquals(77.7f, widgetTextPx(resources, WidgetTextRole.TEMPERATURE), 0.01f)
        assertEquals(33.6f, widgetTextPx(resources, WidgetTextRole.BODY, settingsScale = 0.8f), 0.01f)
    }

    @Test
    fun `clock role is selected from tile size`() {
        assertEquals(WidgetTextRole.CLOCK_SMALL, WidgetTextRole.clock(com.clockweather.app.domain.model.ClockTileSize.SMALL))
        assertEquals(WidgetTextRole.CLOCK_MEDIUM, WidgetTextRole.clock(com.clockweather.app.domain.model.ClockTileSize.MEDIUM))
        assertEquals(WidgetTextRole.CLOCK_LARGE, WidgetTextRole.clock(com.clockweather.app.domain.model.ClockTileSize.LARGE))
        assertEquals(WidgetTextRole.CLOCK_XL, WidgetTextRole.clock(com.clockweather.app.domain.model.ClockTileSize.EXTRA_LARGE))
    }

    @Test
    fun `digit panel correction applies small outward offsets`() {
        val resources = mockk<Resources>()
        every { resources.displayMetrics } returns DisplayMetrics().apply {
            density = 2f
        }

        assertEquals(-3f, widgetDigitOffsetPx(resources, DigitPanelCorrection.ODD), 0.01f)
        assertEquals(3f, widgetDigitOffsetPx(resources, DigitPanelCorrection.EVEN), 0.01f)
    }

    @Test
    fun `computeFlipClockLetterSpacing centers digits over their tiles`() {
        // Two tiles sharing a 400px-wide container with a 4px gap between them.
        // Tile center distance = (400 + 4) / 2 = 202px.
        // With glyphAdvance=60px and fontSize=100px:
        // letterSpacing = (202 - 60) / 100 = 1.42
        val ls = computeFlipClockLetterSpacing(
            pairWidthPx = 400f,
            gapPx = 4f,
            glyphAdvancePx = 60f,
            fontSizePx = 100f,
        )
        assertEquals(1.42f, ls, 0.001f)
    }

    @Test
    fun `computeFlipClockLetterSpacing clamps to zero when tiles are too narrow`() {
        // If tiles are so narrow that glyphAdvance already exceeds the tile center distance,
        // letterSpacing should be clamped to 0 (no negative spacing).
        val ls = computeFlipClockLetterSpacing(
            pairWidthPx = 80f,
            gapPx = 4f,
            glyphAdvancePx = 60f,
            fontSizePx = 100f,
        )
        // (80 + 4) / 2 = 42, (42 - 60) / 100 = -0.18 → clamped to 0
        assertEquals(0f, ls, 0.001f)
    }

    @Test
    fun `computeFlipClockLetterSpacing with real-world dimensions`() {
        // Typical Pixel 7: density=2.625, widget ~470px pair width, gap=5.25px
        // clockTextPx≈147, monospace glyph advance≈88px (0.6*147)
        val ls = computeFlipClockLetterSpacing(
            pairWidthPx = 470f,
            gapPx = 5.25f,
            glyphAdvancePx = 88.2f,
            fontSizePx = 147f,
        )
        // (470 + 5.25) / 2 = 237.625, (237.625 - 88.2) / 147 ≈ 1.0161
        assertEquals(1.016f, ls, 0.01f)
    }

    @Test
    fun `computeFlipTileHeightPx preserves base height at default scales`() {
        // At fontScale=1 and widgetTextScale=1, height equals the base dimen
        assertEquals(252f, computeFlipTileHeightPx(252f, 1f, 1f), 0.001f)
    }

    @Test
    fun `computeFlipTileHeightPx scales with font and widget settings`() {
        // base=252px (96dp * 2.625), fontScale=1.3, widgetTextScale=1.05
        // 252 * 1.3 * 1.05 = 343.98
        assertEquals(343.98f, computeFlipTileHeightPx(252f, 1.3f, 1.05f), 0.01f)
    }

    @Test
    fun `first render uses saved location id immediately after detecting current location`() = runBlocking {
        val detectedLocation = Location(
            id = 0L,
            name = "Greater London",
            country = "UK",
            latitude = 51.5072,
            longitude = -0.1276,
            isCurrentLocation = true,
        )
        val savedLocation = detectedLocation.copy(id = 99L)
        val currentMinute = System.currentTimeMillis() / 60000L
        mockkObject(ClockSnapshot.Companion)
        every { ClockSnapshot.now(any(), any()) } returns ClockSnapshot(
            localTime = LocalTime.of(10, 26),
            epochMinute = currentMinute,
        )
        every { locationRepo.getSavedLocations() } returns flowOf(emptyList())
        coEvery { locationRepo.getCurrentLocation() } returns detectedLocation
        coEvery { locationRepo.saveLocation(detectedLocation) } returns 99L
        every { weatherRepo.getWeatherData(savedLocation) } returnsMany listOf(
            flowOf(null),
            flowOf(
                sampleWeatherData(
                    location = savedLocation,
                    currentLastUpdated = LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 0)),
                    startDate = LocalDate.now(),
                )
            )
        )

        updater.updateWidget(widgetId)

        coVerify(exactly = 1) { weatherRepo.refreshWidgetWeatherData(savedLocation) }
        verify(exactly = 2) { weatherRepo.getWeatherData(savedLocation) }
    }

    private fun sampleWeatherData(
        location: Location,
        currentLastUpdated: LocalDateTime,
        startDate: LocalDate,
        dayCount: Int = 8,
    ): WeatherData {
        return WeatherData(
            location = location,
            currentWeather = CurrentWeather(
                temperature = 17.0,
                feelsLikeTemperature = 17.0,
                humidity = 60,
                dewPoint = 9.0,
                precipitation = 0.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                windGusts = 12.0,
                visibility = 10.0,
                uvIndex = 5.0,
                cloudCover = 30,
                lastUpdated = currentLastUpdated,
            ),
            hourlyForecasts = emptyList(),
            dailyForecasts = (0 until dayCount).map { offset ->
                DailyForecast(
                    date = startDate.plusDays(offset.toLong()),
                    weatherCondition = WeatherCondition.CLEAR_DAY,
                    temperatureMax = 20.0 + offset,
                    temperatureMin = 10.0 + offset,
                    feelsLikeMax = 20.0 + offset,
                    feelsLikeMin = 10.0 + offset,
                    sunrise = LocalTime.of(6, 0),
                    sunset = LocalTime.of(19, 0),
                    daylightDurationSeconds = 36000.0,
                    precipitationSum = 0.0,
                    precipitationProbability = 0,
                    windSpeedMax = 10.0,
                    windDirectionDominant = WindDirection.N,
                    windDirectionDegrees = 0,
                    uvIndexMax = 5.0,
                    averageHumidity = 60,
                    averagePressure = 1012.0,
                )
            },
        )
    }
}
