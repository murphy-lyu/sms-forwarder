package com.murphy.smsforwarder.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    primaryContainer = Mint,
    onPrimaryContainer = DeepTeal,
    secondary = Teal,
    secondaryContainer = Color(0xFFD5F5EC),
    onSecondaryContainer = Color(0xFF075B4A),
    tertiary = IMessageBlue,
    tertiaryContainer = Color(0xFFD7E9FF),
    onTertiaryContainer = Color(0xFF004887),
    error = Coral,
    background = WarmSurface,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE5ECE8),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFFB7C3BE)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF68D5C6),
    onPrimary = Color(0xFF003735),
    primaryContainer = Color(0xFF0E4F4C),
    onPrimaryContainer = Color(0xFFB1EFE5),
    secondary = Color(0xFF52D6B4),
    secondaryContainer = Color(0xFF075B4A),
    onSecondaryContainer = Color(0xFFB8F3E2),
    tertiary = Color(0xFF75B8FF),
    tertiaryContainer = Color(0xFF004A82),
    onTertiaryContainer = Color(0xFFD4E8FF),
    error = Color(0xFFFFB4A0),
    errorContainer = Color(0xFF6A2D1F),
    onErrorContainer = Color(0xFFFFDAD0),
    background = Color(0xFF101719),
    onBackground = Color(0xFFE0E7EA),
    surface = Color(0xFF192124),
    onSurface = Color(0xFFE0E7EA),
    surfaceVariant = Color(0xFF263034),
    onSurfaceVariant = Color(0xFFBAC4C8),
    outline = Color(0xFF849095)
)

@Composable
fun SMSForwarderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
