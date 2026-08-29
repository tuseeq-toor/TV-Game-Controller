import { parseState, emptyState } from "./protocol.js";

export function createGame(canvas) {
  const ctx = canvas.getContext("2d");
  const player = { x: 0.5, y: 0.5 };
  const aim = { x: 1, y: 0 };
  const targets = [];
  const shots = [];
  let score = 0;
  let combo = 0;
  let fire = 0;
  let pad = emptyState();
  let connected = false;

  function spawn() {
    targets.push({ x: 0.08 + Math.random() * 0.84, y: 0.12 + Math.random() * 0.76 });
  }
  while (targets.length < 5) spawn();

  function resize() {
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * devicePixelRatio;
    canvas.height = rect.height * devicePixelRatio;
  }
  resize();
  window.addEventListener("resize", resize);

  function ingest(state) {
    pad = state;
    connected = true;
  }

  function step(dt) {
    player.x = clamp(player.x + pad.lx * dt * 0.42, 0.06, 0.94);
    player.y = clamp(player.y + pad.ly * dt * 0.42, 0.08, 0.92);
    const len = Math.hypot(pad.rx, pad.ry);
    if (len > 0.12) {
      aim.x = pad.rx / len;
      aim.y = pad.ry / len;
    }
    fire = Math.max(0, fire - dt);
    const wantFire = (pad.buttons & 1) !== 0 || pad.rt > 0.35;
    if (wantFire && fire <= 0) {
      shots.push({ x: player.x, y: player.y, dx: aim.x, dy: aim.y });
      fire = 0.16;
    }
    for (const shot of shots) {
      shot.x += shot.dx * dt * 0.95;
      shot.y += shot.dy * dt * 0.95;
    }
    for (let i = shots.length - 1; i >= 0; i--) {
      const shot = shots[i];
      if (shot.x < 0 || shot.x > 1 || shot.y < 0 || shot.y > 1) shots.splice(i, 1);
    }
    let hits = 0;
    for (let t = targets.length - 1; t >= 0; t--) {
      const target = targets[t];
      if (shots.some((shot) => Math.hypot(shot.x - target.x, shot.y - target.y) < 0.045)) {
        targets.splice(t, 1);
        hits += 1;
        spawn();
      }
    }
    if (hits) {
      combo += hits;
      score += hits * (10 + combo * 2);
    }
  }

  function draw() {
    const w = canvas.width;
    const h = canvas.height;
    ctx.fillStyle = "#0a1226";
    ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = "#ffc857";
    for (const target of targets) {
      ctx.beginPath();
      ctx.arc(target.x * w, target.y * h, 16 * devicePixelRatio, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.fillStyle = "#ffffff";
    for (const shot of shots) {
      ctx.beginPath();
      ctx.arc(shot.x * w, shot.y * h, 6 * devicePixelRatio, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.fillStyle = "#1ce8c8";
    ctx.beginPath();
    ctx.arc(player.x * w, player.y * h, 22 * devicePixelRatio, 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = "#ffffff";
    ctx.lineWidth = 5 * devicePixelRatio;
    ctx.beginPath();
    ctx.moveTo(player.x * w, player.y * h);
    ctx.lineTo(player.x * w + aim.x * 54 * devicePixelRatio, player.y * h + aim.y * 54 * devicePixelRatio);
    ctx.stroke();
    ctx.fillStyle = "#f4f7ff";
    ctx.font = `${18 * devicePixelRatio}px sans-serif`;
    ctx.fillText(`ORB HUNT   Score ${score}${combo > 1 ? `   Combo x${combo}` : ""}`, 24, 36 * devicePixelRatio);
    const hud = document.querySelector("#score");
    if (hud) hud.textContent = `Score ${score}`;
    window.__orbHunt = {
      score,
      combo,
      player: { ...player },
      aim: { ...aim },
      targets: targets.map((t) => ({ ...t })),
      connected,
    };
    ctx.fillStyle = "#9aa6c4";
    ctx.font = `${13 * devicePixelRatio}px sans-serif`;
    ctx.fillText(
      connected ? "Left stick move · Right stick / gyro aim · A or R2 shoot" : "Connect a phone controller",
      24,
      h - 24 * devicePixelRatio,
    );
  }

  let last = performance.now();
  function loop(now) {
    step(Math.min(0.05, (now - last) / 1000));
    last = now;
    draw();
    requestAnimationFrame(loop);
  }
  requestAnimationFrame(loop);

  return { ingest, parseState };
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}
