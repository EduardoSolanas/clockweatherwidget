package com.clockweather.app.data.repository

internal data class ResolvedCurrentLocationName(
    val value: String,
    val isSpecific: Boolean
)

internal fun resolveCurrentLocationName(
    locality: String?,
    subLocality: String? = null,
    cityDistrict: String? = null,
    suburb: String? = null,
    neighbourhood: String? = null,
    subAdminArea: String?,
    adminArea: String?,
    fallbackLabel: String
): String {
    return resolveCurrentLocationNameDetails(
        locality = locality,
        subLocality = subLocality,
        cityDistrict = cityDistrict,
        suburb = suburb,
        neighbourhood = neighbourhood,
        subAdminArea = subAdminArea,
        adminArea = adminArea,
        fallbackLabel = fallbackLabel
    ).value
}

internal fun resolveCurrentLocationNameDetails(
    locality: String?,
    subLocality: String? = null,
    cityDistrict: String? = null,
    suburb: String? = null,
    neighbourhood: String? = null,
    subAdminArea: String?,
    adminArea: String?,
    fallbackLabel: String
): ResolvedCurrentLocationName {
    val specificName = orderedDistinctNames(
        subLocality,
        cityDistrict,
        locality,
        suburb,
        neighbourhood
    ).firstOrNull()

    if (specificName != null) {
        return ResolvedCurrentLocationName(
            value = specificName,
            isSpecific = true
        )
    }

    val areaName = orderedDistinctNames(
        subAdminArea,
        adminArea
    ).firstOrNull()

    if (areaName != null) {
        return ResolvedCurrentLocationName(
            value = areaName,
            isSpecific = false
        )
    }

    return ResolvedCurrentLocationName(
        value = fallbackLabel,
        isSpecific = false
    )
}

private fun orderedDistinctNames(vararg names: String?): List<String> {
    val resolved = ArrayList<String>()
    for (name in names) {
        val normalized = name?.trim()?.takeIf { it.isNotBlank() } ?: continue
        if (normalized !in resolved) {
            resolved += normalized
        }
    }
    return resolved
}

