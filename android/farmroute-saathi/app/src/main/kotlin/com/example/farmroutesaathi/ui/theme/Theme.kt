package com.example.farmroutesaathi.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================================================
// FARMROUTE SAATHI — MASTER THEME DEFINITION ("FROM SOIL TO MARKET")
// Master Design Reference: Login Page
// ============================================================================

// Light Theme Scheme (Master: Soil to Market)
private val LivingIntelligenceLightScheme = lightColorScheme(
    primary = SoilCropGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EFE7),
    onPrimaryContainer = SoilCropGreen,
    secondary = SoilFreshLeaf,
    onSecondary = Color.White,
    secondaryContainer = SoilFieldPaper,
    onSecondaryContainer = SoilDeepEarth,
    tertiary = SoilHarvestGold,
    onTertiary = SoilDeepEarth,
    background = SoilFieldPaper,
    onBackground = TextPrimaryLight,
    surface = SurfaceParchment,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceParchmentWarm,
    onSurfaceVariant = TextSecondaryLight,
    outline = Color(0xFF493A2D).copy(alpha = 0.16f),
    outlineVariant = Color(0xFF493A2D).copy(alpha = 0.08f),
    error = SemanticErrorRed,
    onError = Color.White
)

// Dark Theme Scheme (Night in Farmland: Rich Organic Charcoal-Green with Warm Cream Text)
private val LivingIntelligenceDarkScheme = darkColorScheme(
    primary = Color(0xFF5E9457),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF243527),
    onPrimaryContainer = Color(0xFFCBEBC7),
    secondary = SoilFreshLeaf,
    onSecondary = Color(0xFF121713),
    secondaryContainer = SurfaceDarkCard,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = SoilHarvestGold,
    onTertiary = Color(0xFF121713),
    background = OledBackgroundDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDarkCard,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceDarkCardHigh,
    onSurfaceVariant = TextSecondaryDark,
    outline = BorderDarkSubtle,
    outlineVariant = BorderDarkSubtle.copy(alpha = 0.5f),
    error = Color(0xFFEF4444),
    onError = Color.White
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
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
