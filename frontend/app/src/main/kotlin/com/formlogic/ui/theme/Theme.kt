package com.formlogic.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand palette ─────────────────────────────────────────────────────────────
val Purple       = Color(0xFF6C63FF)
val PurpleLight  = Color(0xFF9D97FF)
val PurpleDark   = Color(0xFF4A43CC)
val Teal         = Color(0xFF43D9AD)
val Coral        = Color(0xFFFF6584)
val Amber        = Color(0xFFFFB547)
val BgDark       = Color(0xFF090A11)
val BgCard       = Color(0xFF12131F)
val BgCardAlt    = Color(0xFF1A1B2E)
val TextPrimary  = Color(0xFFF0F0FF)
val TextSecondary = Color(0xFF8080A0)
val BorderColor  = Color(0xFF1E1F35)

private val darkScheme = darkColorScheme(
    primary              = Purple,
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFF1E1B4B),
    onPrimaryContainer   = PurpleLight,
    secondary            = Teal,
    onSecondary          = Color(0xFF003D2E),
    secondaryContainer   = Color(0xFF003D2E),
    onSecondaryContainer = Teal,
    tertiary             = Coral,
    onTertiary           = Color.White,
    background           = BgDark,
    onBackground         = TextPrimary,
    surface              = BgCard,
    onSurface            = TextPrimary,
    surfaceVariant       = BgCardAlt,
    onSurfaceVariant     = TextSecondary,
    outline              = BorderColor,
    error                = Color(0xFFFF4757),
    onError              = Color.White,
)

@Composable
fun FormLogicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkScheme,
        typography  = Typography(),
        content     = content
    )
}
