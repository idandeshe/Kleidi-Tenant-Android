// The approved tenant-app look: white ground, ink text (#15202B), one blue accent (#1E5AA8).
// Light only — the mock-ups were approved as a light design; a dark variant is a later decision.

package one.lab5.kleidi.tenant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class TenantColors(
    /** Screen background. */
    val ground: Color,
    /** Primary text. */
    val ink: Color,
    /** The one accent: buttons, links, selected states. */
    val accent: Color,
    /** Soft accent fill (pills, selected choice background). */
    val accentSoft: Color,
    /** Captions, labels, secondary text — 4.5:1 on white. */
    val secondaryText: Color,
    /** Card borders, separators. */
    val hairline: Color,
    /** Slightly raised surface (disabled button, placeholders). */
    val surface: Color,
    val error: Color,
    val errorSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
)

private fun hex(value: Long) = Color(0xFF000000 or value)

val TenantInk = hex(0x15202B)
val TenantAccent = hex(0x1E5AA8)

val LightTenantColors = TenantColors(
    ground = Color.White,
    ink = TenantInk,
    accent = TenantAccent,
    accentSoft = hex(0xE8F0FB),
    secondaryText = hex(0x5B6673),
    hairline = hex(0xE3E8EE),
    surface = hex(0xF4F6F8),
    error = hex(0xB42318),
    errorSoft = hex(0xFEECEA),
    success = hex(0x146C43),
    successSoft = hex(0xE6F4EC),
    warning = hex(0x8A5A00),
    warningSoft = hex(0xFFF4D6),
)

val LocalTenantColors = staticCompositionLocalOf { LightTenantColors }

object TenantTheme {
    val colors: TenantColors
        @Composable @ReadOnlyComposable get() = LocalTenantColors.current
}

private val TenantTypography = Typography(
    displaySmall = TextStyle(fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

@Composable
fun TenantTheme(content: @Composable () -> Unit) {
    val colors = LightTenantColors
    val scheme = lightColorScheme(
        primary = colors.accent, onPrimary = Color.White,
        primaryContainer = colors.accentSoft, onPrimaryContainer = colors.accent,
        secondary = colors.secondaryText, onSecondary = Color.White,
        secondaryContainer = colors.accentSoft, onSecondaryContainer = colors.accent,
        background = colors.ground, onBackground = colors.ink,
        surface = colors.ground, onSurface = colors.ink,
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
        surfaceContainer = colors.surface, surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.hairline,
        surfaceVariant = colors.surface, onSurfaceVariant = colors.secondaryText,
        outline = colors.hairline, outlineVariant = colors.hairline,
        error = colors.error, onError = Color.White, errorContainer = colors.errorSoft, onErrorContainer = colors.error,
    )
    CompositionLocalProvider(LocalTenantColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = TenantTypography, content = content)
    }
}
