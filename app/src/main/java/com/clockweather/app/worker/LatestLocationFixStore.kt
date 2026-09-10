package com.clockweather.app.worker

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey

/** A passive fix and when the platform observed it. */
data class LatestLocationFix(
    val latitude: Double,
    val longitude: Double,
    val fixTimeMs: Long,
)

/**
 * The newest accepted passive fix, held outside the WorkManager request that acts on it.
 *
 * Relocation work is enqueued with [androidx.work.ExistingWorkPolicy.KEEP], so a fix that
 * arrives while an earlier request is queued or running is discarded along with its request —
 * and the worker then publishes the older position it was created with. Replacing the request
 * instead would be worse: during continuous movement each new fix would cancel the request in
 * flight and no refresh would ever finish.
 *
 * Keeping the fix here separates the two. The request stays deduplicated by KEEP, while the
 * coordinates it acts on are whatever is newest when the worker actually starts.
 *
 * Writes are ordered by [LatestLocationFix.fixTimeMs] rather than by arrival, because delayed
 * delivery can present an older fix after a newer one has already been accepted.
 */
object LatestLocationFixStore {

    private val KEY_LATITUDE = doublePreferencesKey("latest_fix_latitude")
    private val KEY_LONGITUDE = doublePreferencesKey("latest_fix_longitude")
    private val KEY_FIX_TIME_MS = longPreferencesKey("latest_fix_time_ms")

    suspend fun record(
        dataStore: DataStore<Preferences>,
        latitude: Double,
        longitude: Double,
        fixTimeMs: Long,
    ) {
        dataStore.edit { preferences ->
            val accepted = preferences[KEY_FIX_TIME_MS]
            if (accepted != null && accepted >= fixTimeMs) return@edit
            preferences[KEY_LATITUDE] = latitude
            preferences[KEY_LONGITUDE] = longitude
            preferences[KEY_FIX_TIME_MS] = fixTimeMs
        }
    }

    fun read(preferences: Preferences): LatestLocationFix? {
        val latitude = preferences[KEY_LATITUDE] ?: return null
        val longitude = preferences[KEY_LONGITUDE] ?: return null
        val fixTimeMs = preferences[KEY_FIX_TIME_MS] ?: return null
        return LatestLocationFix(latitude, longitude, fixTimeMs)
    }

    /**
     * Drops a fix once its work has published it, so a later worker with no input of its own
     * does not act on coordinates that have already been used.
     */
    suspend fun clear(dataStore: DataStore<Preferences>) {
        dataStore.edit { preferences ->
            preferences.remove(KEY_LATITUDE)
            preferences.remove(KEY_LONGITUDE)
            preferences.remove(KEY_FIX_TIME_MS)
        }
    }
}
