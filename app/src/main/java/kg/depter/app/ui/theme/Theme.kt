package kg.depter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF3346D3)
val BrandDark = Color(0xFF1F2C99)

@Immutable
data class DepterColors(
    val gave: Color,
    val got: Color,
    val gaveContainer: Color,
    val gotContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
)

private val LightExtra = DepterColors(
    gave = Color(0xFFD12F45),
    got = Color(0xFF12875A),
    gaveContainer = Color(0xFFFDE8EB),
    gotContainer = Color(0xFFE2F5EC),
    warning = Color(0xFFC86A00),
    warningContainer = Color(0xFFFFF0DC),
    heroStart = Color(0xFF3346D3),
    heroEnd = Color(0xFF5B3FD1),
    onHero = Color.White,
)

private val DarkExtra = DepterColors(
    gave = Color(0xFFFF8A96),
    got = Color(0xFF5ED6A0),
    gaveContainer = Color(0xFF3A1D23),
    gotContainer = Color(0xFF15332A),
    warning = Color(0xFFFFB45C),
    warningContainer = Color(0xFF3A2A12),
    heroStart = Color(0xFF26338F),
    heroEnd = Color(0xFF3E2A8F),
    onHero = Color.White,
)

private val LightScheme: ColorScheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE2E6FF),
    onPrimaryContainer = Color(0xFF0E1A66),
    secondary = Color(0xFF5A5F78),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E5F3),
    onSecondaryContainer = Color(0xFF191C2E),
    tertiary = Color(0xFF12875A),
    background = Color(0xFFF4F5FA),
    onBackground = Color(0xFF15171F),
    surface = Color(0xFFF4F5FA),
    onSurface = Color(0xFF15171F),
    surfaceVariant = Color(0xFFE6E8F2),
    onSurfaceVariant = Color(0xFF5B6072),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEEF5),
    surfaceContainerHighest = Color(0xFFE6E8F0),
    outline = Color(0xFFB9BDCC),
    outlineVariant = Color(0xFFDDE0EA),
    error = Color(0xFFC62839),
)

private val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFFB5BEFF),
    onPrimary = Color(0xFF0E1A66),
    primaryContainer = Color(0xFF2A379E),
    onPrimaryContainer = Color(0xFFE2E6FF),
    secondary = Color(0xFFC3C6DC),
    onSecondary = Color(0xFF2C3043),
    secondaryContainer = Color(0xFF2E3243),
    onSecondaryContainer = Color(0xFFE0E2F2),
    tertiary = Color(0xFF5ED6A0),
    background = Color(0xFF0F1117),
    onBackground = Color(0xFFE5E6EE),
    surface = Color(0xFF0F1117),
    onSurface = Color(0xFFE5E6EE),
    surfaceVariant = Color(0xFF262A36),
    onSurfaceVariant = Color(0xFFA9ADBD),
    surfaceContainerLowest = Color(0xFF0B0D12),
    surfaceContainerLow = Color(0xFF181B23),
    surfaceContainer = Color(0xFF1A1D26),
    surfaceContainerHigh = Color(0xFF22252F),
    surfaceContainerHighest = Color(0xFF2B2F3A),
    outline = Color(0xFF545A6B),
    outlineVariant = Color(0xFF2F3340),
    error = Color(0xFFFF8A96),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Tabular figures keep amounts aligned in lists. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

val LocalDepterColors = staticCompositionLocalOf { LightExtra }

object Depter {
    val colors: DepterColors
        @Composable get() = LocalDepterColors.current
}

@Composable
fun DepterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalDepterColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

val AmountLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
