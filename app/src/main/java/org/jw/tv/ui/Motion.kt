package org.jw.tv.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

// All motion here animates graphicsLayer properties (alpha, scale, translation)
// read in the draw phase: no recomposition or relayout per frame, so it stays
// smooth on weak TV GPUs/CPUs.

/** 0 → 1 once, when first composed. */
@Composable
fun rememberEnterProgress(durationMillis: Int = 220): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return progress.asState()
}

/** Fades the element in with [progress]. */
fun Modifier.fadeIn(progress: State<Float>): Modifier = graphicsLayer {
    alpha = progress.value
}

/** Fades in while growing from 94 % to full size: dialogs and panels. */
fun Modifier.popIn(progress: State<Float>): Modifier = graphicsLayer {
    val p = progress.value
    alpha = p
    val scale = 0.94f + 0.06f * p
    scaleX = scale
    scaleY = scale
}

/** Fades in while sliding in from the left with [progress]: sidebar labels. */
fun Modifier.revealFromLeft(progress: () -> Float): Modifier = graphicsLayer {
    val p = progress()
    alpha = p
    translationX = (1f - p) * -16.dp.toPx()
}

/** Fades in and rises slightly every time [key] changes: content swaps. */
@Composable
fun Modifier.fadeInOnChange(key: Any?): Modifier {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * 24.dp.toPx()
    }
}
