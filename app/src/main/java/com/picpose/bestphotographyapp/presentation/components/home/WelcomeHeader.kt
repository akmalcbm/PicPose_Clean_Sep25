/**
 * ---
 * File: WelcomeHeader.kt
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

package com.picpose.bestphotographyapp.presentation.components.home

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.picpose.bestphotographyapp.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit

// ✅ DataStore extension for persistence
private val Context.dataStore by preferencesDataStore("user_preferences")

@Composable
fun AnimatedWelcomeHeader() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dataStore = context.dataStore
    val PREF_KEY = booleanPreferencesKey("hide_welcome_card")

    var isHidden by remember { mutableStateOf(false) }
    var isLoaded by remember { mutableStateOf(false) }
    var isVisible by remember { mutableStateOf(false) }

    // ✅ Load persistent preference
    LaunchedEffect(Unit) {
        val prefs = dataStore.data.first()
        isHidden = prefs[PREF_KEY] ?: false
        isLoaded = true
    }

    // ✅ Trigger entrance animation only when needed
    LaunchedEffect(isLoaded) {
        if (isLoaded && !isHidden) isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible && !isHidden,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(700, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(700)),
        exit = fadeOut(animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // 🌈 Polished Welcome Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 🤖 Elegant Illustration Circle
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🤖",
                                fontSize = 26.sp
                            )
                        }

                        // 📝 Welcome Text
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = stringResource(R.string.welcome_to_picpose),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 2
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = stringResource(R.string.welcome_subtitle),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                maxLines = 2,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // ❌ Close Button (floating top-right corner)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-10).dp)
                    .size(28.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        CircleShape
                    )
                    .shadow(2.dp, CircleShape)
                    .clickable {
                        scope.launch {
                            dataStore.edit { prefs -> prefs[PREF_KEY] = true }
                            isVisible = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
