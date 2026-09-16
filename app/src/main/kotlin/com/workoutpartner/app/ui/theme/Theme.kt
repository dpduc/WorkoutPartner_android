package com.workoutpartner.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricOrange,
    onPrimary = PureWhite,
    primaryContainer = ElectricOrangeDark,
    onPrimaryContainer = PureWhite,
    secondary = BrandCyan,
    onSecondary = DarkNavy,
    secondaryContainer = BrandCyanDark,
    onSecondaryContainer = PureWhite,
    background = DarkNavy,
    onBackground = PureWhite,
    surface = DarkNavySurface,
    onSurface = PureWhite,
    surfaceVariant = DarkNavyCard,
    onSurfaceVariant = MutedSlate,
    outline = DarkNavyBorder,
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricOrange,
    onPrimary = PureWhite,
    primaryContainer = ElectricOrangeLight,
    onPrimaryContainer = DarkNavy,
    secondary = BrandCyanDark,
    onSecondary = PureWhite,
    secondaryContainer = BrandCyanLight,
    onSecondaryContainer = DarkNavy,
    background = OffWhite,
    onBackground = DarkNavy,
    surface = LightSurface,
    onSurface = DarkNavy,
    surfaceVariant = LightCard,
    onSurfaceVariant = MutedSlate,
    outline = LightBorder,
)

@Composable
fun WorkoutPartnerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
