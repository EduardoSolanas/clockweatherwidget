package com.clockweather.app.util

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking

/**
 * In-memory cache of DataStore [Preferences] for widget-tick hot path.
 *
 * Minute-tick clock updates can call [get] instead of `dataStore.data.first()`,
 * avoiding disk I/O every 60 seconds. The cache is kept in sync by a Flow
 * collector launched at application start.
 *
 * Full widget rebuilds should still read from DataStore directly for freshness.
 */
object WidgetPrefsCache {

    @Volatile
    private var snapshot: Preferences? = null

    private var initialized = false

    /**
     * Start observing the DataStore. Call once from [com.clockweather.app.ClockWeatherApplication.onCreate].
     */
    fun init(dataStore: DataStore<Preferences>, scope: CoroutineScope) {
        if (initialized) return
        initialized = true

        dataStore.data
            .catch { emit(emptyPreferences()) }
            .onEach { snapshot = it }
            .launchIn(scope)
    }

    /**
     * Returns the most recent cached [Preferences] snapshot.
     *
     * If the cache hasn't been seeded yet (rare — only possible if called
     * before the first Flow emission), performs a blocking-once read.
     */
    fun get(dataStore: DataStore<Preferences>): Preferences {
        return snapshot ?: runBlocking {
            dataStore.data.first().also { snapshot = it }
        }
    }

    /**
     * Returns the in-memory snapshot without any disk I/O, or null if
     * the cache hasn't been seeded yet. Used by ultra-fast clock push paths
     * where blocking is unacceptable.
     */
    fun getCachedSnapshot(): Preferences? = snapshot

    /**
     * Synchronously seeds the cache if it hasn't been seeded yet.
     * Safe to call from [com.clockweather.app.ClockWeatherApplication.onCreate] on the main thread
     * because the DataStore file is small and this only blocks on first access.
     * Eliminates the cold-start race where [getCachedSnapshot] returns null before the
     * async flow emits its first value.
     */
    fun seedBlocking(dataStore: DataStore<Preferences>) {
        if (snapshot == null) {
            snapshot = runBlocking { dataStore.data.first() }
        }
    }

    /** Only for use in tests to reset singleton state between test cases. */
    fun resetForTesting() {
        snapshot = null
        initialized = false
    }
}

