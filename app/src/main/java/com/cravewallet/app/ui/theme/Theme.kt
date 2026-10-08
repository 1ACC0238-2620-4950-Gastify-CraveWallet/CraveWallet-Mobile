package com.cravewallet.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Accent,
    onSecondary = OnSurface,
    secondaryContainer = PrimaryContainer,
    onSecondaryContainer = OnPrimaryContainer,
    tertiary = Accent,
    tertiaryContainer = AccentContainer,
    background = Background,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceContainerLowest = Surface,
    surfaceContainerLow = Surface,
    surfaceContainer = Surface,
    surfaceContainerHigh = Surface,
    surfaceContainerHighest = SurfaceVariant,
    inverseSurface = OnSurface,
    inverseOnSurface = Color.White,
    inversePrimary = PrimaryContainer,
    error = Error,
    onError = Color.White,
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = OnSurface,
)

// La app solo define tema claro: el Design System no incluye tokens oscuros.
@Composable
fun CraveWalletTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}
