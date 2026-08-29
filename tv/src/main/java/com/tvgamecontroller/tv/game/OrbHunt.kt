package com.tvgamecontroller.tv.game

import com.tvgamecontroller.protocol.Buttons
import com.tvgamecontroller.protocol.GamepadState
import com.tvgamecontroller.protocol.Hat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

data class Vec(var x: Float, var y: Float)

data class OrbHuntSnapshot(
    val player: Vec,
    val aim: Vec,
    val targets: List<Vec>,
    val shots: List<Vec>,
    val score: Int,
    val combo: Int,
    val connected: Boolean,
    val hint: String,
)

class OrbHunt {
    val player = Vec(0.5f, 0.5f)
    val aim = Vec(1f, 0f)
    val targets = mutableListOf<Vec>()
    val shots = mutableListOf<Pair<Vec, Vec>>()
    var score = 0
        private set
    var combo = 0
        private set
    private var fireCooldown = 0f
    private var spawnTimer = 0f

    init {
        repeat(5) { spawnTarget() }
    }

    fun step(dt: Float, pad: GamepadState, connected: Boolean): OrbHuntSnapshot {
        val hat = Hat.toVector(pad.hat)
        val mx = (pad.leftStickX + hat.first).coerceIn(-1f, 1f)
        val my = (pad.leftStickY + hat.second).coerceIn(-1f, 1f)
        player.x = (player.x + mx * dt * 0.42f).coerceIn(0.06f, 0.94f)
        player.y = (player.y + my * dt * 0.42f).coerceIn(0.08f, 0.92f)

        val aimLen = hypot(pad.rightStickX, pad.rightStickY)
        if (aimLen > 0.12f) {
            aim.x = pad.rightStickX / aimLen
            aim.y = pad.rightStickY / aimLen
        }

        fireCooldown = (fireCooldown - dt).coerceAtLeast(0f)
        val wantFire = pad.isPressed(Buttons.A) || pad.rightTrigger > 0.35f
        if (wantFire && fireCooldown <= 0f) {
            shots += Vec(player.x, player.y) to Vec(aim.x, aim.y)
            fireCooldown = 0.16f
        }

        val nextShots = ArrayList<Pair<Vec, Vec>>(shots.size)
        shots.forEach { (pos, dir) ->
            pos.x += dir.x * dt * 0.95f
            pos.y += dir.y * dt * 0.95f
            if (pos.x in 0f..1f && pos.y in 0f..1f) {
                nextShots += pos to dir
            }
        }
        shots.clear()
        shots.addAll(nextShots)

        val hit = mutableSetOf<Vec>()
        shots.forEach { (pos, _) ->
            targets.forEach { target ->
                if (hypot(pos.x - target.x, pos.y - target.y) < 0.045f) {
                    hit += target
                }
            }
        }
        if (hit.isNotEmpty()) {
            targets.removeAll(hit)
            combo += hit.size
            score += hit.size * (10 + combo * 2)
            repeat(hit.size) { spawnTarget() }
        } else if (!wantFire) {
            combo = (combo - 1).coerceAtLeast(0)
        }

        spawnTimer += dt
        if (targets.size < 5 && spawnTimer > 0.8f) {
            spawnTarget()
            spawnTimer = 0f
        }

        val heading = atan2(aim.y, aim.x)
        return OrbHuntSnapshot(
            player = Vec(player.x, player.y),
            aim = Vec(cos(heading), sin(heading)),
            targets = targets.map { Vec(it.x, it.y) },
            shots = shots.map { Vec(it.first.x, it.first.y) },
            score = score,
            combo = combo,
            connected = connected,
            hint = if (connected) {
                "Left stick move · Right stick / gyro aim · A or R2 shoot"
            } else {
                "Connect a phone on this Wi-Fi to start"
            },
        )
    }

    private fun spawnTarget() {
        targets += Vec(Random.nextFloat() * 0.84f + 0.08f, Random.nextFloat() * 0.78f + 0.12f)
    }
}
