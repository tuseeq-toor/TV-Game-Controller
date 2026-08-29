# TV Game Controller

Turn your phone into a digital gamepad — dual analog sticks, face buttons, D-pad, triggers, and motion controls — and play on an Android TV.

```text
Phone (controller)  --Wi-Fi-->  Android TV host app  -->  Orb Hunt playground
                         \--Bluetooth HID-->  any TV game that accepts a controller
```

## What you get

Two Android apps and a browser controller that speak the same protocol:

| Piece | What it does |
| --- | --- |
| **TV Gamepad** (phone) | On-screen DualShock / Xbox-style pad. Gyro look or tilt move. Optional Bluetooth HID so the TV sees a real controller. |
| **TV Gamepad Host** (Android TV) | Shows a PIN + QR code, accepts the phone over Wi-Fi, and runs a built-in **Orb Hunt** playground so you can verify sticks, buttons, and motion immediately. |
| **Browser controller** | Served by the TV app. Scan the QR code if you do not want to install the phone APK yet. |

### Controls

- Left stick and D-pad: move
- Right stick: aim / look
- A / B / X / Y, L1 / R1, Select / Start / Home
- L2 / R2 analog triggers
- **Gyro look**: twist the phone to nudge the right stick; it recenters when you hold still (good for aiming)
- **Tilt move**: lean the phone to hold a left-stick direction (good for driving / balance)
- Recenter sets the current pose as neutral
- Sensitivity, invert Y, deadzone, and haptics live under **Setup**

### Two ways to play on the TV

1. **Wi-Fi (recommended first)**  
   Phone and TV on the same network. Open the host app on the TV, connect from the phone (or scan the QR code). Use Orb Hunt, or any future game that reads this protocol.

2. **Bluetooth HID**  
   In the phone app, turn on **Bluetooth HID**. On the TV go to **Settings → Remotes & accessories → Pair accessory** and select the phone. Android TV then treats the phone as a standard gamepad, so Play Store games and emulators can use it. Gyro look still drives the right stick.

## Install on your phone and Android TV

These apps are not on the Play Store yet. You install two APKs (or only the TV one and use the phone browser).

| File | Device |
| --- | --- |
| `tv/build/outputs/apk/debug/tv-debug.apk` | Android TV — app name **TV Gamepad Host** |
| `mobile/build/outputs/apk/debug/mobile-debug.apk` | Phone — app name **TV Gamepad** |

Build them on a computer:

```bash
./gradlew :tv:assembleDebug :mobile:assembleDebug
```

### 1. Put the TV app on the TV

1. Copy `tv-debug.apk` onto the TV. Easy options:
   - USB stick
   - Send it from your phone with **Send files to TV** / Nearby Share
   - On a computer: `adb connect <tv-ip>` then `adb install tv-debug.apk`
2. On the TV, allow apps from unknown sources if it asks.
3. Open **TV Gamepad Host**. Leave that screen up. Note the **PIN** and Wi-Fi address.

### 2. Use your phone as the controller

**Fastest (no phone APK):** scan the QR code on the TV. Chrome / the system browser opens the pad. Same Wi-Fi is required. Tap **Connect** and enter the PIN if asked.

**Better (native pad + motion + haptics):**

1. Copy `mobile-debug.apk` to the phone and open it.
2. Allow **Install unknown apps** for Files / Chrome / Drive.
3. Open **TV Gamepad**. Allow Bluetooth / notifications if prompted.
4. Tap **Setup**, pick the TV or type its address, enter the PIN, tap **Connect**.

You should now be able to move and shoot in **Orb Hunt** on the TV.

### 3. Play other TV games (Bluetooth)

Wi-Fi mode talks to the host app. For Play Store games and emulators:

1. In the phone app, turn on **Bluetooth HID**.
2. On the TV: **Settings → Remotes & accessories → Pair accessory**.
3. Select the phone. Android TV treats it as a normal gamepad. Gyro look still drives the right stick.

### Android Studio

Open this folder (Ladybug / Koala or newer, JDK 17), sync Gradle, run **tv** on the TV and **mobile** on the phone.

## Browser playground (no Android device required)

```bash
cd web
npm install
npm start
```

Then open [http://127.0.0.1:9842/demo.html](http://127.0.0.1:9842/demo.html) for a side-by-side TV + pad, or use `/tv.html` and `/` in two windows. The on-screen pad drives Orb Hunt over the same WebSocket protocol the Android apps use. On a keyboard: WASD move, IJKL aim, Space shoot.

## Protocol

LAN transport is a WebSocket on port **9842**:

- `GET /` serves the browser controller
- `GET /controller` upgrades to WebSocket
- Handshake: JSON `hello` → `welcome` → `auth` (PIN) → `ready`
- Input: 40-byte binary `TGC1` snapshots at ~60 Hz (JSON `state` is also accepted)
- NSD / Bonjour type: `_tvgamepad._tcp.`

Shared Kotlin lives in `protocol/`. The browser copy is `web/protocol.js`.

```bash
./gradlew :protocol:test
cd web && npm test
```

## Permissions

The phone app asks for Bluetooth (HID) and notification access. Wi-Fi mode only needs the local network. Motion uses the gyroscope and game rotation vector when the hardware has them.

## Limits

- Wi-Fi mode talks to the host app. It does not inject analog sticks into arbitrary Play Store games. Use Bluetooth HID for that.
- Some phone makers restrict `BluetoothHidDevice`. If advertising fails, stay on Wi-Fi + the host app.
- Browser motion APIs often require a permission prompt and work more reliably in the native phone app.
