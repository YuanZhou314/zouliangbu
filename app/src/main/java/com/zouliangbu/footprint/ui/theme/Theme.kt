package com.zouliangbu.footprint.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    secondary = TrailOrange,
    tertiary = SkyBlue
)

private val DarkColors = darkColorScheme(
    primary = ForestGreen,
    secondary = TrailOrange,
    tertiary = SkyBlue
)

@Composable
fun FootprintTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}