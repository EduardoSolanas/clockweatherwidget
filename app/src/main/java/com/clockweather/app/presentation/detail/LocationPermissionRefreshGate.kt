package com.clockweather.app.presentation.detail

/**
 * Decides whether a location-permission observation should force a refresh.
 *
 * The permission effect on the weather page fires on first composition as well as on a real
 * change, so opening the page with permission already granted used to force a download whose
 * cost nothing had asked for — the startup path already ensures freshness, and forcing ignores
 * the cache entirely. Startup gets one refresh owner by having this gate stay silent about a
 * permission that was granted before anyone looked.
 *
 * A grant that follows a denial is a real change and still refreshes: the page has no location
 * to have loaded from, so waiting for the next freshness check would leave it empty.
 */
class LocationPermissionRefreshGate {

    private var sawDenial = false

    fun shouldRefreshForPermissionChange(granted: Boolean): Boolean {
        val grantedAfterDenial = granted && sawDenial
        if (!granted) sawDenial = true
        return grantedAfterDenial
    }
}
