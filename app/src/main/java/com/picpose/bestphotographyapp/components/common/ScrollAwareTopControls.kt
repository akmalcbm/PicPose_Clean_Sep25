/**
 * ---
 * File: ScrollAwareTopControls.kt
 * Layer: Shared UI
 * Project: PicPose
 *
 * Purpose:
 * Reusable scroll-aware visibility behavior for top controls such as chips/search bars.
 * ---
 */

package com.picpose.bestphotographyapp.components.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import kotlin.math.abs

@Composable
fun rememberScrollAwareTopControlsVisibility(
    enabled: Boolean,
    scrollPositionProvider: () -> Pair<Int, Int>,
    resetKey: Any? = Unit,
    nearTopOffsetPx: Int = 24,
    microScrollThresholdPx: Int = 6,
    hideThresholdPx: Int = 64,
    showThresholdPx: Int = 44,
): Boolean {
    var isVisible by remember { mutableStateOf(true) }
    var lastIndex by remember(resetKey) { mutableIntStateOf(Int.MIN_VALUE) }
    var lastOffset by remember(resetKey) { mutableIntStateOf(Int.MIN_VALUE) }
    var accumulatedDirectionDelta by remember(resetKey) { mutableIntStateOf(0) }
    var lastDirection by remember(resetKey) { mutableIntStateOf(0) }

    LaunchedEffect(enabled, resetKey) {
        if (!enabled) {
            isVisible = true
            lastIndex = Int.MIN_VALUE
            lastOffset = Int.MIN_VALUE
            accumulatedDirectionDelta = 0
            lastDirection = 0
        }
    }

    LaunchedEffect(
        enabled,
        resetKey,
        nearTopOffsetPx,
        microScrollThresholdPx,
        hideThresholdPx,
        showThresholdPx,
    ) {
        if (!enabled) return@LaunchedEffect

        snapshotFlow(scrollPositionProvider).collect { (currentIndex, currentOffset) ->
            if (lastIndex == Int.MIN_VALUE || lastOffset == Int.MIN_VALUE) {
                lastIndex = currentIndex
                lastOffset = currentOffset
                isVisible = currentIndex == 0 && currentOffset <= nearTopOffsetPx
                accumulatedDirectionDelta = 0
                lastDirection = 0
                return@collect
            }

            val atTop = currentIndex == 0 && currentOffset <= nearTopOffsetPx
            if (atTop) {
                isVisible = true
                accumulatedDirectionDelta = 0
                lastDirection = 0
                lastIndex = currentIndex
                lastOffset = currentOffset
                return@collect
            }

            val indexDelta = currentIndex - lastIndex
            val offsetDelta = currentOffset - lastOffset

            val directionalDelta = when {
                indexDelta > 0 -> microScrollThresholdPx * 8
                indexDelta < 0 -> -microScrollThresholdPx * 8
                else -> offsetDelta
            }
            if (abs(directionalDelta) < microScrollThresholdPx) {
                lastIndex = currentIndex
                lastOffset = currentOffset
                return@collect
            }

            val direction = if (directionalDelta > 0) 1 else -1
            if (direction != lastDirection) {
                accumulatedDirectionDelta = 0
                lastDirection = direction
            }
            accumulatedDirectionDelta += abs(directionalDelta)

            if (direction > 0 && isVisible && accumulatedDirectionDelta >= hideThresholdPx) {
                isVisible = false
                accumulatedDirectionDelta = 0
            } else if (direction < 0 && !isVisible && accumulatedDirectionDelta >= showThresholdPx) {
                isVisible = true
                accumulatedDirectionDelta = 0
            }

            lastIndex = currentIndex
            lastOffset = currentOffset
        }
    }

    return isVisible
}

@Composable
fun ScrollAwareTopControls(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(durationMillis = 160)) +
            expandVertically(
                animationSpec = tween(durationMillis = 220),
                expandFrom = androidx.compose.ui.Alignment.Top,
            ),
        exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
            shrinkVertically(
                animationSpec = tween(durationMillis = 200),
                shrinkTowards = androidx.compose.ui.Alignment.Top,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
        ) {
            content()
        }
    }
}
