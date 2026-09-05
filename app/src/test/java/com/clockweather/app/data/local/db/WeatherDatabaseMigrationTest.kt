package com.clockweather.app.data.local.db

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class WeatherDatabaseMigrationTest {

    private lateinit var databaseFile: File

    @org.junit.After
    fun removeDatabaseFile() {
        databaseFile.delete()
        File(databaseFile.path + "-journal").delete()
    }

    @Test
    fun `migration 4 to 5 adds location and independent section timestamp columns`() {
        val context = RuntimeEnvironment.getApplication()
        databaseFile = context.getDatabasePath("weather_migration_${System.nanoTime()}.db")
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(databaseFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v4 current_weather table
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS current_weather (
                            locationId INTEGER PRIMARY KEY NOT NULL,
                            temperature REAL NOT NULL,
                            feelsLikeTemperature REAL NOT NULL,
                            humidity INTEGER NOT NULL,
                            dewPoint REAL NOT NULL,
                            precipitation REAL NOT NULL,
                            precipitationProbability INTEGER NOT NULL,
                            weatherCode INTEGER NOT NULL,
                            isDay INTEGER NOT NULL,
                            pressure REAL NOT NULL,
                            windSpeed REAL NOT NULL,
                            windDirectionDegrees INTEGER NOT NULL,
                            windGusts REAL NOT NULL,
                            visibility REAL NOT NULL,
                            uvIndex REAL NOT NULL,
                            cloudCover INTEGER NOT NULL,
                            lastUpdated TEXT NOT NULL,
                            aqCo REAL,
                            aqNo2 REAL,
                            aqO3 REAL,
                            aqSo2 REAL,
                            aqPm25 REAL,
                            aqPm10 REAL,
                            aqUsEpaIndex INTEGER,
                            aqGbDefraIndex INTEGER
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        // Insert a v4 record
        db.execSQL(
            """
            INSERT INTO current_weather (
                locationId, temperature, feelsLikeTemperature, humidity, dewPoint,
                precipitation, precipitationProbability, weatherCode, isDay, pressure,
                windSpeed, windDirectionDegrees, windGusts, visibility, uvIndex,
                cloudCover, lastUpdated, aqCo, aqNo2, aqO3, aqSo2, aqPm25, aqPm10,
                aqUsEpaIndex, aqGbDefraIndex
            ) VALUES (
                1, 20.0, 20.0, 50, 10.0,
                0.0, 0, 1, 1, 1013.25,
                10.0, 0, 12.0, 10000.0, 3.0,
                20, '2026-09-05T10:00:00', 1.0, 2.0, 3.0, 4.0, 5.0, 6.0,
                1, 1
            )
            """.trimIndent()
        )

        // Run Migration 4 -> 5
        WeatherDatabase.MIGRATION_4_5.migrate(db)

        // Query migrated record
        val cursor = db.query("SELECT locationId, temperature, locationName, latitude, longitude, aqLastUpdated, pollenLastUpdated FROM current_weather WHERE locationId = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
        assertEquals(20.0, cursor.getDouble(1), 0.001)
        assertNull(cursor.getString(2)) // locationName initially null for existing caches
        assertNull(cursor.getString(5)) // aqLastUpdated initially null
        assertNull(cursor.getString(6)) // pollenLastUpdated initially null
        cursor.close()

        // Verify we can update and insert new columns in v5
        db.execSQL(
            """
            UPDATE current_weather
            SET locationName = 'London', latitude = 51.5, longitude = -0.12,
                aqLastUpdated = '2026-09-05T09:00:00', pollenLastUpdated = '2026-09-05T06:00:00'
            WHERE locationId = 1
            """.trimIndent()
        )

        val updatedCursor = db.query("SELECT locationName, latitude, longitude, aqLastUpdated, pollenLastUpdated FROM current_weather WHERE locationId = 1")
        assertTrue(updatedCursor.moveToFirst())
        assertEquals("London", updatedCursor.getString(0))
        assertEquals(51.5, updatedCursor.getDouble(1), 0.001)
        assertEquals(-0.12, updatedCursor.getDouble(2), 0.001)
        assertEquals("2026-09-05T09:00:00", updatedCursor.getString(3))
        assertEquals("2026-09-05T06:00:00", updatedCursor.getString(4))
        updatedCursor.close()

        db.close()
    }
}
