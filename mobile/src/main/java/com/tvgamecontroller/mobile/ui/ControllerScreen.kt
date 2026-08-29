package com.tvgamecontroller.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvgamecontroller.mobile.ControllerUiState
import com.tvgamecontroller.mobile.R
import com.tvgamecontroller.mobile.net.DiscoveredTv
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
    onPickTv: (DiscoveredTv) -> Unit,
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
    Box(
        Modifier
            .fillMaxSize()
            .background(Navy),
    ) {
        if (state.showSettings) {
            SettingsScreen(
                state = state,
                onHost = onHost,
                onPin = onPin,
                onPickTv = onPickTv,
                onConnect = onConnect,
                onDisconnect = onDisconnect,
                onMotionMode = onMotionMode,
                onSensitivity = onSensitivity,
                onInvertY = onInvertY,
                onDeadzone = onDeadzone,
                onHaptics = onHaptics,
                onRecenter = onRecenter,
                onClose = onToggleSettings,
                onHid = onHid,
            )
        } else {
            GamepadScreen(
                state = state,
                onButton = onButton,
                onStick = onStick,
                onTrigger = onTrigger,
                onHat = onHat,
                onOpenSettings = onToggleSettings,
            )
        }
    }
}

@Composable
private fun GamepadScreen(
    state: ControllerUiState,
    onButton: (Int, Boolean) -> Unit,
    onStick: (Boolean, Float, Float) -> Unit,
    onTrigger: (Boolean, Float) -> Unit,
    onHat: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val pad = state.state
    val (hatX, hatY) = Hat.toVector(pad.hat)
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoulderButton(
                    label = "L2",
                    pressed = pad.isPressed(Buttons.L2),
                    contentDescription = stringResource(R.string.button_l2),
                    analog = true,
                    onPressed = { onButton(Buttons.L2, it) },
                    onAnalog = { onTrigger(true, it) },
                )
                ShoulderButton(
                    label = "L1",
                    pressed = pad.isPressed(Buttons.L1),
                    contentDescription = stringResource(R.string.button_l1),
                    onPressed = { onButton(Buttons.L1, it) },
                )
            }
            SettingsEntry(state = state, onOpenSettings = onOpenSettings)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShoulderButton(
                    label = "R1",
                    pressed = pad.isPressed(Buttons.R1),
                    contentDescription = stringResource(R.string.button_r1),
                    onPressed = { onButton(Buttons.R1, it) },
                )
                ShoulderButton(
                    label = "R2",
                    pressed = pad.isPressed(Buttons.R2),
                    contentDescription = stringResource(R.string.button_r2),
                    analog = true,
                    onPressed = { onButton(Buttons.R2, it) },
                    onAnalog = { onTrigger(false, it) },
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnalogStick(
                valueX = pad.leftStickX,
                valueY = pad.leftStickY,
                contentDescription = stringResource(R.string.left_stick),
            ) { x, y -> onStick(true, x, y) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Dpad(
                    hatX = hatX,
                    hatY = hatY,
                    contentDescription = stringResource(R.string.dpad),
                    onChanged = onHat,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShoulderButton(
                        label = "SEL",
                        pressed = pad.isPressed(Buttons.SELECT),
                        contentDescription = stringResource(R.string.button_select),
                        onPressed = { onButton(Buttons.SELECT, it) },
                    )
                    ShoulderButton(
                        label = "START",
                        pressed = pad.isPressed(Buttons.START),
                        contentDescription = stringResource(R.string.button_start),
                        onPressed = { onButton(Buttons.START, it) },
                    )
                    ShoulderButton(
                        label = "HOME",
                        pressed = pad.isPressed(Buttons.HOME),
                        contentDescription = stringResource(R.string.button_home),
                        onPressed = { onButton(Buttons.HOME, it) },
                    )
                }
            }
            Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                FaceButton(
                    label = "Y",
                    color = ButtonY,
                    pressed = pad.isPressed(Buttons.Y),
                    contentDescription = stringResource(R.string.button_y),
                    modifier = Modifier.align(Alignment.TopCenter),
                    onPressed = { onButton(Buttons.Y, it) },
                )
                FaceButton(
                    label = "X",
                    color = ButtonX,
                    pressed = pad.isPressed(Buttons.X),
                    contentDescription = stringResource(R.string.button_x),
                    modifier = Modifier.align(Alignment.CenterStart),
                    onPressed = { onButton(Buttons.X, it) },
                )
                FaceButton(
                    label = "B",
                    color = ButtonB,
                    pressed = pad.isPressed(Buttons.B),
                    contentDescription = stringResource(R.string.button_b),
                    modifier = Modifier.align(Alignment.CenterEnd),
                    onPressed = { onButton(Buttons.B, it) },
                )
                FaceButton(
                    label = "A",
                    color = ButtonA,
                    pressed = pad.isPressed(Buttons.A),
                    contentDescription = stringResource(R.string.button_a),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onPressed = { onButton(Buttons.A, it) },
                )
            }
            AnalogStick(
                valueX = pad.rightStickX,
                valueY = pad.rightStickY,
                contentDescription = stringResource(R.string.right_stick),
            ) { x, y -> onStick(false, x, y) }
        }
    }
}

