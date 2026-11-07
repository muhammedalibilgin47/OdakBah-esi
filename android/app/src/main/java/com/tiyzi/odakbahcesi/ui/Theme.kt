package com.tiyzi.odakbahcesi.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Uygulama temaları. Orman ücretsiz, diğerleri Premium. */
enum class AppTheme(
    val label: String,
    val emoji: String,
    val premium: Boolean,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val dark: Boolean = false,
) {
    ORMAN("Orman", "🌲", false, Color(0xFF2E7D32), Color(0xFF81C784), Color(0xFFF1F8E9)),
    OKYANUS("Okyanus", "🌊", true, Color(0xFF0277BD), Color(0xFF4FC3F7), Color(0xFFE1F5FE)),
    GUNBATIMI("Gün Batımı", "🌅", true, Color(0xFFE65100), Color(0xFFFFB74D), Color(0xFFFFF3E0)),
    LAVANTA("Lavanta", "💜", true, Color(0xFF6A1B9A), Color(0xFFBA68C8), Color(0xFFF3E5F5)),
    GECE("Gece", "🌙", true, Color(0xFF80CBC4), Color(0xFF4DB6AC), Color(0xFF101418), dark = true);

    companion object {
        fun of(name: String): AppTheme = entries.firstOrNull { it.name == name } ?: ORMAN
    }
}

@Composable
fun FocusTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val scheme = if (theme.dark) {
        darkColorScheme(
            primary = theme.primary,
            onPrimary = Color(0xFF00201C),
            secondary = theme.secondary,
            background = theme.background,
            surface = Color(0xFF1B2126),
            surfaceVariant = Color(0xFF263038),
            primaryContainer = Color(0xFF1F3B38),
            onPrimaryContainer = Color(0xFFCFF3EE),
        )
    } else {
        lightColorScheme(
            primary = theme.primary,
            onPrimary = Color.White,
            secondary = theme.secondary,
            background = theme.background,
            surface = Color.White,
            primaryContainer = theme.secondary.copy(alpha = 0.30f),
            onPrimaryContainer = theme.primary,
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
