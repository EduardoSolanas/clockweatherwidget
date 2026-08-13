package com.clockweather.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Weather for the current location is fetched by a WorkManager job, which Android
 * counts as background. From Android 10 the fused provider returns nothing there
 * unless location is allowed "all the time", so without that grant the widget can
 * only ever notice a move while the app itself is on screen.
 */
object BackgroundLocationAccess {

    /** Before Android 10 the foreground grant already covered background access. */
    private fun isSeparatePermission(sdkInt: Int): Boolean = sdkInt >= Build.VERSION_CODES.Q

    /** True when the app should ask the user to upgrade to "Allow all the time". */
    fun needsGrant(
        sdkInt: Int,
        foregroundGranted: Boolean,
        backgroundGranted: Boolean,
    ): Boolean = when {
        !isSeparatePermission(sdkInt) -> false
        // Android refuses the background grant until a foreground one exists.
        !foregroundGranted -> false
        else -> !backgroundGranted
    }

    fun needsGrant(context: Context): Boolean = needsGrant(
        sdkInt = Build.VERSION.SDK_INT,
        foregroundGranted = isForegroundGranted(context),
        backgroundGranted = isBackgroundGranted(context),
    )

    fun isForegroundGranted(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)

    fun isBackgroundGranted(context: Context): Boolean =
        !isSeparatePermission(Build.VERSION.SDK_INT) ||
            isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

    private fun isGranted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
