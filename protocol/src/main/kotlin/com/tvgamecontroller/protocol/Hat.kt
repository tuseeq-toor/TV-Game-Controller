package com.tvgamecontroller.protocol

/**
 * Standard 8-way hat / D-pad encoding used by HID gamepads.
 * 0=N, 1=NE, 2=E, 3=SE, 4=S, 5=SW, 6=W, 7=NW, 8=neutral.
 */
object Hat {
    const val NORTH = 0
    const val NORTHEAST = 1
    const val EAST = 2
    const val SOUTHEAST = 3
    const val SOUTH = 4
    const val SOUTHWEST = 5
    const val WEST = 6
    const val NORTHWEST = 7
    const val NEUTRAL = 8

    fun fromDpad(up: Boolean, down: Boolean, left: Boolean, right: Boolean): Int {
        val ud = when {
            up && !down -> 1
            down && !up -> -1
            else -> 0
        }
        val lr = when {
            right && !left -> 1
            left && !right -> -1
            else -> 0
        }
        return when {
            ud == 1 && lr == 0 -> NORTH
            ud == 1 && lr == 1 -> NORTHEAST
            ud == 0 && lr == 1 -> EAST
            ud == -1 && lr == 1 -> SOUTHEAST
            ud == -1 && lr == 0 -> SOUTH
            ud == -1 && lr == -1 -> SOUTHWEST
            ud == 0 && lr == -1 -> WEST
            ud == 1 && lr == -1 -> NORTHWEST
            else -> NEUTRAL
        }
    }

    fun toVector(hat: Int): Pair<Float, Float> = when (hat) {
        NORTH -> 0f to -1f
        NORTHEAST -> 0.7071f to -0.7071f
        EAST -> 1f to 0f
        SOUTHEAST -> 0.7071f to 0.7071f
        SOUTH -> 0f to 1f
        SOUTHWEST -> -0.7071f to 0.7071f
        WEST -> -1f to 0f
        NORTHWEST -> -0.7071f to -0.7071f
        else -> 0f to 0f
    }
}
