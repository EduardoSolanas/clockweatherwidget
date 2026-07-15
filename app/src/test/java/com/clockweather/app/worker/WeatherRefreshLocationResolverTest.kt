package com.clockweather.app.worker

import com.clockweather.app.domain.model.Location
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherRefreshLocationResolverTest {

    @Test
    fun `current location uses fresh coordinates while preserving database identity and flag`() {
        val saved = Location(
            id = 42,
            name = "Old place",
            country = "GB",
            latitude = 51.0,
            longitude = -0.1,
            isCurrentLocation = true,
        )
        val detected = Location(
            name = "New place",
            country = "GB",
            latitude = 52.0,
            longitude = -1.0,
            isCurrentLocation = true,
        )

        val refreshed = WeatherRefreshLocationResolver.resolve(saved, detected)

        assertEquals(detected.copy(id = 42, isCurrentLocation = true), refreshed)
    }

    @Test
    fun `fixed saved location remains unchanged`() {
        val saved = Location(
            id = 7,
            name = "Paris",
            country = "FR",
            latitude = 48.8566,
            longitude = 2.3522,
            isCurrentLocation = false,
        )
        val unrelatedDetection = Location(
            name = "London",
            country = "GB",
            latitude = 51.5074,
            longitude = -0.1278,
            isCurrentLocation = true,
        )

        assertEquals(saved, WeatherRefreshLocationResolver.resolve(saved, unrelatedDetection))
    }

    @Test
    fun `current location remains unchanged when no fresh fix is available`() {
        val saved = Location(
            id = 42,
            name = "Old place",
            country = "GB",
            latitude = 51.0,
            longitude = -0.1,
            isCurrentLocation = true,
        )

        assertEquals(saved, WeatherRefreshLocationResolver.resolve(saved, null))
    }
}
