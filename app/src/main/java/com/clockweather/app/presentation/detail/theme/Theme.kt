package com.clockweather.app.presentation.detail.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val DarkColorScheme = darkColorScheme(
    primary = Blue500,
    onPrimary = Color.White,
    primaryContainer = Blue700,
    onPrimaryContainer = Blue300,
    secondary = Teal500,
    onSecondary = Color.White,
    background = Blue900,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorRed,
    onError = Color.White,
    outline = Blue600
)

val LightColorScheme = lightColorScheme(
    primary = Blue600,
    onPrimary = Color.White,
    primaryContainer = Blue300,
    onPrimaryContainer = Blue900,
    secondary = Teal500,
    onSecondary = Color.White,
    background = Color(0xFFF8FAFF),
    onBackground = Blue900,
    surface = Color.White,
    onSurface = Blue900,
    surfaceVariant = Color(0xFFE8F0FE),
    onSurfaceVariant = Blue700,
    error = ErrorRed,
    onError = Color.White,
    outline = Blue400
)

