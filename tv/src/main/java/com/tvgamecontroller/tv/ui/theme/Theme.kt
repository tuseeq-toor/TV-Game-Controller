package com.tvgamecontroller.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF1CE8C8)
val Navy = Color(0xFF070B16)
val Panel = Color(0xFF121A30)
val TextDim = Color(0xFF9AA6C4)

@Composable
fun TvHostTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Cyan,
            background = Navy,
            surface = Panel,
            onBackground = Color.White,
        ),
        content = content,
    )
}