@Composable
private fun SettingsEntry(
    state: ControllerUiState,
    onOpenSettings: () -> Unit,
) {
    val live = state.connected || hidConnected(state.hidStatus)
    val waiting = state.hidEnabled && !live
    val statusColor = when {
        live -> Cyan
        waiting -> Color(0xFFFFC14D)
        else -> Color(0xFF4A5578)
    }
    val statusText = when {
        live -> stringResource(R.string.status_ready)
        waiting -> state.hidStatus.ifBlank { stringResource(R.string.status_advertising) }
        else -> stringResource(R.string.status_not_connected)
    }
    val openLabel = stringResource(R.string.settings_open)
    IconButton(
        onClick = onOpenSettings,
        modifier = Modifier
            .size(48.dp)
            .semantics { contentDescription = "$openLabel. $statusText" },
        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(26.dp))
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(10.dp)
                    .background(statusColor, CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    state: ControllerUiState,
    onHost: (String) -> Unit,
    onPin: (String) -> Unit,
    onPickTv: (DiscoveredTv) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onMotionMode: (MotionMode) -> Unit,
    onSensitivity: (Float) -> Unit,
    onInvertY: (Boolean) -> Unit,
    onDeadzone: (Float) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onRecenter: () -> Unit,
    onClose: () -> Unit,
    onHid: (Boolean) -> Unit,
) {
    BackHandler(onBack = onClose)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Cyan,
        unfocusedBorderColor = Color(0xFF2A3354),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = TextDim,
        unfocusedLabelColor = TextDim,
        cursorColor = Cyan,
    )
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = Color(0xFF1C2540),
        labelColor = Color.White,
        selectedContainerColor = Cyan,
        selectedLabelColor = Navy,
    )
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.settings_title),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.settings_close),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsCard(
                title = stringResource(R.string.settings_connection),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                StatusLine(state)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.host,
                        onValueChange = onHost,
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.tv_address)) },
                        colors = fieldColors,
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.pin,
                        onValueChange = onPin,
                        modifier = Modifier.width(112.dp),
                        label = { Text(stringResource(R.string.pin)) },
                        colors = fieldColors,
                        singleLine = true,
                    )
                }
                if (state.discovered.isEmpty()) {
                    Text(stringResource(R.string.searching_tvs), color = TextDim, fontSize = 13.sp)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.discovered.forEach { tv ->
                            FilterChip(
                                selected = state.host == tv.host,
                                onClick = { onPickTv(tv) },
                                label = { Text("${tv.name} · ${tv.host}") },
                                colors = chipColors,
                            )
                        }
                    }
                }
                Button(
                    onClick = { if (state.connected) onDisconnect() else onConnect() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.connected) Color(0xFF2A3354) else Cyan,
                        contentColor = if (state.connected) Color.White else Navy,
                    ),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        if (state.connected) stringResource(R.string.action_disconnect) else stringResource(R.string.action_connect),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
                OutlinedButton(
                    onClick = onRecenter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Cyan),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.action_recenter), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Text(stringResource(R.string.action_recenter_hint), color = TextDim, fontSize = 13.sp)
            }
            SettingsCard(
                title = stringResource(R.string.settings_play),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Text(stringResource(R.string.motion), color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MotionMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.motionMode == mode,
                            onClick = { onMotionMode(mode) },
                            label = { Text(motionLabel(mode)) },
                            colors = chipColors,
                        )
                    }
                }
                Text(motionHint(state.motionMode), color = TextDim, fontSize = 13.sp)
                Text(stringResource(R.string.sensitivity, state.sensitivity), color = TextDim, fontSize = 13.sp)
                Slider(
                    value = state.sensitivity,
                    onValueChange = onSensitivity,
                    valueRange = 0.4f..3f,
                    modifier = Modifier.semantics {
                        contentDescription = "Sensitivity ${"%.1f".format(state.sensitivity)}"
                    },
                )
                Text(stringResource(R.string.deadzone, state.deadzone), color = TextDim, fontSize = 13.sp)
                Slider(
                    value = state.deadzone,
                    onValueChange = onDeadzone,
                    valueRange = 0f..0.3f,
                    modifier = Modifier.semantics {
                        contentDescription = "Stick deadzone ${"%.2f".format(state.deadzone)}"
                    },
                )
                SettingsToggle(stringResource(R.string.invert_look_y), state.invertY, onInvertY)
                SettingsToggle(stringResource(R.string.haptics), state.haptics, onHaptics)
                SettingsToggle(
                    title = stringResource(R.string.bluetooth_hid),
                    checked = state.hidEnabled,
                    onCheckedChange = onHid,
                    subtitle = state.hidStatus.ifBlank { stringResource(R.string.bluetooth_hid_hint) },
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .background(Panel, RoundedCornerShape(20.dp))
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        content()
    }
}

@Composable
private fun StatusLine(state: ControllerUiState) {
    val live = state.connected || hidConnected(state.hidStatus)
    val color = if (live) Cyan else TextDim
    val text = when {
        state.connected -> state.connectionLabel
        state.hidEnabled -> state.hidStatus
        else -> stringResource(R.string.status_not_connected)
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).background(if (live) Cyan else Color(0xFF4A5578), CircleShape))
        Text(text, color = color, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextDim, fontSize = 12.sp)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun motionLabel(mode: MotionMode): String = when (mode) {
    MotionMode.OFF -> stringResource(R.string.motion_off)
    MotionMode.GYRO_LOOK -> stringResource(R.string.motion_gyro)
    MotionMode.TILT_MOVE -> stringResource(R.string.motion_tilt)
}

@Composable
private fun motionHint(mode: MotionMode): String = when (mode) {
    MotionMode.OFF -> stringResource(R.string.motion_off_hint)
    MotionMode.GYRO_LOOK -> stringResource(R.string.motion_gyro_hint)
    MotionMode.TILT_MOVE -> stringResource(R.string.motion_tilt_hint)
}

private fun hidConnected(status: String): Boolean =
    status.contains("Connected", ignoreCase = true)
