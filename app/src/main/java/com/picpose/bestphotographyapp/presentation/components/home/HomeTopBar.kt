package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeTopBar(
    titleText: String = "PicPose",
    initialSearch: String = "",
    onSearchClick: (String) -> Unit = {},
    onQueryChanged: (String) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    var isSearchExpanded by remember { mutableStateOf(initialSearch.isNotBlank()) }
    var query by remember { mutableStateOf(initialSearch) }
    val focusManager = LocalFocusManager.current

    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = {
            AnimatedContent(
                targetState = isSearchExpanded,
                transitionSpec = {
                    slideInHorizontally() + fadeIn() togetherWith slideOutHorizontally() + fadeOut()
                },
                label = "home_search_animation"
            ) { expanded ->
                if (expanded) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            onQueryChanged(it)
                        },
                        placeholder = {
                            Text(
                                text = "Search prompts, guides...",
                                textAlign = TextAlign.Start
                            )
                        },
                        leadingIcon = {
                            IconButton(onClick = {
                                isSearchExpanded = false
                                query = ""
                                onQueryChanged("")
                                focusManager.clearFocus()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        },
                        trailingIcon = {
                            Row {
                                if (query.isNotBlank()) {
                                    IconButton(onClick = {
                                        query = ""
                                        onQueryChanged("")
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear"
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    focusManager.clearFocus()
                                    onSearchClick(query)
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = "Submit search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            onSearchClick(query)
                        }),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                } else {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Start
                    )
                }
            }
        },
        actions = {
            if (!isSearchExpanded) {
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
                IconButton(onClick = onProfileClick) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile"
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}
