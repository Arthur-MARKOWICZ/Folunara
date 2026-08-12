package com.arthur.ereader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arthur.ereader.domain.model.AppThemeMode

@Composable
fun EreaderTheme(
    mode: AppThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = EreaderTypography,
        shapes = EreaderShapes,
        content = content,
    )
}

private val Navy = Color(0xFF10204F)
private val NavyLight = Color(0xFFCBD4FF)
private val Cream = Color(0xFFFFF8E8)
private val CreamVariant = Color(0xFFF5E9CE)
private val Amber = Color(0xFFFFB703)
private val Ink = Color(0xFF1C1B1F)

private val LightColors = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF07143B),
    secondary = Color(0xFF5B5F71),
    secondaryContainer = Color(0xFFE0E3F5),
    tertiary = Color(0xFF7A5900),
    tertiaryContainer = Color(0xFFFFDEA1),
    surface = Cream,
    surfaceVariant = CreamVariant,
    background = Cream,
    onSurface = Ink,
    outline = Color(0xFF777680),
)

private val DarkColors = darkColorScheme(
    primary = NavyLight,
    onPrimary = Color(0xFF14245A),
    primaryContainer = Color(0xFF29396F),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFC4C6DA),
    secondaryContainer = Color(0xFF444655),
    tertiary = Amber,
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5C4300),
    surface = Color(0xFF121318),
    surfaceVariant = Color(0xFF292A30),
    background = Color(0xFF121318),
    onSurface = Color(0xFFE5E1E9),
    outline = Color(0xFF91909A),
)

private val EreaderShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

private val EreaderTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
)
