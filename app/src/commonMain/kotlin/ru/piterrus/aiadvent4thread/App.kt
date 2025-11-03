package ru.piterrus.aiadvent4thread

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Яркая сочная зеленая цветовая схема
private val GreenPrimary = Color(0xFF00C853)
private val GreenPrimaryVariant = Color(0xFF00E676)
private val GreenSecondary = Color(0xFF69F0AE)
private val GreenSecondaryVariant = Color(0xFF00E676)
private val GreenTertiary = Color(0xFFB9F6CA)
private val GreenBackground = Color(0xFF00E676)
private val GreenSurface = Color(0xFFFFFFFF)
private val GreenSurfaceVariant = Color(0xFFB9F6CA)

private val LightGreenColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = Color.White,
    primaryContainer = GreenTertiary,
    onPrimaryContainer = Color(0xFF004D40),
    secondary = GreenSecondary,
    onSecondary = Color(0xFF004D40),
    secondaryContainer = GreenSurfaceVariant,
    onSecondaryContainer = Color(0xFF004D40),
    tertiary = GreenSecondaryVariant,
    background = GreenBackground,
    onBackground = Color(0xFF1B1B1B),
    surface = GreenSurface,
    onSurface = Color(0xFF1B1B1B),
    surfaceVariant = GreenSurfaceVariant,
    onSurfaceVariant = Color(0xFF004D40)
)

@Composable
expect fun App(
    defaultApiKey: String = "",
    defaultFolderId: String = ""
)

@Composable
fun AppContent(
    defaultApiKey: String = "",
    defaultFolderId: String = "",
    chatScreenContent: @Composable (apiKey: String, catalogId: String) -> Unit
) {
    MaterialTheme(
        colorScheme = LightGreenColorScheme
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            chatScreenContent(
                defaultApiKey,
                defaultFolderId
            )
        }
    }
}

