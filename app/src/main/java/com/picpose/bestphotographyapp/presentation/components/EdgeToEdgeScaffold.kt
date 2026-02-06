package com.picpose.bestphotographyapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * A reusable Edge-to-Edge Scaffold layout with correct system bar handling,
 * bottom navigation spacing, and Samsung-safe insets.
 *
 * Usage:
 * ```
 * EdgeToEdgeScaffold(
 *     topBar = { MyAppBar() },
 *     snackbarHostState = snackbarHostState
 * ) { innerPadding ->
 *     // Your screen content here
 * }
 * ```
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeToEdgeScaffold(
    topBar: @Composable (() -> Unit)? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier,
    backgroundColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.background,
    content: @Composable (innerPadding: androidx.compose.foundation.layout.PaddingValues) -> Unit
) {
    Scaffold(
        topBar = { topBar?.invoke() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0),
        modifier = modifier.background(backgroundColor)
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            content(innerPadding)
        }
    }
}
