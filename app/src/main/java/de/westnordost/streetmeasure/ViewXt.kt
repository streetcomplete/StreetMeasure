package de.westnordost.streetmeasure

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

fun View.flip(duration: Long, block: (View) -> Unit) {
    animate()
        .scaleX(0f)
        .setDuration(duration / 2)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            block(this)
            animate()
                .scaleX(1f)
                .setDuration(duration / 2)
                .setInterpolator(AccelerateInterpolator())
                .start()
        }
        .start()
}