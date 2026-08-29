import { chromium } from "playwright";

const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.CHROME_PATH || "/usr/bin/google-chrome-stable",
  args: ["--use-fake-ui-for-media-stream", "--autoplay-policy=no-user-gesture-required"],
});
const page = await browser.newPage({ viewport: { width: 1400, height: 900 } });
await page.goto("http://127.0.0.1:9842/demo.html", { waitUntil: "networkidle" });
await page.waitForTimeout(800);

const tv = page.frameLocator("iframe").nth(0);
const pad = page.frameLocator("iframe").nth(1);

async function assertPlayRow(frame, label) {
  const layout = await frame.evaluate(() => {
    const left = document.querySelector("#leftStick").getBoundingClientRect();
    const right = document.querySelector("#rightStick").getBoundingClientRect();
    const face = document.querySelector(".face").getBoundingClientRect();
    const dpad = document.querySelector(".dpad").getBoundingClientRect();
    return {
      left: { x: left.x, y: left.y, width: left.width, height: left.height },
      right: { x: right.x, y: right.y, width: right.width, height: right.height },
      face: { x: face.x, y: face.y },
      dpad: { x: dpad.x, y: dpad.y },
      overflowX: document.documentElement.scrollWidth - window.innerWidth,
      overflowY: document.documentElement.scrollHeight - window.innerHeight,
    };
  });
  const sticksAligned = Math.abs(layout.left.y - layout.right.y) < 24 && layout.left.x < layout.right.x;
  const landscapeOrder = layout.left.x < layout.dpad.x && layout.dpad.x < layout.face.x && layout.face.x < layout.right.x;
  const portraitStack = layout.dpad.y > layout.left.y + 16 && layout.face.y > layout.right.y + 16;
  if (!sticksAligned || (!landscapeOrder && !portraitStack)) {
    throw new Error(`${label}: play row is broken ${JSON.stringify(layout)}`);
  }
  if (layout.overflowX > 8 || layout.overflowY > 8) {
    throw new Error(`${label}: page overflows ${JSON.stringify(layout)}`);
  }
  return layout;
}

await assertPlayRow(page.frames().find((frame) => frame.url().includes("index.html")), "demo pad");
await pad.locator("#pin").fill("8472");
await pad.locator("#connect").click();
await page.waitForTimeout(500);

const status = await pad.locator("#status").textContent();
if (!status.includes("Playground TV")) {
  throw new Error(`controller did not connect: ${status}`);
}

const controllerFrame = page.frames().find((frame) => frame.url().includes("index.html"));
const tvFrame = page.frames().find((frame) => frame.url().includes("tv.html"));
if (!controllerFrame || !tvFrame) throw new Error("iframes not found");

async function keysDown(codes) {
  await controllerFrame.evaluate((list) => {
    for (const code of list) window.dispatchEvent(new KeyboardEvent("keydown", { code, bubbles: true }));
  }, codes);
}
async function keysUp(codes) {
  await controllerFrame.evaluate((list) => {
    for (const code of list) window.dispatchEvent(new KeyboardEvent("keyup", { code, bubbles: true }));
  }, codes);
}

for (let step = 0; step < 40; step++) {
  const hunt = await tvFrame.evaluate(() => window.__orbHunt);
  if (!hunt?.targets?.length) break;
  const target = hunt.targets[0];
  const dx = target.x - hunt.player.x;
  const dy = target.y - hunt.player.y;
  const move = [];
  const aim = [];
  if (dx > 0.04) move.push("KeyD");
  if (dx < -0.04) move.push("KeyA");
  if (dy > 0.04) move.push("KeyS");
  if (dy < -0.04) move.push("KeyW");
  if (dx > 0.02) aim.push("KeyL");
  if (dx < -0.02) aim.push("KeyJ");
  if (dy > 0.02) aim.push("KeyK");
  if (dy < -0.02) aim.push("KeyI");
  const pressed = [...move, ...aim, "Space"];
  await keysDown(pressed);
  await page.waitForTimeout(140);
  await keysUp(pressed);
  if (hunt.score > 0) break;
}

await page.waitForTimeout(400);
await page.screenshot({ path: "/tmp/demo_after_play.png", fullPage: true });

const scoreText = await tv.locator("#score").textContent();
const state = await tv.locator("body").evaluate(() => window.__orbHunt);

const phone = await browser.newPage({ viewport: { width: 844, height: 390 }, isMobile: true, hasTouch: true });
await phone.goto("http://127.0.0.1:9842/index.html?pin=8472", { waitUntil: "networkidle" });
const phoneLayout = await assertPlayRow(phone.mainFrame(), "iphone landscape");
await phone.screenshot({ path: "/tmp/pad_iphone_landscape.png" });
await phone.locator("#settingsToggle").click();
await phone.screenshot({ path: "/tmp/pad_iphone_setup.png" });
await phone.locator("#settingsToggle").click();
const multi = await phone.evaluate(() => {
  const a = document.querySelector("[data-button='A']");
  const stick = document.querySelector("#leftStick");
  const fire = (el, type, id, x, y) => {
    el.dispatchEvent(new PointerEvent(type, {
      pointerId: id,
      pointerType: "touch",
      clientX: x,
      clientY: y,
      buttons: type === "pointerup" ? 0 : 1,
      bubbles: true,
      cancelable: true,
    }));
  };
  const ar = a.getBoundingClientRect();
  const sr = stick.getBoundingClientRect();
  fire(a, "pointerdown", 11, ar.left + ar.width / 2, ar.top + ar.height / 2);
  fire(stick, "pointerdown", 12, sr.left + sr.width / 2, sr.top + sr.height / 2);
  fire(stick, "pointermove", 12, sr.left + sr.width * 0.9, sr.top + sr.height / 2);
  return {
    scale: window.visualViewport?.scale ?? 1,
    aOn: a.classList.contains("on"),
  };
});
if (multi.scale !== 1) throw new Error(`page zoomed during multi-touch: ${multi.scale}`);
if (!multi.aOn) throw new Error("A did not stay held while the stick moved");
await phone.waitForTimeout(40);
const tel = await phone.locator("#telemetry").textContent();
if (!tel.includes("L(0.80") && !tel.includes("L(0.7") && !tel.includes("L(0.9")) {
  throw new Error(`stick did not move during multi-touch: ${tel}`);
}
await phone.setViewportSize({ width: 390, height: 844 });
await assertPlayRow(phone.mainFrame(), "iphone portrait");
await phone.screenshot({ path: "/tmp/pad_iphone_portrait.png" });
await phone.close();

await browser.close();

if (!state || state.score < 1) {
  throw new Error(`expected score > 0, got ${JSON.stringify({ scoreText, state })}`);
}
console.log(JSON.stringify({ status, scoreText, state, phoneLayout }, null, 2));
