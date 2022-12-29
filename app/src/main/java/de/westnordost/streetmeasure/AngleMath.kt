package de.westnordost.streetmeasure

import kotlin.math.PI

/** returns a number between [startAt] - [startAt]+2PI */
fun normalizeRadians(value: Double, startAt: Double = 0.0): Double {
    val pi2 = PI * 2
    var result = value % pi2 // is now -2PI..2PI
    result = (result + pi2) % pi2 // is now 0..2PI
    if (result > startAt + pi2) result -= pi2
    return result
}