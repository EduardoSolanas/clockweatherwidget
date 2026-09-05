package com.clockweather.app.worker

import com.clockweather.app.domain.model.Location
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Queue step 3 of docs/TIME_WEATHER_SYNC_REVIEW.md.
 *
 * A refresh hands the existing cache to the provider so still-fresh optional sections can be
 * reused instead of re-bought. After a move that reuse is wrong: air quality and pollen
 * measured in the city the user left would be re-published under the city they arrived in,
 * carrying a timestamp that says they are current.
 *
 * The comparison has to use the coordinates stored on the weather row itself. Rows written
 * before that column existed hold null, and the domain mapper substitutes the location row's
 * own coordinates for them — which are the new ones after a move, so a mapped-location
 * comparison would always report no movement and always reuse.
 */
class CachedWeatherOwnershipTest {

    private val brighton = Location(
        id = 1L,
        name = "Brighton",
        country = "GB",
        latitude = 50.8225,
        longitude = -0.1372,
        isCurrentLocation = true,
    )

    private val londonLatitude = 51.5074
    private val londonLongitude = -0.1278

    @Test
    fun `cache recorded at the requested location is reusable`() {
        assertTrue(
            WeatherRefreshLocationResolver.cacheDescribes(
                snapshotLatitude = brighton.latitude,
                snapshotLongitude = brighton.longitude,
                requested = brighton,
            )
        )
    }

    @Test
    fun `cache recorded before a significant move is not reusable`() {
        assertFalse(
            "London air quality must not be reused for Brighton",
            WeatherRefreshLocationResolver.cacheDescribes(
                snapshotLatitude = londonLatitude,
                snapshotLongitude = londonLongitude,
                requested = brighton,
            )
        )
    }

    @Test
    fun `a fix that jitters within the threshold stays reusable`() {
        assertTrue(
            "sub-threshold jitter must not throw away optional sections every refresh",
            WeatherRefreshLocationResolver.cacheDescribes(
                snapshotLatitude = brighton.latitude + 0.001,
                snapshotLongitude = brighton.longitude + 0.001,
                requested = brighton,
            )
        )
    }

    /** Migrated rows predate the coordinate columns; unknown provenance cannot prove ownership. */
    @Test
    fun `cache with unknown coordinates is not reusable`() {
        assertFalse(
            WeatherRefreshLocationResolver.cacheDescribes(
                snapshotLatitude = null,
                snapshotLongitude = null,
                requested = brighton,
            )
        )
        assertFalse(
            "a half-written row is still unknown provenance",
            WeatherRefreshLocationResolver.cacheDescribes(
                snapshotLatitude = brighton.latitude,
                snapshotLongitude = null,
                requested = brighton,
            )
        )
    }
}
