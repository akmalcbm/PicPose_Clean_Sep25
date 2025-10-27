package com.picpose.bestphotographyapp.presentation.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val currentUser by authViewModel.currentUser.collectAsState()

    var name by remember { mutableStateOf(TextFieldValue(currentUser?.name ?: "")) }
    var bio by remember { mutableStateOf(TextFieldValue(currentUser?.bio ?: "")) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedImageUri = uri
    }

    // ✅ Use EdgeToEdgeScaffold (handles paddings perfectly)
    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Edit Profile",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isSaving) {
                                isSaving = true
                                val nameText = name.text.trim()
                                val bioText = bio.text.trim()
                                authViewModel.updateProfile(
                                    name = nameText,
                                    bio = bioText,
                                    profilePictureUri = selectedImageUri,
                                    accountType = currentUser?.accountType ?: AccountType.NORMAL
                                ) { result ->
                                    isSaving = false
                                    result.onSuccess { updatedUser ->
                                        authViewModel.refreshUserSession(updatedUser)
                                        Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                        onSaveSuccess()
                                    }.onFailure { e ->
                                        Toast.makeText(context, e.message ?: "Update failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 🖼 Profile Picture
            item {
                Box(
                    contentAlignment = Alignment.BottomEnd,
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { imagePickerLauncher.launch("image/*") }
                ) {
                    when {
                        selectedImageUri != null -> {
                            Image(
                                painter = rememberAsyncImagePainter(selectedImageUri),
                                contentDescription = "Selected Profile Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        !currentUser?.profilePicture.isNullOrBlank() -> {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(currentUser?.profilePicture)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        else -> {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Default Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Change photo",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // ✏️ Name Input
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }

            // 📜 Bio Input
            item {
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    placeholder = { Text("Tell us about yourself...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4
                )
            }

            // 💾 Save Button
            item {
                Button(
                    onClick = {
                        if (!isSaving) {
                            isSaving = true
                            val nameText = name.text.trim()
                            val bioText = bio.text.trim()
                            authViewModel.updateProfile(
                                name = nameText,
                                bio = bioText,
                                profilePictureUri = selectedImageUri,
                                accountType = currentUser?.accountType ?: AccountType.NORMAL
                            ) { result ->
                                isSaving = false
                                result.onSuccess { updatedUser ->
                                    authViewModel.refreshUserSession(updatedUser)
                                    Toast.makeText(context, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                                    onSaveSuccess()
                                }.onFailure { e ->
                                    Toast.makeText(context, e.message ?: "Update failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text("Save Changes", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
