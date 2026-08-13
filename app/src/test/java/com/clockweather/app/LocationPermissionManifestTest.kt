package com.clockweather.app

import android.content.pm.PackageManager
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Foreground location alone leaves the background weather worker blind on Android 10+,
 * so the widget can never notice the user has moved while another app is in front.
 */
@RunWith(RobolectricTestRunner::class)
class LocationPermissionManifestTest {

    @Test
    fun `the manifest asks for background location`() {
        val context = RuntimeEnvironment.getApplication()
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toList()

        assertTrue(
            "Declared permissions: $requested",
            requested.contains(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        )
    }
}
