package com.example.farmroutesaathi.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Living Intelligence Light Color Scheme
private val LivingIntelligenceLightScheme = lightColorScheme(
    primary = ForestGreenDeep,
    onPrimary = WarmIvoryBackground,
    primaryContainer = ForestGreenPrimary,
    onPrimaryContainer = SurfaceParchment,
    secondary = FreshLeafGreen,
    onSecondary = SurfaceParchment,
    secondaryContainer = WarmIvoryBackground,
    onSecondaryContainer = ForestGreenDeep,
    tertiary = HarvestGold,
    onTertiary = TextPrimaryLight,
    background = WarmIvoryBackground,
    onBackground = TextPrimaryLight,
    surface = SurfaceParchment,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceParchmentWarm,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderParchment,
    error = SemanticErrorRed,
    onError = SurfaceParchment
)

// Living Intelligence Dark Color Scheme (OLED Optimized)
private val LivingIntelligenceDarkScheme = darkColorScheme(
    primary = FreshLimeAccent,
    onPrimary = OledBackgroundDark,
    primaryContainer = ForestGreenDeep,
    onPrimaryContainer = TextPrimaryDark,
    secondary = FreshLeafGreen,
    onSecondary = OledBackgroundDark,
    secondaryContainer = SurfaceDarkCard,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = HarvestGold,
    onTertiary = OledBackgroundDark,
    background = OledBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDarkCard,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkCardHigh,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDarkSubtle,
    error = SemanticErrorRed,
    onError = OledBackgroundDark
)

@Composable
fun FarmRouteSaathiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) LivingIntelligenceDarkScheme else LivingIntelligenceLightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
