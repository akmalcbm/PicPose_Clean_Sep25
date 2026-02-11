package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun premiumHorizontalCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface
)

@Composable
fun premiumHorizontalCardElevation(): CardElevation = CardDefaults.cardElevation(
    defaultElevation = 6.dp
)

@Composable
fun premiumHorizontalCardBorder(): BorderStroke = BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
)

@Composable
fun premiumListCardColors(): CardColors = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surface
)

@Composable
fun premiumListCardElevation(): CardElevation = CardDefaults.cardElevation(
    defaultElevation = 4.dp
)

@Composable
fun premiumListCardBorder(): BorderStroke = BorderStroke(
    width = 1.dp,
    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f)
)
