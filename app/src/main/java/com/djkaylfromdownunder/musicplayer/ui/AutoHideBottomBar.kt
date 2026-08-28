package com.djkaylfromdownunder.musicplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val AUTO_HIDE_DELAY_MS = 4_000L

// How far up the handle needs to be dragged before it reveals the bar - small enough to
// feel responsive, large enough that it isn't triggered by finger tremor on a tap.
private val REVEAL_DRAG_THRESHOLD = 20.dp

/**
 * Wraps a bottom bar so it auto-hides after [AUTO_HIDE_DELAY_MS] of being left alone,
 * sliding down out of view. A small drag handle takes its place - dragging it upward
 * brings the bar back and restarts the same countdown. Used for the mini-player/dock on
 * Library and Play Lists (see AppNavHost) and for the compact playback bar on the Player
 * screen, so the content above gets more room once the bar is out of the way.
 */
@Composable
fun AutoHideBottomBar(content: @Composable () -> Unit) {
    var isVisible by remember { mutableStateOf(true) }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(AUTO_HIDE_DELAY_MS)
            isVisible = false
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = isVisible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            content()
        }
        if (!isVisible) {
            RevealHandle(onReveal = { isVisible = true })
        }
    }
}

@Composable
private fun RevealHandle(onReveal: () -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = with(density) { REVEAL_DRAG_THRESHOLD.toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        var accumulatedDrag by remember { mutableStateOf(0f) }
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                            if (accumulatedDrag < -thresholdPx) onReveal()
                        }
                    )
                }
        )
    }
}
