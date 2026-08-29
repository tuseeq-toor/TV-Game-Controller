package com.tvgamecontroller.protocol

object Buttons {
    const val A = 1 shl 0
    const val B = 1 shl 1
    const val X = 1 shl 2
    const val Y = 1 shl 3
    const val L1 = 1 shl 4
    const val R1 = 1 shl 5
    const val L3 = 1 shl 6
    const val R3 = 1 shl 7
    const val SELECT = 1 shl 8
    const val START = 1 shl 9
    const val HOME = 1 shl 10
    const val L2 = 1 shl 11
    const val R2 = 1 shl 12

    val ALL = listOf(
        "A" to A,
        "B" to B,
        "X" to X,
        "Y" to Y,
        "L1" to L1,
        "R1" to R1,
        "L3" to L3,
        "R3" to R3,
        "SELECT" to SELECT,
        "START" to START,
        "HOME" to HOME,
        "L2" to L2,
        "R2" to R2,
    )

    fun isPressed(mask: Int, button: Int): Boolean = mask and button != 0

    fun names(mask: Int): List<String> = ALL.filter { mask and it.second != 0 }.map { it.first }
}
