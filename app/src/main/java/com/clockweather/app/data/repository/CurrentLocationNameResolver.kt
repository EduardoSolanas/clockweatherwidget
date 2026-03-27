package com.clockweather.app.data.repository

internal fun resolveCurrentLocationName(
    locality: String?,
    subAdminArea: String?,
    adminArea: String?,
    fallbackLabel: String
): String {
    return locality?.takeIf { it.isNotBlank() }
        ?: subAdminArea?.takeIf { it.isNotBlank() }
        ?: adminArea?.takeIf { it.isNotBlank() }
        ?: fallbackLabel
}

