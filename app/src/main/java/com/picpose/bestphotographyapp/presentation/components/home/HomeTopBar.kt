package com.picpose.bestphotographyapp.presentation.components.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun HomeTopBar(
    titleText: String = "PicPose",
    initialSearch: String = "",
    userImage: String? = null,       // 👈 NEW FIELD
    onSearchClick: (String) -> Unit = {},
    onQueryChanged: (String) -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    var isSearchExpanded by remember { mutableStateOf(initialSearch.isNotBlank()) }
    var query by remember { mutableStateOf(initialSearch) }
    val focusManager = LocalFocusManager.current

    TopAppBar(
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
        ),
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
                            Text(stringResource(R.string.search_prompts_guides_placeholder), textAlign = TextAlign.Start)
                        },
                        leadingIcon = {
                            IconButton(
                                onClick = {
                                    isSearchExpanded = false
                                    query = ""
                                    onQueryChanged("")
                                    focusManager.clearFocus()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back)
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
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear))
                                    }
                                }
                                IconButton(onClick = {
                                    focusManager.clearFocus()
                                    onSearchClick(query)
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.submit_search))
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                                onSearchClick(query)
                            }
                        ),
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
                // 🔍 Search button
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                }

                Spacer(Modifier.width(8.dp))

                // 👤 User Profile Photo (NEW)
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .clickable { onProfileClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (!userImage.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(userImage)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.profile),
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        // Default placeholder circle
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = stringResource(R.string.profile),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(Modifier.width(12.dp))
            }
        },

        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface, //surfaceContainer, used for same as Bottom Nav Bar
            titleContentColor = MaterialTheme.colorScheme.onSurface
        )

    )
}
