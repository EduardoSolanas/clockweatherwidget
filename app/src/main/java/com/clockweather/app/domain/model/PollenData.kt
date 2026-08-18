package com.clockweather.app.domain.model

data class PollenType(
    val code: String, // "GRASS", "TREE", "WEED"
    val displayName: String,
    val inSeason: Boolean = false,
    val indexValue: Int? = null, // Universal Pollen Index 0..5
    val category: String? = null, // "None", "Very Low", "Low", "Moderate", "High", "Very High"
    val healthRecommendations: List<String> = emptyList()
)

data class PlantPollen(
    val code: String,
    val displayName: String,
    val inSeason: Boolean = false,
    val indexValue: Int? = null,
    val category: String? = null
)

data class PollenData(
    val grassPollen: PollenType? = null,
    val treePollen: PollenType? = null,
    val weedPollen: PollenType? = null,
    val dominantPlants: List<PlantPollen> = emptyList(),
    val healthRecommendations: List<String> = emptyList()
) {
    val maxIndex: Int?
        get() = listOfNotNull(
            grassPollen?.indexValue,
            treePollen?.indexValue,
            weedPollen?.indexValue
        ).maxOrNull()

    val maxCategory: String?
        get() {
            val max = maxIndex ?: return null
            return when {
                grassPollen?.indexValue == max -> grassPollen.category
                treePollen?.indexValue == max -> treePollen.category
                weedPollen?.indexValue == max -> weedPollen.category
                else -> null
            }
        }

    val hasData: Boolean
        get() = grassPollen != null || treePollen != null || weedPollen != null || dominantPlants.isNotEmpty()
}
