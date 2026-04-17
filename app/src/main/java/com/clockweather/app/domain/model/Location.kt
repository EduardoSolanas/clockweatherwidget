package com.clockweather.app.domain.model

data class Location(
    val id: Long = 0,
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timezone: String = "auto",
    val isCurrentLocation: Boolean = false,
    val area: String? = null
)
