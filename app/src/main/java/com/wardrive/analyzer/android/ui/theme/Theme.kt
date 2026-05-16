package com.wardrive.analyzer.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NeonDark = darkColorScheme(
    primary = Color(0xFF00F5D4),
    onPrimary = Color(0xFF001414),
    secondary = Color(0xFF00C8FF),
    onSecondary = Color(0xFF00131A),
    background = Color(0xFF090312),
    onBackground = Color(0xFFE6FFFA),
    surface = Color(0xFF12051E),
    onSurface = Color(0xFFE6FFFA),
    surfaceVariant = Color(0xFF1B0A2A),
    outline = Color(0xFF00A3C4)
)

private val NeonTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 30.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.8.sp
    ),
    titleMedium = TextStyle(
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold
    )
)

@Composable
fun WardriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeonDark,
        typography = NeonTypography,
        content = content
    )
}
