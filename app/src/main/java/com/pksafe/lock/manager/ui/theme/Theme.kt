package com.pksafe.lock.manager.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = CardWhite,
    primaryContainer = BrandBlueLight,
    onPrimaryContainer = BrandBlueDark,
    secondary = TextBody,
    onSecondary = CardWhite,
    secondaryContainer = SurfaceGray,
    onSecondaryContainer = TextTitle,
    tertiary = Info,
    background = SoftBg,
    onBackground = TextTitle,
    surface = CardWhite,
    onSurface = TextTitle,
    surfaceVariant = SurfaceGray,
    onSurfaceVariant = TextMuted,
    outline = BorderLight,
    outlineVariant = BorderSoft,
    error = Danger,
    errorContainer = DangerLight,
    onError = CardWhite,
    onErrorContainer = Danger
)

private val DarkColorScheme = darkColorScheme(
    primary = BrandBlue,
    onPrimary = CardWhite,
    primaryContainer = BrandBlueDark,
    onPrimaryContainer = BrandBlueLight,
    secondary = TextMuted,
    onSecondary = CardWhite,
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = CardWhite,
    tertiary = Info,
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Danger,
    errorContainer = DangerLight,
    onError = CardWhite,
    onErrorContainer = Danger
)

@Composable
fun PKLockerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
