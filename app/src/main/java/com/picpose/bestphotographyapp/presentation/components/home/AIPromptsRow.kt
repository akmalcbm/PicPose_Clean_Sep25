package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.picpose.bestphotographyapp.data.models.AIPrompt

@Composable
fun AIPromptsRow(
    prompts: List<AIPrompt>,
    onPromptClick: (AIPrompt) -> Unit,
    onCopyPrompt: (AIPrompt) -> Unit,
    onFavoriteClick: (AIPrompt) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(prompts) { prompt ->
            AIPromptCardOnlyHome(
                prompt = prompt,
                onClick = { onPromptClick(prompt) },
                onCopy = { onCopyPrompt(prompt) },
                onFavoriteClick = { onFavoriteClick(prompt) },
                showFavoriteIcon = true,
                isCompact = true
            )
        }
    }
}