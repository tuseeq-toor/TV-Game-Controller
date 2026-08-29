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
await browser.close();

if (!state || state.score < 1) {
  throw new Error(`expected score > 0, got ${JSON.stringify({ scoreText, state })}`);
}
console.log(JSON.stringify({ status, scoreText, state }, null, 2));
