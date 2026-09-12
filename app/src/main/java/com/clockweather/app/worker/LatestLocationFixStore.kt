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
 * A fix that arrives while an earlier request is queued or running used to be discarded along
 * with its own request, leaving the worker to publish the older position it was created with.
 * Cancelling the request in flight instead would be worse: during continuous movement each new
 * fix would cancel the run before it finished and no refresh would ever complete.
 *
 * Keeping the fix here separates the request from the coordinates it acts on. The worker reads
 * whatever is newest when it actually starts, so a fix is never lost to a queueing decision —
 * which is also why the request itself no longer needs to carry coordinates.
 *
 * Writes are ordered by [LatestLocationFix.fixTimeMs] rather than by arrival, because delayed
 * delivery can present an older fix after a newer one has already been accepted.
 */
object LatestLocationFixStore {

    /**
     * How long a queued fix may wait before a live lookup is the better answer.
     *
     * The request that acts on this fix carries no coordinates of its own, and WorkManager can
     * defer it — Doze, a network constraint, a battery saver. Past this bound the device has had
     * time to move somewhere the fix no longer describes, so asking the platform again beats
     * publishing weather for wherever it used to be. Matches the bound
     * `LocationRepositoryImpl` applies to a recent last-known fix.
     */
    const val MAX_QUEUED_FIX_AGE_MS = 15 * 60 * 1000L

    /**
     * Whether [fix] is still worth preferring over a fresh device lookup. A fix dated in the
     * future is rejected too: a rolled-back clock must not make a stale observation look newest.
     */
    fun isRecent(fix: LatestLocationFix, nowMs: Long = System.currentTimeMillis()): Boolean =
        (nowMs - fix.fixTimeMs) in 0..MAX_QUEUED_FIX_AGE_MS

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
     * Clears the consumed fix only when no newer fix replaced it while work was running.
     * Returns false when the stored fix has advanced and must be handled by a later run.
     */
    suspend fun clearIfSame(dataStore: DataStore<Preferences>, fixTimeMs: Long): Boolean {
        var cleared = false
        dataStore.edit { preferences ->
            if (preferences[KEY_FIX_TIME_MS] == fixTimeMs) {
                preferences.remove(KEY_LATITUDE)
                preferences.remove(KEY_LONGITUDE)
                preferences.remove(KEY_FIX_TIME_MS)
                cleared = true
            }
        }
        return cleared
    }
}
