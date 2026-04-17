package com.clockweather.app.data.repository

internal data class ResolvedCurrentLocationName(
    val value: String,
    val isSpecific: Boolean,
    val area: String? = null
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
        val area = orderedDistinctNames(
            locality,
            subAdminArea,
            adminArea
        ).firstOrNull { it != specificName }
        return ResolvedCurrentLocationName(
            value = specificName,
            isSpecific = true,
            area = area
        )
    }

    val areaName = orderedDistinctNames(
        subAdminArea,
        adminArea
    ).firstOrNull()

    if (areaName != null) {
        val broader = orderedDistinctNames(
            adminArea
        ).firstOrNull { it != areaName }
        return ResolvedCurrentLocationName(
            value = areaName,
            isSpecific = false,
            area = broader
        )
    }

    return ResolvedCurrentLocationName(
        value = fallbackLabel,
        isSpecific = false,
        area = null
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
