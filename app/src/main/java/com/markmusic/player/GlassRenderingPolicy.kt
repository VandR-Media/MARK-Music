package com.markmusic.player

/**
 * Avoid GPU layer churn for large static glass panels on scrollable screens.
 * Interactive buttons still retain the existing press/tilt animation.
 * Pure policy lets unit tests guard performance-sensitive rendering decisions.
 */
internal object GlassRenderingPolicy {
    fun usesParallax(hasClickAction: Boolean): Boolean = hasClickAction

    fun elevationDp(hasClickAction: Boolean, pressed: Boolean, depth: Float): Float =
        if (!hasClickAction) 3f + depth * 2f
        else if (pressed) 3f else 4f + depth * 11f
}
