package com.tvgamecontroller.tv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvgamecontroller.protocol.Buttons
import com.tvgamecontroller.tv.BtPadReading
import com.tvgamecontroller.tv.HostUiState
import com.tvgamecontroller.tv.game.OrbHuntSnapshot
import com.tvgamecontroller.tv.ui.theme.Cyan
import com.tvgamecontroller.tv.ui.theme.Navy
import com.tvgamecontroller.tv.ui.theme.Panel
import com.tvgamecontroller.tv.ui.theme.TextDim

@Composable
fun TvHome(state: HostUiState) {
    Row(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(28.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        Column(
            Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .background(Panel, RoundedCornerShape(24.dp))
                .padding(24.dp),
        ) {
            Text("TV Gamepad Host", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Install the phone app, or scan the QR code to use the browser controller.", color = TextDim, fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            InfoLine("Wi-Fi address", "${state.host}:${state.port}")
            InfoLine("Pairing PIN", state.pin)
            InfoLine("Web controller", state.webUrl)
            InfoLine("Controllers", if (state.clients.isEmpty()) "Waiting…" else state.clients.joinToString())
            Spacer(Modifier.height(16.dp))
            val qr = remember(state.webUrl) { qrImage(state.webUrl) }
            Image(qr, contentDescription = "QR code for the phone controller", modifier = Modifier.size(220.dp))
            Spacer(Modifier.height(12.dp))
            Text("Same Wi-Fi as this TV. Phone landscape works best.", color = TextDim, fontSize = 14.sp)
        }
        Column(Modifier.weight(0.58f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Playfield(state.game, Modifier.weight(1f).fillMaxWidth())
            InputTelemetry(state)
            BtPadTester(state.btPad)
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.padding(bottom = 10.dp)) {
        Text(label.uppercase(), color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Playfield(game: OrbHuntSnapshot, modifier: Modifier) {
    Box(modifier.background(Color(0xFF0E1630), RoundedCornerShape(24.dp))) {
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val w = size.width
            val h = size.height
            drawRect(Color(0xFF0A1226))
            game.targets.forEach { target ->
                drawCircle(Color(0xFFFFC857), radius = 16f, center = Offset(target.x * w, target.y * h))
            }
            game.shots.forEach { shot ->
                drawCircle(Color.White, radius = 6f, center = Offset(shot.x * w, shot.y * h))
            }
            val px = game.player.x * w
            val py = game.player.y * h
            drawCircle(Cyan, radius = 22f, center = Offset(px, py))
            drawLine(
                Color.White,
                start = Offset(px, py),
                end = Offset(px + game.aim.x * 54f, py + game.aim.y * 54f),
                strokeWidth = 5f,
            )
        }
        Row(
            Modifier
                .align(Alignment.TopStart)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("ORB HUNT", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("Score ${game.score}", color = Cyan, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (game.combo > 1) {
                Text("Combo x${game.combo}", color = Color(0xFFFFC857), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
        Text(
            game.hint,
            color = TextDim,
            modifier = Modifier.align(Alignment.BottomStart).padding(18.dp),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun InputTelemetry(state: HostUiState) {
    val pad = state.pad
    Row(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StickPreview("L", pad.leftStickX, pad.leftStickY)
        StickPreview("R", pad.rightStickX, pad.rightStickY)
        Column(Modifier.weight(1f)) {
            Text(
                "Buttons ${if (pad.buttons == 0) "—" else com.tvgamecontroller.protocol.Buttons.names(pad.buttons).joinToString()}",
                color = Color.White,
                fontSize = 16.sp,
            )
            Text(
                "Gyro ${fmt(pad.gyroX)}  ${fmt(pad.gyroY)}  ${fmt(pad.gyroZ)}   Motion ${if (pad.motionEnabled) "on" else "off"}",
                color = TextDim,
                fontSize = 14.sp,
            )
            Text(
                "A shoot  ·  gyro look moves the right stick  ·  tilt move drives the left stick",
                color = TextDim,
                fontSize = 13.sp,
            )
        }
        TriggerBar(pad.leftTrigger, pad.rightTrigger)
        val a = pad.isPressed(Buttons.A)
        Box(
            Modifier
                .size(42.dp)
                .background(if (a) Color(0xFF3DDA7A) else Color(0xFF22304F), RoundedCornerShape(21.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = if (a) Navy else Color.White, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * Shows the raw axis and button values Android receives from a paired
 * Bluetooth gamepad — exactly what games see. Use it to verify the phone's
 * Bluetooth pad: press R2 and GAS/RY/RT should move to 1.00.
 */
@Composable
private fun BtPadTester(pad: BtPadReading) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text("BLUETOOTH PAD TESTER", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        if (!pad.seen) {
            Text(
                "Pair the phone over Bluetooth, then press any button or trigger — raw values the TV receives appear here.",
                color = TextDim,
                fontSize = 14.sp,
            )
        } else {
            if (pad.deviceName.isNotEmpty() || pad.lastButton.isNotEmpty()) {
                Text(
                    buildString {
                        if (pad.deviceName.isNotEmpty()) append(pad.deviceName)
                        if (pad.lastButton.isNotEmpty()) {
                            if (isNotEmpty()) append("   ·   ")
                            append("last button ${pad.lastButton}")
                        }
                        if (pad.pressedButtons.isNotEmpty()) {
                            append("   ·   held ${pad.pressedButtons.joinToString()}")
                        }
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
            if (pad.axes.isNotEmpty()) {
                Text(
                    pad.axes.joinToString("  ") { (name, value) -> "$name ${fmt(value)}" },
                    color = Cyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StickPreview(label: String, x: Float, y: Float) {
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x3322E3C6))
            drawCircle(Color(0x661CE8C8), style = Stroke(3.dp.toPx()))
            drawCircle(
                Cyan,
                radius = 8.dp.toPx(),
                center = Offset(size.width / 2f + x * 20.dp.toPx(), size.height / 2f + y * 20.dp.toPx()),
            )
        }
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TriggerBar(left: Float, right: Float) {
    Column {
        Text("LT ${fmt(left)}  RT ${fmt(right)}", color = TextDim, fontSize = 12.sp)
        Canvas(Modifier.width(120.dp).height(10.dp)) {
            drawRoundRect(Color(0xFF22304F))
            drawRoundRect(Cyan.copy(alpha = 0.85f), size = androidx.compose.ui.geometry.Size(size.width * ((left + right) / 2f), size.height))
        }
    }
}

private fun fmt(value: Float): String = "%.2f".format(value)
