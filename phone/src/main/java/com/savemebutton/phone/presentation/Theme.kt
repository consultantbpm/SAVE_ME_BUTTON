package com.savemebutton.phone.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB00020),
    onPrimary = Color.White,
    background = Color(0xFF101010),
    surface = Color(0xFF181818),
)

@Composable
fun SaveMeButtonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
