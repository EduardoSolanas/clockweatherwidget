package com.clockweather.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Single source of truth for the DataStore instance.
 * Using a property delegate guarantees only one instance exists per process.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
