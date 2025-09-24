package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
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
        title = {
            if (isSearching) {
                // Search TextField inside top bar
                TextField(
                    value = query,
                    onValueChange = { value ->
                        query = value
                        onQueryChanged(value) // emit every change (ViewModel has debounce)
                    },
                    modifier = Modifier
                        .height(48.dp),
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
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    trailingIcon = {
                        // Use the simple TrailingIcons composable defined below
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
                    colors = TextFieldDefaults.textFieldColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
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
        colors = TopAppBarDefaults.smallTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
private fun TrailingIcons(
    query: String,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    // AnimatedVisibility is fine directly here since trailingIcon lambda already lays out icons in a RowScope
    AnimatedVisibility(visible = query.isNotBlank(), enter = fadeIn(), exit = fadeOut()) {
        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, contentDescription = "Clear")
        }
    }
    IconButton(onClick = onSearch) {
        Icon(Icons.Default.Search, contentDescription = "Submit search")
    }
}
