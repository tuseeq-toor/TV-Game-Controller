import { Buttons, Hat, applyMotion, emptyState, encodeBinary, stateJson } from "./protocol.js";

const ui = {
  status: document.querySelector("#status"),
  host: document.querySelector("#host"),
  pin: document.querySelector("#pin"),
  connect: document.querySelector("#connect"),
  motion: document.querySelector("#motion"),
  sensitivity: document.querySelector("#sensitivity"),
  telemetry: document.querySelector("#telemetry"),
  settings: document.querySelector("#settings"),
  settingsToggle: document.querySelector("#settingsToggle"),
};

const pad = emptyState();
const sensors = { gx: 0, gy: 0, gz: 0, ax: 0, ay: 0, az: 0, pitch: 0, roll: 0 };
const settings = { mode: "gyro", sensitivity: 1.4, deadzone: 0.08, invertY: false, pitch0: 0 };
let socket = null;
let seq = 0;

function setStatus(text, ok = false) {
  ui.status.textContent = text;
  ui.status.classList.toggle("ok", ok);
}

function isFormField(target) {
  return Boolean(target?.closest?.("input, select, textarea"));
}

function preventPageZoom(event) {
  if (isFormField(event.target)) return;
  if (event.cancelable) event.preventDefault();
}

document.addEventListener("gesturestart", preventPageZoom, { passive: false });
document.addEventListener("gesturechange", preventPageZoom, { passive: false });
document.addEventListener("gestureend", preventPageZoom, { passive: false });
document.addEventListener("touchstart", (event) => {
  if (event.touches.length > 1) preventPageZoom(event);
}, { passive: false });
document.addEventListener("touchmove", preventPageZoom, { passive: false });
document.addEventListener("dblclick", preventPageZoom, { passive: false });

function connect() {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    socket.close();
    return;
  }
  const host = ui.host.value.trim() || location.hostname;
  const proto = location.protocol === "https:" ? "wss" : "ws";
  const port = location.port || (proto === "wss" ? "443" : "9842");
  const url = `${proto}://${host}:${port}/controller`;
  socket = new WebSocket(url);
  socket.binaryType = "arraybuffer";
  setStatus("Connecting…");
  ui.connect.textContent = "Disconnect";
  socket.onopen = () => {
    socket.send(JSON.stringify({ type: "hello", role: "controller", name: navigator.userAgent.slice(0, 40), protocol: 1 }));
    socket.send(JSON.stringify({ type: "auth", pin: ui.pin.value.trim() }));
  };
  socket.onmessage = (event) => {
    if (typeof event.data !== "string") return;
    const msg = JSON.parse(event.data);
    if (msg.type === "ready") setStatus(`Wi-Fi · ${msg.serverName}`, true);
    if (msg.type === "error") setStatus(msg.message || "Server error");
    if (msg.type === "rumble" && navigator.vibrate) navigator.vibrate(msg.ms || 40);
  };
  socket.onclose = () => {
    setStatus("Disconnected");
    ui.connect.textContent = "Connect";
  };
}

ui.connect.addEventListener("click", connect);
ui.motion.addEventListener("change", () => {
  settings.mode = ui.motion.value;
});
ui.sensitivity.addEventListener("input", () => {
  settings.sensitivity = Number(ui.sensitivity.value);
});
ui.settingsToggle.addEventListener("click", () => {
  const open = ui.settings.hasAttribute("hidden");
  ui.settings.toggleAttribute("hidden", !open);
  ui.settingsToggle.textContent = open ? "Hide" : "Setup";
});

function bindHold(el, onChange) {
  let pointerId = null;
  const start = (event) => {
    if (pointerId !== null) return;
    event.preventDefault();
    pointerId = event.pointerId;
    try { el.setPointerCapture(event.pointerId); } catch { /* some browsers reject synthetic pointers */ }
    el.classList.add("on");
    onChange(true);
  };
  const end = (event) => {
    if (event.pointerId !== pointerId) return;
    pointerId = null;
    el.classList.remove("on");
    onChange(false);
  };
  el.addEventListener("pointerdown", start);
  el.addEventListener("pointerup", end);
  el.addEventListener("pointercancel", end);
}

function bindStick(el, left) {
  const knob = el.querySelector(".knob");
  let pointerId = null;
  const emit = (x, y) => {
    if (left) {
      pad.lx = x;
      pad.ly = y;
    } else {
      pad.rx = x;
      pad.ry = y;
    }
    knob.style.left = `${31 + x * 24}%`;
    knob.style.top = `${31 + y * 24}%`;
  };
  const fromEvent = (event) => {
    const rect = el.getBoundingClientRect();
    const dx = (event.clientX - rect.left) / rect.width * 2 - 1;
    const dy = (event.clientY - rect.top) / rect.height * 2 - 1;
    const len = Math.hypot(dx, dy);
    const scale = len > 1 ? 1 / len : 1;
    emit(dx * scale, dy * scale);
  };
  el.addEventListener("pointerdown", (event) => {
    if (pointerId !== null) return;
    event.preventDefault();
    pointerId = event.pointerId;
    try { el.setPointerCapture(event.pointerId); } catch { /* some browsers reject synthetic pointers */ }
    fromEvent(event);
  });
  el.addEventListener("pointermove", (event) => {
    if (event.pointerId !== pointerId) return;
    event.preventDefault();
    fromEvent(event);
  });
  const reset = (event) => {
    if (event.pointerId !== pointerId) return;
    pointerId = null;
    emit(0, 0);
  };
  el.addEventListener("pointerup", reset);
  el.addEventListener("pointercancel", reset);
}

bindStick(document.querySelector("#leftStick"), true);
bindStick(document.querySelector("#rightStick"), false);

