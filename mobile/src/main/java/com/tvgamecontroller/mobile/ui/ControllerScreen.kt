package com.tvgamecontroller.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvgamecontroller.mobile.ControllerUiState
import com.tvgamecontroller.mobile.ui.theme.ButtonA
import com.tvgamecontroller.mobile.ui.theme.ButtonB
import com.tvgamecontroller.mobile.ui.theme.ButtonX
import com.tvgamecontroller.mobile.ui.theme.ButtonY
import com.tvgamecontroller.mobile.ui.theme.Cyan
import com.tvgamecontroller.mobile.ui.theme.Navy
import com.tvgamecontroller.mobile.ui.theme.Panel
import com.tvgamecontroller.mobile.ui.theme.TextDim
import com.tvgamecontroller.protocol.Buttons
import com.tvgamecontroller.protocol.Hat
import com.tvgamecontroller.protocol.MotionMode

@Composable
fun ControllerScreen(
    state: ControllerUiState,
    onHost: (String) -> Unit,
    onPin: (String) -> Unit,
    onPickTv: (com.tvgamecontroller.mobile.net.DiscoveredTv) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onButton: (Int, Boolean) -> Unit,
    onStick: (Boolean, Float, Float) -> Unit,
    onTrigger: (Boolean, Float) -> Unit,
    onHat: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onMotionMode: (MotionMode) -> Unit,
    onSensitivity: (Float) -> Unit,
    onInvertY: (Boolean) -> Unit,
    onDeadzone: (Float) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onRecenter: () -> Unit,
    onToggleSettings: () -> Unit,
    onHid: (Boolean) -> Unit,
) {
    val pad = state.state
    val (hatX, hatY) = Hat.toVector(pad.hat)
    Column(
        Modifier
            .fillMaxSize()
            .background(Navy)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        TopBar(
            state = state,
            onConnect = onConnect,
            onDisconnect = onDisconnect,
            onToggleSettings = onToggleSettings,
            onRecenter = onRecenter,
        )
        AnimatedVisibility(state.showSettings) {
            SettingsPanel(
                state = state,
                onHost = onHost,
                onPin = onPin,
                onPickTv = onPickTv,
                onMotionMode = onMotionMode,
                onSensitivity = onSensitivity,
                onInvertY = onInvertY,
                onDeadzone = onDeadzone,
                onHaptics = onHaptics,
                onHid = onHid,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoulderButton("L2", pad.isPressed(Buttons.L2), analog = true, onPressed = { onButton(Buttons.L2, it) }, onAnalog = { onTrigger(true, it) })
                ShoulderButton("L1", pad.isPressed(Buttons.L1), onPressed = { onButton(Buttons.L1, it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoulderButton("R1", pad.isPressed(Buttons.R1), onPressed = { onButton(Buttons.R1, it) })
                ShoulderButton("R2", pad.isPressed(Buttons.R2), analog = true, onPressed = { onButton(Buttons.R2, it) }, onAnalog = { onTrigger(false, it) })
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnalogStick(pad.leftStickX, pad.leftStickY) { x, y -> onStick(true, x, y) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Dpad(hatX, hatY, onChanged = onHat)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShoulderButton("SEL", pad.isPressed(Buttons.SELECT), onPressed = { onButton(Buttons.SELECT, it) })
                    ShoulderButton("START", pad.isPressed(Buttons.START), onPressed = { onButton(Buttons.START, it) })
                    ShoulderButton("HOME", pad.isPressed(Buttons.HOME), onPressed = { onButton(Buttons.HOME, it) })
                }
            }
            Box(Modifier.width(168.dp).height(168.dp), contentAlignment = Alignment.Center) {
                FaceButton("Y", ButtonY, pad.isPressed(Buttons.Y), Modifier.align(Alignment.TopCenter), onPressed = { onButton(Buttons.Y, it) })
                FaceButton("X", ButtonX, pad.isPressed(Buttons.X), Modifier.align(Alignment.CenterStart), onPressed = { onButton(Buttons.X, it) })
                FaceButton("B", ButtonB, pad.isPressed(Buttons.B), Modifier.align(Alignment.CenterEnd), onPressed = { onButton(Buttons.B, it) })
                FaceButton("A", ButtonA, pad.isPressed(Buttons.A), Modifier.align(Alignment.BottomCenter), onPressed = { onButton(Buttons.A, it) })
            }
            AnalogStick(pad.rightStickX, pad.rightStickY) { x, y -> onStick(false, x, y) }
        }
        Spacer(Modifier.weight(0.4f))
        Text(
            "Motion ${state.motionMode.name.replace('_', ' ')}  ·  L(${fmt(pad.leftStickX)},${fmt(pad.leftStickY)})  R(${fmt(pad.rightStickX)},${fmt(pad.rightStickY)})",
            color = TextDim,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun TopBar(
    state: ControllerUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onToggleSettings: () -> Unit,
    onRecenter: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("TV Gamepad", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(state.connectionLabel, color = if (state.connected) Cyan else TextDim, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onRecenter) { Text("Recenter", color = Cyan) }
            TextButton(onClick = onToggleSettings) { Text(if (state.showSettings) "Hide" else "Setup", color = Cyan) }
            Button(
                onClick = { if (state.connected) onDisconnect() else onConnect() },
                colors = ButtonDefaults.buttonColors(containerColor = if (state.connected) Color(0xFF2A3354) else Cyan, contentColor = if (state.connected) Color.White else Navy),
            ) {
                Text(if (state.connected) "Disconnect" else "Connect")
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    state: ControllerUiState,
    onHost: (String) -> Unit,
    onPin: (String) -> Unit,
    onPickTv: (com.tvgamecontroller.mobile.net.DiscoveredTv) -> Unit,
    onMotionMode: (MotionMode) -> Unit,
    onSensitivity: (Float) -> Unit,
    onInvertY: (Boolean) -> Unit,
    onDeadzone: (Float) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onHid: (Boolean) -> Unit,
) {
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Cyan,
        unfocusedBorderColor = Color(0xFF2A3354),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = Cyan,
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Panel, RoundedCornerShape(16.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(state.host, onHost, modifier = Modifier.weight(1f), label = { Text("TV address") }, colors = fieldColors, singleLine = true)
            OutlinedTextField(state.pin, onPin, modifier = Modifier.width(110.dp), label = { Text("PIN") }, colors = fieldColors, singleLine = true)
        }
        if (state.discovered.isEmpty()) {
            Text("Searching this Wi-Fi for a TV Gamepad Host…", color = TextDim, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.discovered.forEach { tv ->
                    FilterChip(
                        selected = state.host == tv.host,
                        onClick = { onPickTv(tv) },
                        label = { Text("${tv.name} · ${tv.host}") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan, selectedLabelColor = Navy),
                    )
                }
            }
        }
        Text("Motion", color = Color.White, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MotionMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.motionMode == mode,
                    onClick = { onMotionMode(mode) },
                    label = { Text(mode.name.replace('_', ' ')) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Cyan, selectedLabelColor = Navy),
                )
            }
        }
        Text("Sensitivity ${"%.1f".format(state.sensitivity)}", color = TextDim, fontSize = 12.sp)
        Slider(state.sensitivity, onSensitivity, valueRange = 0.4f..3f)
        Text("Stick deadzone ${"%.2f".format(state.deadzone)}", color = TextDim, fontSize = 12.sp)
        Slider(state.deadzone, onDeadzone, valueRange = 0f..0.3f)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Invert look Y", color = Color.White)
            Switch(state.invertY, onInvertY)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Haptics", color = Color.White)
            Switch(state.haptics, onHaptics)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Bluetooth HID (play existing TV games)", color = Color.White)
                Text(state.hidStatus, color = TextDim, fontSize = 12.sp)
            }
            Switch(state.hidEnabled, onHid)
        }
    }
}

private fun fmt(value: Float): String = "%.2f".format(value)
