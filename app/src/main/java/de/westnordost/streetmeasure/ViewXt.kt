package de.westnordost.streetmeasure

import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

fun View.flip(duration: Long, block: ((View) -> Unit)? = null) {
    animate()
        .scaleY(0f)
        .setDuration(duration / 2)
        .setInterpolator(AccelerateInterpolator())
        .withEndAction {
            block?.invoke(this)
            animate()
                .scaleY(1f)
                .setDuration(duration / 2)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        .start()
}