const dpad = { up: false, down: false, left: false, right: false };
function syncHat() {
  pad.hat = Hat.fromDpad(dpad.up, dpad.down, dpad.left, dpad.right);
}
document.querySelectorAll("[data-hat]").forEach((el) => {
  bindHold(el, (pressed) => {
    dpad[el.dataset.hat] = pressed;
    syncHat();
  });
});

document.querySelectorAll("[data-button]").forEach((el) => {
  bindHold(el, (pressed) => {
    const mask = Buttons[el.dataset.button];
    pad.buttons = pressed ? pad.buttons | mask : pad.buttons & ~mask;
    if (el.dataset.trigger === "lt") pad.lt = pressed ? 1 : 0;
    if (el.dataset.trigger === "rt") pad.rt = pressed ? 1 : 0;
  });
});

const keyStick = { lx: 0, ly: 0, rx: 0, ry: 0 };
const keyMap = {
  KeyW: () => { keyStick.ly = -1; },
  KeyS: () => { keyStick.ly = 1; },
  KeyA: () => { keyStick.lx = -1; },
  KeyD: () => { keyStick.lx = 1; },
  ArrowUp: () => { dpad.up = true; syncHat(); },
  ArrowDown: () => { dpad.down = true; syncHat(); },
  ArrowLeft: () => { dpad.left = true; syncHat(); },
  ArrowRight: () => { dpad.right = true; syncHat(); },
  KeyI: () => { keyStick.ry = -1; },
  KeyK: () => { keyStick.ry = 1; },
  KeyJ: () => { keyStick.rx = -1; },
  KeyL: () => { keyStick.rx = 1; },
  Space: () => { pad.buttons |= Buttons.A; },
  KeyU: () => { pad.buttons |= Buttons.Y; },
  KeyH: () => { pad.buttons |= Buttons.X; },
  KeyO: () => { pad.buttons |= Buttons.B; },
};
const keyUp = {
  KeyW: () => { keyStick.ly = 0; },
  KeyS: () => { keyStick.ly = 0; },
  KeyA: () => { keyStick.lx = 0; },
  KeyD: () => { keyStick.lx = 0; },
  ArrowUp: () => { dpad.up = false; syncHat(); },
  ArrowDown: () => { dpad.down = false; syncHat(); },
  ArrowLeft: () => { dpad.left = false; syncHat(); },
  ArrowRight: () => { dpad.right = false; syncHat(); },
  KeyI: () => { keyStick.ry = 0; },
  KeyK: () => { keyStick.ry = 0; },
  KeyJ: () => { keyStick.rx = 0; },
  KeyL: () => { keyStick.rx = 0; },
  Space: () => { pad.buttons &= ~Buttons.A; },
  KeyU: () => { pad.buttons &= ~Buttons.Y; },
  KeyH: () => { pad.buttons &= ~Buttons.X; },
  KeyO: () => { pad.buttons &= ~Buttons.B; },
};
window.addEventListener("keydown", (event) => {
  if (event.repeat) return;
  if (isFormField(event.target)) return;
  if (keyMap[event.code]) {
    event.preventDefault();
    keyMap[event.code]();
  }
});
window.addEventListener("keyup", (event) => {
  if (keyUp[event.code]) {
    event.preventDefault();
    keyUp[event.code]();
  }
});

document.querySelector("#recenter").addEventListener("click", async () => {
  settings.pitch0 = sensors.pitch;
  if (window.DeviceMotionEvent?.requestPermission) {
    await DeviceMotionEvent.requestPermission();
  }
  if (window.DeviceOrientationEvent?.requestPermission) {
    await DeviceOrientationEvent.requestPermission();
  }
});

window.addEventListener("devicemotion", (event) => {
  const rate = event.rotationRate || {};
  sensors.gx = ((rate.beta || 0) * Math.PI) / 180;
  sensors.gy = ((rate.alpha || 0) * Math.PI) / 180;
  sensors.gz = ((rate.gamma || 0) * Math.PI) / 180;
  const acc = event.accelerationIncludingGravity || {};
  sensors.ax = acc.x || 0;
  sensors.ay = acc.y || 0;
  sensors.az = acc.z || 0;
});

window.addEventListener("deviceorientation", (event) => {
  sensors.pitch = ((event.beta || 0) * Math.PI) / 180;
  sensors.roll = ((event.gamma || 0) * Math.PI) / 180;
});

function tick() {
  seq += 1;
  const next = applyMotion({
    ...pad,
    seq,
    lx: pad.lx || keyStick.lx,
    ly: pad.ly || keyStick.ly,
    rx: pad.rx || keyStick.rx,
    ry: pad.ry || keyStick.ry,
  }, sensors, settings);
  ui.telemetry.textContent =
    `Motion ${settings.mode}  ·  L(${next.lx.toFixed(2)},${next.ly.toFixed(2)})  R(${next.rx.toFixed(2)},${next.ry.toFixed(2)})`;
  if (socket && socket.readyState === WebSocket.OPEN) {
    try {
      socket.send(encodeBinary(next));
    } catch {
      socket.send(stateJson(next));
    }
  }
  if (window.opener) {
    window.opener.postMessage({ channel: "tvgamepad", state: next }, "*");
  }
  localStorage.setItem("tvgamepad.state", JSON.stringify(next));
  requestAnimationFrame(tick);
}

const params = new URLSearchParams(location.search);
if (params.get("host")) ui.host.value = params.get("host");
if (params.get("pin")) ui.pin.value = params.get("pin");
if (!ui.host.value) ui.host.value = location.hostname === "localhost" ? "localhost" : location.hostname;
tick();
