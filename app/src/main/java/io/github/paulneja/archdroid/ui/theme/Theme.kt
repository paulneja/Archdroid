package io.github.paulneja.archdroid.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.ui.graphics.Color
import io.github.paulneja.archdroid.data.model.VisualStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArchDroidTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    visualStyle: VisualStyle = VisualStyle.CHROME,
    performanceMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme(primary = AppAccent)
        else -> expressiveLightColorScheme()
    }
    val motion = rememberSystemMotion() && !performanceMode
    val accent = if (dynamicColor) base.primary else when (visualStyle) {
        VisualStyle.CHROME -> AppAccent
        VisualStyle.AURORA -> Color(0xFFBBA2FF)
        VisualStyle.EMBER -> Color(0xFFFFAD77)
    }
    val scheme = if (darkTheme) base.copy(
        primary = accent,
        onPrimary = AppBackground,
        primaryContainer = AppRaised,
        onPrimaryContainer = accent,
        secondary = accent,
        onSecondary = AppBackground,
        secondaryContainer = AppRaised,
        onSecondaryContainer = AppText,
        tertiary = accent,
        onTertiary = AppBackground,
        tertiaryContainer = AppRaised,
        onTertiaryContainer = AppText,
        background = AppBackground,
        onBackground = AppText,
        surface = AppSurface,
        onSurface = AppText,
        surfaceVariant = AppInput,
        onSurfaceVariant = AppMuted,
        surfaceTint = accent,
        surfaceDim = AppBackground,
        surfaceBright = AppRaised,
        surfaceContainerLowest = AppBackground,
        surfaceContainerLow = AppSurface,
        surfaceContainer = AppRaised,
        surfaceContainerHigh = AppInput,
        surfaceContainerHighest = AppInput,
        outline = AppBorder,
        outlineVariant = AppBorder,
        inverseSurface = AppText,
        inverseOnSurface = AppBackground,
    ) else base
    CompositionLocalProvider(
        LocalPerformanceMode provides performanceMode,
        LocalVisualStyle provides visualStyle,
        LocalAppMotion provides motion,
        LocalRippleConfiguration provides if (motion) RippleConfiguration() else null,
    ) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(8.dp),
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(10.dp),
                large = RoundedCornerShape(10.dp),
                extraLarge = RoundedCornerShape(10.dp),
            ),
            motionScheme = if (motion) MotionScheme.expressive() else StillMotion,
            content = content,
        )
    }
}
