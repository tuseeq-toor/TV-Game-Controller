package com.tvgamecontroller.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun AnalogStick(
    valueX: Float,
    valueY: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp,
    onChanged: (Float, Float) -> Unit,
) {
    var dragging by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
                stateDescription = if (dragging) "moving" else "centered"
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onChanged(0f, 0f)
                    },
                    onDragCancel = {
                        dragging = false
                        onChanged(0f, 0f)
                    },
                    onDrag = { change, _ ->
                        val radius = this.size.width / 2f
                        val dx = (change.position.x - radius) / radius
                        val dy = (change.position.y - radius) / radius
                        val length = hypot(dx, dy)
                        val scale = if (length > 1f) 1f / length else 1f
                        onChanged((dx * scale).coerceIn(-1f, 1f), (dy * scale).coerceIn(-1f, 1f))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Color(0x221CE8C8))
            drawCircle(Color(0x551CE8C8), style = Stroke(width = 3.dp.toPx()))
            drawCircle(Color(0x14000000), radius = size.toPx() * 0.18f)
        }
        Box(
            Modifier
                .offset {
                    IntOffset((valueX * 40.dp.toPx()).roundToInt(), (valueY * 40.dp.toPx()).roundToInt())
                }
                .size(56.dp)
                .background(Color(0xFFE8F4FF), CircleShape),
        )
    }
}

@Composable
fun FaceButton(
    label: String,
    color: Color,
    pressed: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    onPressed: (Boolean) -> Unit,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .defaultMinSize(48.dp, 48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
                stateDescription = if (pressed) "pressed" else "released"
            }
            .background(if (pressed) color else color.copy(alpha = 0.88f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressed(true)
                        try {
                            awaitRelease()
                        } finally {
                            onPressed(false)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF0B1020), fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

@Composable
fun ShoulderButton(
    label: String,
    pressed: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
    analog: Boolean = false,
    onPressed: (Boolean) -> Unit,
    onAnalog: (Float) -> Unit = {},
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
            .size(width = 88.dp, height = 48.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
                stateDescription = if (pressed) "pressed" else "released"
            }
            .background(
                if (pressed) Color(0xFF1CE8C8) else Color(0xFF1C2540),
                RoundedCornerShape(16.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPressed(true)
                        if (analog) onAnalog(1f)
                        try {
                            awaitRelease()
                        } finally {
                            onPressed(false)
                            if (analog) onAnalog(0f)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun Dpad(
    hatX: Float,
    hatY: Float,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onChanged: (Boolean, Boolean, Boolean, Boolean) -> Unit,
) {
    Canvas(
        modifier = modifier
            .size(128.dp)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .pointerInput(Unit) {
                fun emit(position: Offset) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val dx = position.x - cx
                    val dy = position.y - cy
                    val dead = 18.dp.toPx()
                    if (hypot(dx, dy) < dead) {
                        onChanged(false, false, false, false)
                        return
                    }
                    onChanged(dy < -dead, dy > dead, dx < -dead, dx > dead)
                }
                detectDragGestures(
                    onDragStart = { emit(it) },
                    onDragEnd = { onChanged(false, false, false, false) },
                    onDragCancel = { onChanged(false, false, false, false) },
                    onDrag = { change, _ -> emit(change.position) },
                )
            },
    ) {
        val w = size.width
        val arm = w * 0.28f
        drawRoundRect(
            color = Color(0xFF1C2540),
            topLeft = Offset((w - arm) / 2f, 0f),
            size = Size(arm, w),
            cornerRadius = CornerRadius(18f, 18f),
        )
        drawRoundRect(
            color = Color(0xFF1C2540),
            topLeft = Offset(0f, (w - arm) / 2f),
            size = Size(w, arm),
            cornerRadius = CornerRadius(18f, 18f),
        )
        drawCircle(
            Color(0xFF1CE8C8),
            radius = 9.dp.toPx(),
            center = Offset(w / 2f + hatX * 24.dp.toPx(), w / 2f + hatY * 24.dp.toPx()),
        )
    }
}
