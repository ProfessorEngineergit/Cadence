@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.github.professorengineergit.cadence

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFFA83D2C), onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD0), onPrimaryContainer = Color(0xFF4A160D),
    secondary = Color(0xFF655078), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE9DDF7), onSecondaryContainer = Color(0xFF30243D),
    tertiary = Color(0xFF38634C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD8EBD9), onTertiaryContainer = Color(0xFF173A28),
    background = Color(0xFFFBF8F3), onBackground = Color(0xFF29242C),
    surface = Color(0xFFFBF8F3), onSurface = Color(0xFF29242C),
    surfaceVariant = Color(0xFFEAE3DD), onSurfaceVariant = Color(0xFF6C6267),
    outline = Color(0xFF82767C), outlineVariant = Color(0xFFDBD2D3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3EFE9),
    surfaceContainer = Color(0xFFEFEAE4),
    surfaceContainerHigh = Color(0xFFE9E4DE),
    surfaceContainerHighest = Color(0xFFE4DED8),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4A3), onPrimary = Color(0xFF641D10),
    primaryContainer = Color(0xFF82301F), onPrimaryContainer = Color(0xFFFFDAD0),
    secondary = Color(0xFFD7BEEB), onSecondary = Color(0xFF372545),
    secondaryContainer = Color(0xFF4B3A5C), onSecondaryContainer = Color(0xFFEEDCFF),
    tertiary = Color(0xFFAACFB2), onTertiary = Color(0xFF123722),
    tertiaryContainer = Color(0xFF2A4E37), onTertiaryContainer = Color(0xFFC6ECCE),
    background = Color(0xFF19161C), onBackground = Color(0xFFEDE5EB),
    surface = Color(0xFF19161C), onSurface = Color(0xFFEDE5EB),
    surfaceVariant = Color(0xFF4C4449), onSurfaceVariant = Color(0xFFD1C2CB),
    outline = Color(0xFF9B8C95), outlineVariant = Color(0xFF4C4449),
    surfaceContainerLowest = Color(0xFF131016), surfaceContainerLow = Color(0xFF211D24),
    surfaceContainer = Color(0xFF27232A), surfaceContainerHigh = Color(0xFF312D34),
    surfaceContainerHighest = Color(0xFF3C373F),
)

@Composable
fun CadenceTheme(state: CadenceState, content: @Composable () -> Unit) {
    val dark = when (state.theme) { AppTheme.SYSTEM -> isSystemInDarkTheme(); AppTheme.LIGHT -> false; AppTheme.DARK -> true }
    val colors = if (state.dynamicColors && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else if (dark) DarkColors else LightColors
    val base = TextStyle(fontFamily = FontFamily.SansSerif)
    MaterialExpressiveTheme(
        colorScheme = colors,
        motionScheme = MotionScheme.expressive(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8), small = RoundedCornerShape(16),
            medium = RoundedCornerShape(20), large = RoundedCornerShape(28), extraLarge = RoundedCornerShape(36),
        ),
        typography = Typography(
            displayLarge = base.copy(fontSize = 62.sp, lineHeight = 70.sp, fontWeight = FontWeight.Medium, letterSpacing = (-3).sp, fontFeatureSettings = "tnum"),
            displayMedium = base.copy(fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.Medium, letterSpacing = (-2).sp),
            headlineLarge = base.copy(fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.3).sp),
            headlineMedium = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp),
            headlineSmall = base.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
            titleLarge = base.copy(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
            titleMedium = base.copy(fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 21.sp),
            bodySmall = base.copy(fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
            labelSmall = base.copy(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
        ),
        content = content,
    )
}
