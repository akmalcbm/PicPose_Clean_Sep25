/**
 * ---
 * File: AnimatedDailyTipCard.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Contains reusable Compose UI building blocks shared across screens.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.home.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.R

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedDailyTipCard(
    tip: String,
    onNextTip: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 🔹 Header Row with icon + Next button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = stringResource(R.string.tip),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.daily_ai_tip),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                IconButton(onClick = {
                    onNextTip()
                    isExpanded = false // collapse on next tip
                }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.next_tip),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            //Spacer(modifier = Modifier.height(8.dp))

            // 🔹 Animated tip text with fade + slide
            AnimatedContent(
                targetState = tip,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInHorizontally(initialOffsetX = { it / 2 })) togetherWith
                            (fadeOut(tween(300)) + slideOutHorizontally(targetOffsetX = { -it / 2 }))
                },
                label = "TipTransition"
            ) { displayedTip ->
                val annotatedText = buildAnnotatedString {
                    val parts = displayedTip.split(":", limit = 2)

                    // Bold heading (before :)
                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(parts[0])
                        append(":")
                    }

                    // Normal text (after :)
                    if (parts.size > 1) {
                        append("\n")
                        append(parts[1].trim())
                    }
                }

                Text(
                    text = annotatedText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    textAlign = TextAlign.Start
                )

            }

            // 🔹 Read more / Show less control (only for long tips)
            if (tip.length > 100) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isExpanded)
                        stringResource(R.string.show_less)
                    else
                        stringResource(R.string.read_more),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .clickable { isExpanded = !isExpanded }
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 🔹 Hint to user for interactivity
            Text(
                text = stringResource(R.string.tap_to_see_another_tip),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
