package com.example.royalcarromclassic.engine

import kotlin.random.Random

/** The daily lucky wheel: one spin per day, prizes drawn with the same odds as the online wheel. */
object DailySpin {
    const val COUNTER = "daily_spin"

    /** Wheel segments, clockwise from the pointer. */
    val PRIZES = listOf(100, 250, 500, 1000, 150, 300, 750, 2000)

    /** Relative odds of each segment (the jackpot is rare). */
    val WEIGHTS = listOf(26, 18, 10, 4, 22, 14, 5, 1)

    /** Index of the winning segment. */
    fun draw(random: Random = Random.Default): Int {
        var roll = random.nextInt(WEIGHTS.sum())
        for (i in WEIGHTS.indices) {
            roll -= WEIGHTS[i]
            if (roll < 0) return i
        }
        return 0
    }
}
