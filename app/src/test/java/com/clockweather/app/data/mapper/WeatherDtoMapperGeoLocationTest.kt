package com.clockweather.app.data.mapper

import com.clockweather.app.data.remote.dto.GeoLocationDto
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the mapGeoLocation function to ensure proper resolution of location names
 * using the administrative hierarchy and distinct place names from the provider.
 */
class WeatherDtoMapperGeoLocationTest {

    private val mapper = WeatherDtoMapper()

    @Test
    fun `uses admin4 (most specific) when available`() {
        val dto = GeoLocationDto(
            id = 1,
            name = "Greater London",
            latitude = 51.5074,
            longitude = -0.1278,
            elevation = null,
            featureCode = "ADM2",
            countryCode = "GB",
            country = "United Kingdom",
            timezone = "Europe/London",
            population = 9000000,
            admin1 = "England",
            admin2 = "Greater London",
            admin3 = "Wandsworth",
            admin4 = "Battersea"
        )

        val location = mapper.mapGeoLocation(dto)

        assertEquals("Battersea", location.name)
    }

    @Test
    fun `falls back to admin3 when admin4 is missing`() {
        val dto = GeoLocationDto(
            id = 1,
            name = "Greater London",
            latitude = 51.5074,
            longitude = -0.1278,
            elevation = null,
            featureCode = "ADM2",
            countryCode = "GB",
            country = "United Kingdom",
            timezone = "Europe/London",
            population = 9000000,
            admin1 = "England",
            admin2 = "Greater London",
            admin3 = "Wandsworth",
            admin4 = null
        )

        val location = mapper.mapGeoLocation(dto)

        assertEquals("Wandsworth", location.name)
    }

    @Test
    fun `falls back to admin2 when admin3 and admin4 are missing`() {
        val dto = GeoLocationDto(
            id = 1,
            name = "Greater London",
            latitude = 51.5074,
            longitude = -0.1278,
            elevation = null,
            featureCode = "ADM2",
            countryCode = "GB",
            country = "United Kingdom",
            timezone = "Europe/London",
            population = 9000000,
            admin1 = "England",
            admin2 = "Greater London",
            admin3 = null,
            admin4 = null
        )

        val location = mapper.mapGeoLocation(dto)

        assertEquals("Greater London", location.name)
    }

    @Test
    fun `keeps explicit place name when admin fields are broader`() {
        val dto = GeoLocationDto(
            id = 1,
            name = "Los Angeles",
            latitude = 34.0522,
            longitude = -118.2437,
            elevation = null,
            featureCode = "ADM2",
            countryCode = "US",
            country = "United States",
            timezone = "America/Los_Angeles",
            population = 3979576,
            admin1 = "California",
            admin2 = null,
            admin3 = null,
            admin4 = null
        )

        val location = mapper.mapGeoLocation(dto)

        assertEquals("Los Angeles", location.name)
    }

    @Test
    fun `keeps explicit locality name instead of broad admin2`() {
        val dto = GeoLocationDto(
            id = 1,
            name = "Shinjuku",
            latitude = 35.6938,
            longitude = 139.7034,
            elevation = null,
            featureCode = "PPLA3",
            countryCode = "JP",
            country = "Japan",
            timezone = "Asia/Tokyo",
            population = 0,
            admin1 = "Tokyo",
            admin2 = "Tokyo",
            admin3 = null,
            admin4 = null
        )

        val location = mapper.mapGeoLocation(dto)

        assertEquals("Shinjuku", location.name)
    }

    @Test
    fun `falls back to admin1 when explicit name duplicates broader admin value`() {
        val dto = GeoLocationDto(
            id = 1,
            name = "東京都",
            latitude = 35.6762,
            longitude = 139.6503,
            elevation = null,
            featureCode = "ADM1",
            countryCode = "JP",
            country = "Japan",
            timezone = "Asia/Tokyo",
            population = 0,
            admin1 = "東京都",
            admin2 = null,
            admin3 = null,
            admin4 = null
        )

        val location = mapper.mapGeoLocation(dto)

        assertEquals("東京都", location.name)
    }

    @Test
    fun `uses name when all admin fields are missing`() {
        val dto = GeoLocationDto(
            id = 1,
            name = "Some City",
            latitude = 40.7128,
            longitude = -74.0060,
            elevation = null,
            featureCode = "PPL",
            countryCode = "US",
            country = "United States",
            timezone = "America/New_York",
            population = 8000000,
            admin1 = null,
            admin2 = null,
            admin3 = null,
            admin4 = null
        )

        val location = mapper.mapGeoLocation(dto)

        assertEquals("Some City", location.name)
    }

    @Test
    fun `preserves other location fields correctly`() {
        val dto = GeoLocationDto(
            id = 12345,
            name = "Greater London",
            latitude = 51.5074,
            longitude = -0.1278,
            elevation = 11.0,
            featureCode = "ADM2",
            countryCode = "GB",
            country = "United Kingdom",
            timezone = "Europe/London",
            population = 9000000,
            admin1 = "England",
            admin2 = "Greater London",
            admin3 = "City of London",
            admin4 = null
        )

        val location = mapper.mapGeoLocation(dto)

        assertEquals("City of London", location.name)
        assertEquals(12345, location.id)
        assertEquals("United Kingdom", location.country)
        assertEquals(51.5074, location.latitude, 0.0001)
        assertEquals(-0.1278, location.longitude, 0.0001)
        assertEquals("Europe/London", location.timezone)
        assertEquals(false, location.isCurrentLocation)
    }
}

