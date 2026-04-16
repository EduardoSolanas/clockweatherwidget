package com.clockweather.app.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [WidgetPrefsCache] — specifically the cold-start seeding contract.
 *
 * Invariants:
 * 1. Before seeding, getCachedSnapshot() returns null.
 * 2. After seedBlocking(), getCachedSnapshot() returns the seeded preferences immediately.
 * 3. seedBlocking() is idempotent (second call with different data doesn't overwrite).
 * 4. Seeded preference values are readable via getCachedSnapshot().
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetPrefsCacheTest {

    @Before
    fun reset() = WidgetPrefsCache.resetForTesting()

    @After
    fun teardown() = WidgetPrefsCache.resetForTesting()

    private fun fakeDataStore(prefs: Preferences = emptyPreferences()): DataStore<Preferences> =
        mockk<DataStore<Preferences>>().also { ds ->
            every { ds.data } returns flowOf(prefs)
        }

    @Test
    fun `getCachedSnapshot returns null before any seeding`() {
        assertNull(WidgetPrefsCache.getCachedSnapshot())
    }

    @Test
    fun `seedBlocking makes getCachedSnapshot return non-null immediately`() {
        WidgetPrefsCache.seedBlocking(fakeDataStore())

        assertNotNull(WidgetPrefsCache.getCachedSnapshot())
    }

    @Test
    fun `seedBlocking stores correct preference values`() {
        val key = booleanPreferencesKey("use_24h_clock")
        val prefs = mutablePreferencesOf(key to true)

        WidgetPrefsCache.seedBlocking(fakeDataStore(prefs))

        assertEquals(true, WidgetPrefsCache.getCachedSnapshot()?.get(key))
    }

    @Test
    fun `seedBlocking is idempotent — second call does not overwrite existing snapshot`() {
        val key = booleanPreferencesKey("use_24h_clock")
        val firstPrefs = mutablePreferencesOf(key to true)
        val secondPrefs = mutablePreferencesOf(key to false)

        WidgetPrefsCache.seedBlocking(fakeDataStore(firstPrefs))
        WidgetPrefsCache.seedBlocking(fakeDataStore(secondPrefs))

        assertEquals(true, WidgetPrefsCache.getCachedSnapshot()?.get(key))
    }
}
