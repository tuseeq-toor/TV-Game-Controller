package com.tvgamecontroller.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF1CE8C8)
val Navy = Color(0xFF0B1020)
val Panel = Color(0xFF151C33)
val TextDim = Color(0xFF9AA6C4)
val ButtonA = Color(0xFF3DDA7A)
val ButtonB = Color(0xFFFF4D6D)
val ButtonX = Color(0xFF3D7EFF)
val ButtonY = Color(0xFFFFE14D)

private val scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Navy,
    background = Navy,
    surface = Panel,
    onBackground = Color.White,
    onSurface = Color.White,
    secondary = Color(0xFF7C8CFF),
)

@Composable
fun TvGamepadTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
