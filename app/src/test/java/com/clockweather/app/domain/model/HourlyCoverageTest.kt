package com.clockweather.app.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Hourly pages are 7 of the 12 requests in a default Google refresh, and only the detail
 * screen's per-day graph reads them — the widgets use the daily forecast. Background refreshes
 * therefore fetch a rolling 24 hours, and the rest of the week is fetched when the app is
 * opened.
 *
 * This decides whether the cache currently holds that rest. It is derived from the hours
 * themselves rather than a stored timestamp, so there is nothing to migrate and nothing that
 * can disagree with the data it describes.
 */
class HourlyCoverageTest {

    private val reference = LocalDateTime.of(2026, 4, 3, 10, 42)

    @Test
    fun `the near-term window alone is not extended coverage`() {
        assertFalse(hasExtendedHourlyCoverage(hours(NEAR_TERM_HOURS), reference))
    }

    @Test
    fun `hours reaching past the near-term window are extended coverage`() {
        assertTrue(hasExtendedHourlyCoverage(hours(NEAR_TERM_HOURS + 2), reference))
    }

    /**
     * The window has to outlast a day of ageing, or a cache holding exactly the 24 future hours
     * isWeatherDataFresh demands drops below them within the hour and reads stale for good.
     */
    @Test
    fun `the near-term window leaves a day of headroom over the freshness requirement`() {
        assertTrue(NEAR_TERM_HOURS >= 48)
    }

    @Test
    fun `an empty cache is not extended coverage`() {
        assertFalse(hasExtendedHourlyCoverage(emptyList(), reference))
    }

    /**
     * Hours already in the past do not count. A week-long set left to age until only this
     * evening remains must read as needing the rest fetched again, not as still complete.
     */
    @Test
    fun `stale hours that have fallen into the past do not count as coverage`() {
        val yesterday = hours(count = NEAR_TERM_HOURS, start = reference.minusDays(4))

        assertFalse(hasExtendedHourlyCoverage(yesterday, reference))
    }

    @Test
    fun `coverage is measured from the reference time, not from the first cached hour`() {
        val startingYesterday = hours(count = 120, start = reference.minusDays(1))

        assertTrue(
            "120 hours from yesterday still reaches well past the near-term window",
            hasExtendedHourlyCoverage(startingYesterday, reference)
        )
    }

    private fun hours(count: Int, start: LocalDateTime = reference): List<HourlyForecast> {
        val firstHour = start.withMinute(0).withSecond(0).withNano(0)
        return (0 until count).map { offset ->
            HourlyForecast(
                dateTime = firstHour.plusHours(offset.toLong()),
                temperature = 15.0,
                feelsLike = 15.0,
                humidity = 60,
                dewPoint = 9.0,
                precipitationProbability = 0,
                weatherCondition = WeatherCondition.PARTLY_CLOUDY_DAY,
                isDay = true,
                pressure = 1012.0,
                windSpeed = 10.0,
                windDirection = WindDirection.N,
                windDirectionDegrees = 0,
                visibility = 10_000.0,
                uvIndex = 5.0,
            )
        }
    }
}
