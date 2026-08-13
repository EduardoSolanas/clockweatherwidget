package com.clockweather.app.util

import android.Manifest
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * ACCESS_BACKGROUND_LOCATION only exists from Android 10. On the older releases this
 * app still supports (minSdk 26) the foreground grant already reaches background
 * work, so nothing must be asked for — and the permission constant must never be
 * queried on a platform that has never heard of it.
 */
@RunWith(RobolectricTestRunner::class)
class BackgroundLocationAccessSdkTest {

    private fun application(): Application = RuntimeEnvironment.getApplication()

    @Test
    @Config(sdk = [26])
    fun `android 8 needs no upgrade even with nothing granted`() {
        val context = application()

        assertTrue(BackgroundLocationAccess.isBackgroundGranted(context))
        assertFalse(BackgroundLocationAccess.needsGrant(context))
    }

    @Test
    @Config(sdk = [28])
    fun `android 9 needs no upgrade once location is granted`() {
        val context = application()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertTrue(BackgroundLocationAccess.isBackgroundGranted(context))
        assertFalse(BackgroundLocationAccess.needsGrant(context))
    }

    @Test
    @Config(sdk = [29])
    fun `android 10 asks for the upgrade once foreground location is granted`() {
        val context = application()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertFalse(BackgroundLocationAccess.isBackgroundGranted(context))
        assertTrue(BackgroundLocationAccess.needsGrant(context))
    }

    @Test
    @Config(sdk = [29])
    fun `android 10 stays quiet until foreground location is granted`() {
        assertFalse(BackgroundLocationAccess.needsGrant(application()))
    }

    @Test
    @Config(sdk = [34])
    fun `a granted background permission settles the prompt`() {
        val context = application()
        shadowOf(context).grantPermissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )

        assertTrue(BackgroundLocationAccess.isBackgroundGranted(context))
        assertFalse(BackgroundLocationAccess.needsGrant(context))
    }
}
