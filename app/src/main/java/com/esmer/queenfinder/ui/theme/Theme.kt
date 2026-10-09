package com.esmer.queenfinder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Honey,
    secondary = CombLight,
    tertiary = HoneyDark,
    background = Smoke,
    surface = Smoke,
)

private val LightColorScheme = lightColorScheme(
    primary = HoneyDark,
    secondary = Comb,
    tertiary = Honey,
)

@Composable
fun ESMERQUEENFINDERTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
