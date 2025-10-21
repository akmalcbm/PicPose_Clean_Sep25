package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    titleText: String = "PicPose",
    initialSearch: String = "",
    onSearchClick: (String) -> Unit = {},
    onQueryChanged: (String) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    var isSearching by remember { mutableStateOf(initialSearch.isNotBlank()) }
    var query by remember { mutableStateOf(initialSearch) }
    val focusManager = LocalFocusManager.current

    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = {
            if (isSearching) {
                TextField(
                    value = query,
                    onValueChange = { value ->
                        query = value
                        onQueryChanged(value)
                    },
                    modifier = Modifier.height(48.dp),
                    singleLine = true,
                    placeholder = { Text("Search prompts, categories...") },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            onSearchClick(query)
                        }
                    ),
                    leadingIcon = {
                        IconButton(onClick = {
                            // exit search mode
                            isSearching = false
                            query = ""
                            onQueryChanged("") // clear search
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    trailingIcon = {
                        // use the plain composable (no RowScope receiver)
                        TrailingIcons(
                            query = query,
                            onClear = {
                                query = ""
                                onQueryChanged("")
                            },
                            onSearch = {
                                focusManager.clearFocus()
                                onSearchClick(query)
                            }
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        // set same container color for focused/unfocused states
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        // indicator colors
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        // cursor color (optional)
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                Text(text = titleText)
            }
        },
        actions = {
            if (!isSearching) {
                IconButton(onClick = { isSearching = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
            IconButton(onClick = onProfileClick) {
                Icon(Icons.Default.AccountCircle, contentDescription = "Profile")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

/** Plain composable (not a RowScope extension) used inside TextField.trailingIcon */
@Composable
private fun TrailingIcons(
    query: String,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    AnimatedVisibility(visible = query.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear")
        }
    }
    IconButton(onClick = onSearch) {
        Icon(Icons.Default.Search, contentDescription = "Submit search")
    }
}
