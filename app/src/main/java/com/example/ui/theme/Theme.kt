package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RiderSemanticColors(
    val deepBackground: Color,
    val navyBackground: Color,
    val glassCard: Color,
    val glassCardElevated: Color,
    val electricBlue: Color,
    val neonCyan: Color,
    val premiumOrange: Color,
    val successGreen: Color,
    val warningAmber: Color,
    val errorRed: Color,
    val subtleBorder: Color,
    val isDark: Boolean
)

val LocalRiderColors = staticCompositionLocalOf {
    RiderSemanticColors(
        deepBackground = RiderDeepBlack,
        navyBackground = RiderDarkNavy,
        glassCard = RiderSurfaceDark,
        glassCardElevated = RiderSurfaceElevated,
        electricBlue = RiderElectricBlue,
        neonCyan = RiderNeonCyan,
        premiumOrange = RiderOrange,
        successGreen = RiderSuccessGreen,
        warningAmber = RiderWarningAmber,
        errorRed = RiderErrorRed,
        subtleBorder = RiderGlassBorder,
        isDark = true
    )
}

private val RiderDarkColorScheme = darkColorScheme(
    primary = RiderNeonCyan,
    onPrimary = RiderDeepBlack,
    primaryContainer = Color(0xFF0A2A4A),
    onPrimaryContainer = RiderNeonCyan,
    secondary = RiderElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF10243E),
    onSecondaryContainer = Color(0xFFB3DAFF),
    tertiary = RiderOrange,
    onTertiary = RiderDeepBlack,
    tertiaryContainer = Color(0xFF3B2200),
    onTertiaryContainer = Color(0xFFFFD099),
    background = RiderDeepBlack,
    onBackground = RiderTextPrimary,
    surface = RiderDarkNavy,
    onSurface = RiderTextPrimary,
    surfaceVariant = RiderSurfaceDark,
    onSurfaceVariant = RiderTextSecondary,
    outline = Color(0xFF283852),
    error = RiderErrorRed,
    onError = Color.White
)

private val RiderLightColorScheme = lightColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF002B5C),
    secondary = Color(0xFF00838F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFF4F9),
    onSecondaryContainer = Color(0xFF00363D),
    tertiary = Color(0xFFD96B00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE4C4),
    onTertiaryContainer = Color(0xFF4A2100),
    background = RiderLightBg,
    onBackground = RiderLightTextPrimary,
    surface = RiderLightSurface,
    onSurface = RiderLightTextPrimary,
    surfaceVariant = RiderLightSurfaceVariant,
    onSurfaceVariant = RiderLightTextSecondary,
    outline = Color(0xFFCBD5E1),
    error = Color(0xFFD32F2F),
    onError = Color.White
)

@Composable
fun RiderBoosterTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) RiderDarkColorScheme else RiderLightColorScheme
    val semanticColors = if (darkTheme) {
        RiderSemanticColors(
            deepBackground = RiderDeepBlack,
            navyBackground = RiderDarkNavy,
            glassCard = RiderSurfaceDark.copy(alpha = 0.88f),
            glassCardElevated = RiderSurfaceElevated.copy(alpha = 0.94f),
            electricBlue = RiderElectricBlue,
            neonCyan = RiderNeonCyan,
            premiumOrange = RiderOrange,
            successGreen = RiderSuccessGreen,
            warningAmber = RiderWarningAmber,
            errorRed = RiderErrorRed,
            subtleBorder = Color(0x3300E5FF),
            isDark = true
        )
    } else {
        RiderSemanticColors(
            deepBackground = RiderLightBg,
            navyBackground = Color(0xFFE8EFF9),
            glassCard = RiderLightSurface.copy(alpha = 0.96f),
            glassCardElevated = RiderLightSurfaceVariant,
            electricBlue = Color(0xFF0066CC),
            neonCyan = Color(0xFF0097A7),
            premiumOrange = Color(0xFFE65100),
            successGreen = Color(0xFF00A854),
            warningAmber = Color(0xFFD97706),
            errorRed = Color(0xFFD32F2F),
            subtleBorder = Color(0x330066CC),
            isDark = false
        )
    }

    CompositionLocalProvider(LocalRiderColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = RiderTypography,
            content = content
        )
    }
}
