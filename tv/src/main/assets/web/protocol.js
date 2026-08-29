export const PROTOCOL_VERSION = 1;
export const DEFAULT_PORT = 9842;

export const Buttons = {
  A: 1 << 0,
  B: 1 << 1,
  X: 1 << 2,
  Y: 1 << 3,
  L1: 1 << 4,
  R1: 1 << 5,
  L3: 1 << 6,
  R3: 1 << 7,
  SELECT: 1 << 8,
  START: 1 << 9,
  HOME: 1 << 10,
  L2: 1 << 11,
  R2: 1 << 12,
};

export const Hat = {
  NORTH: 0,
  NORTHEAST: 1,
  EAST: 2,
  SOUTHEAST: 3,
  SOUTH: 4,
  SOUTHWEST: 5,
  WEST: 6,
  NORTHWEST: 7,
  NEUTRAL: 8,
  fromDpad(up, down, left, right) {
    const ud = up && !down ? 1 : down && !up ? -1 : 0;
    const lr = right && !left ? 1 : left && !right ? -1 : 0;
    if (ud === 1 && lr === 0) return this.NORTH;
    if (ud === 1 && lr === 1) return this.NORTHEAST;
    if (ud === 0 && lr === 1) return this.EAST;
    if (ud === -1 && lr === 1) return this.SOUTHEAST;
    if (ud === -1 && lr === 0) return this.SOUTH;
    if (ud === -1 && lr === -1) return this.SOUTHWEST;
    if (ud === 0 && lr === -1) return this.WEST;
    if (ud === 1 && lr === -1) return this.NORTHWEST;
    return this.NEUTRAL;
  },
};

export function emptyState() {
  return {
    seq: 0,
    lx: 0,
    ly: 0,
    rx: 0,
    ry: 0,
    lt: 0,
    rt: 0,
    buttons: 0,
    hat: Hat.NEUTRAL,
    gx: 0,
    gy: 0,
    gz: 0,
    ax: 0,
    ay: 0,
    az: 0,
    motion: false,
  };
}

export function stateJson(state) {
  return JSON.stringify({ type: "state", ...state });
}

export function parseState(text) {
  const msg = typeof text === "string" ? JSON.parse(text) : text;
  if (!msg || msg.type !== "state") return null;
  return {
    seq: Number(msg.seq) || 0,
    lx: clamp(msg.lx, -1, 1),
    ly: clamp(msg.ly, -1, 1),
    rx: clamp(msg.rx, -1, 1),
    ry: clamp(msg.ry, -1, 1),
    lt: clamp(msg.lt, 0, 1),
    rt: clamp(msg.rt, 0, 1),
    buttons: Number(msg.buttons) || 0,
    hat: clamp(msg.hat, 0, 8),
    gx: Number(msg.gx) || 0,
    gy: Number(msg.gy) || 0,
    gz: Number(msg.gz) || 0,
    ax: Number(msg.ax) || 0,
    ay: Number(msg.ay) || 0,
    az: Number(msg.az) || 0,
    motion: Boolean(msg.motion),
  };
}

export function encodeBinary(state) {
  const buf = new ArrayBuffer(40);
  const view = new DataView(buf);
  view.setUint8(0, 84); // T
  view.setUint8(1, 71); // G
  view.setUint8(2, 67); // C
  view.setUint8(3, 49); // 1
  view.setUint32(4, state.seq >>> 0, true);
  view.setUint32(8, state.buttons >>> 0, true);
  view.setUint8(12, state.hat);
  view.setUint8(13, state.motion ? 1 : 0);
  view.setInt16(16, stick(state.lx), true);
  view.setInt16(18, stick(state.ly), true);
  view.setInt16(20, stick(state.rx), true);
  view.setInt16(22, stick(state.ry), true);
  view.setInt16(24, unit(state.lt), true);
  view.setInt16(26, unit(state.rt), true);
  view.setInt16(28, scaled(state.gx, 1000), true);
  view.setInt16(30, scaled(state.gy, 1000), true);
  view.setInt16(32, scaled(state.gz, 1000), true);
  view.setInt16(34, scaled(state.ax, 100), true);
  view.setInt16(36, scaled(state.ay, 100), true);
  view.setInt16(38, scaled(state.az, 100), true);
  return new Uint8Array(buf);
}

export function decodeBinary(bytes) {
  if (!bytes || bytes.length < 40) return null;
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (view.getUint8(0) !== 84 || view.getUint8(1) !== 71 || view.getUint8(2) !== 67 || view.getUint8(3) !== 49) {
    return null;
  }
  return {
    seq: view.getUint32(4, true),
    buttons: view.getUint32(8, true),
    hat: view.getUint8(12),
    motion: (view.getUint8(13) & 1) === 1,
    lx: view.getInt16(16, true) / 32767,
    ly: view.getInt16(18, true) / 32767,
    rx: view.getInt16(20, true) / 32767,
    ry: view.getInt16(22, true) / 32767,
    lt: Math.max(0, view.getInt16(24, true) / 32767),
    rt: Math.max(0, view.getInt16(26, true) / 32767),
    gx: view.getInt16(28, true) / 1000,
    gy: view.getInt16(30, true) / 1000,
    gz: view.getInt16(32, true) / 1000,
    ax: view.getInt16(34, true) / 100,
    ay: view.getInt16(36, true) / 100,
    az: view.getInt16(38, true) / 100,
  };
}

export function applyMotion(state, sensors, settings) {
  const next = { ...state, gx: sensors.gx, gy: sensors.gy, gz: sensors.gz, ax: sensors.ax, ay: sensors.ay, az: sensors.az };
  const invert = settings.invertY ? 1 : -1;
  if (settings.mode === "off") {
    next.motion = false;
    return next;
  }
  next.motion = true;
  if (settings.mode === "gyro") {
    next.rx = clamp(next.rx + deadzone(sensors.gy * settings.sensitivity, settings.deadzone), -1, 1);
    next.ry = clamp(next.ry + deadzone(sensors.gx * settings.sensitivity, settings.deadzone) * invert, -1, 1);
  } else if (settings.mode === "tilt") {
    next.lx = clamp(deadzone(sensors.roll * settings.sensitivity, settings.deadzone), -1, 1);
    next.ly = clamp(deadzone((sensors.pitch - (settings.pitch0 || 0)) * settings.sensitivity, settings.deadzone) * invert, -1, 1);
  }
  return next;
}

function stick(value) {
  return Math.max(-32767, Math.min(32767, Math.round(clamp(value, -1, 1) * 32767)));
}
function unit(value) {
  return Math.max(0, Math.min(32767, Math.round(clamp(value, 0, 1) * 32767)));
}
function scaled(value, scale) {
  return Math.max(-32767, Math.min(32767, Math.round(value * scale)));
}
function clamp(value, min, max) {
  const n = Number(value) || 0;
  return Math.max(min, Math.min(max, n));
}
function deadzone(value, dz) {
  if (value > dz) return Math.min(1, (value - dz) / (1 - dz));
  if (value < -dz) return Math.max(-1, (value + dz) / (1 - dz));
  return 0;
}
