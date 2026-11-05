package com.geochron.atlas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DeepNavy = Color(0xFF0B2A4A)
val AtlasBlue = Color(0xFF1565C0)
val AtlasTeal = Color(0xFF00897B)
val AccentOrange = Color(0xFFF2994A)
val Parchment = Color(0xFFF7F3EA)
val MapOceanLight = Color(0xFFBFE3F2)
val MapOceanDark = Color(0xFF10293D)
val MapLandLight = Color(0xFFF3E9CF)

private val LightColors = lightColorScheme(
    primary = AtlasBlue,
    onPrimary = Color.White,
    secondary = AtlasTeal,
    tertiary = AccentOrange,
    background = Color(0xFFF4F7FA),
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB3E8),
    onPrimary = Color(0xFF0B2A4A),
    secondary = Color(0xFF4FC3B0),
    tertiary = AccentOrange,
    background = Color(0xFF0E1621),
    surface = Color(0xFF182635)
)

@Composable
fun GeoChronTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

/** Pastel tints cycled over states/countries so neighbours stay distinguishable (atlas style). */
val REGION_TINTS = listOf(
    Color(0xFFF6D8A0), Color(0xFFCFE8B8), Color(0xFFF9C6C6), Color(0xFFBFDDF2),
    Color(0xFFE3D2F2), Color(0xFFFBE3B9), Color(0xFFCDE7DC), Color(0xFFF5C9DE)
)
val REGION_TINTS_DARK = listOf(
    Color(0xFF6B5836), Color(0xFF47593B), Color(0xFF6E4444), Color(0xFF3D556E),
    Color(0xFF50456B), Color(0xFF6B5B36), Color(0xFF3C5A50), Color(0xFF6B4457)
)

/** Simple bilingual helper — Hindi is the default language of the app. */
fun t(hindi: Boolean, hi: String, en: String): String = if (hindi) hi else en
