package com.pksafe.lock.manager.ui.theme

import androidx.compose.material3.MaterialTheme
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

@Composable
fun PKLockerTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false, // Force light mode — all screens use hardcoded light colors
    content: @Composable () -> Unit
) {
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
