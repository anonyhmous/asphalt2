package com.asphalt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

private fun schemeOf(p: AppPalette) = if (p.isDark) {
    darkColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryTint,
        onPrimaryContainer = p.textPrimary,
        secondary = p.textSecondary,
        onSecondary = p.onPrimary,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.card,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceVariant,
        onSurfaceVariant = p.textSecondary,
        outline = p.border,
        error = p.reject
    )
} else {
    lightColorScheme(
        primary = p.primary,
        onPrimary = p.onPrimary,
        primaryContainer = p.primaryTint,
        onPrimaryContainer = p.textPrimary,
        secondary = p.textSecondary,
        onSecondary = p.onPrimary,
        background = p.background,
        onBackground = p.textPrimary,
        surface = p.card,
        onSurface = p.textPrimary,
        surfaceVariant = p.surfaceVariant,
        onSurfaceVariant = p.textSecondary,
        outline = p.border,
        error = p.reject
    )
}

/**
 * الوضع (نهاري/ليلي) بييجي من ThemeState، فالتحويل من الزرار بيطبق على
 * كل الشاشات والكارت العايم في نفس اللحظة.
 */
@Composable
fun MyApplicationTheme(content: @Composable () -> Unit) {
    val palette = ThemeState.palette
    val colorScheme = remember(palette) { schemeOf(palette) }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
