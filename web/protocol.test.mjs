import test from "node:test";
import assert from "node:assert/strict";
import { applyMotion, Buttons, decodeBinary, emptyState, encodeBinary, Hat, parseState, stateJson } from "./protocol.js";

test("hat directions match the shared protocol", () => {
  assert.equal(Hat.fromDpad(true, false, false, false), Hat.NORTH);
  assert.equal(Hat.fromDpad(false, true, true, false), Hat.SOUTHWEST);
  assert.equal(Hat.fromDpad(false, false, false, false), Hat.NEUTRAL);
});

test("binary snapshot round-trips sticks, buttons, and gyro", () => {
  const original = {
    ...emptyState(),
    seq: 42,
    lx: -0.5,
    ly: 0.25,
    rx: 1,
    ry: -1,
    lt: 0.3,
    rt: 0.9,
    buttons: Buttons.A | Buttons.R1 | Buttons.START,
    hat: Hat.WEST,
    gx: 0.4,
    ay: 9.8,
    motion: true,
  };
  const decoded = decodeBinary(encodeBinary(original));
  assert.equal(decoded.seq, 42);
  assert.equal(decoded.buttons, original.buttons);
  assert.equal(decoded.hat, Hat.WEST);
  assert.equal(decoded.motion, true);
  assert.ok(Math.abs(decoded.lx + 0.5) < 0.002);
  assert.ok(Math.abs(decoded.rt - 0.9) < 0.002);
  assert.ok(Math.abs(decoded.ay - 9.8) < 0.02);
});

test("json state round-trips", () => {
  const parsed = parseState(stateJson({ ...emptyState(), seq: 7, lx: 0.33, buttons: Buttons.X, hat: Hat.SOUTH, motion: true }));
  assert.equal(parsed.seq, 7);
  assert.equal(parsed.buttons, Buttons.X);
  assert.equal(parsed.hat, Hat.SOUTH);
  assert.ok(Math.abs(parsed.lx - 0.33) < 0.001);
});

test("gyro look moves the right stick and returns to center", () => {
  const moving = applyMotion(emptyState(), { gx: 0.4, gy: -0.5, gz: 0, ax: 0, ay: 0, az: 0, pitch: 0, roll: 0 }, {
    mode: "gyro",
    sensitivity: 1,
    deadzone: 0,
    invertY: false,
  });
  assert.equal(moving.motion, true);
  assert.ok(moving.rx < 0);
  assert.ok(moving.ry < 0);
  const still = applyMotion(emptyState(), { gx: 0, gy: 0, gz: 0, ax: 0, ay: 0, az: 0, pitch: 0, roll: 0 }, {
    mode: "gyro",
    sensitivity: 1,
    deadzone: 0,
    invertY: false,
  });
  assert.equal(still.rx, 0);
  assert.equal(still.ry, 0);
});
