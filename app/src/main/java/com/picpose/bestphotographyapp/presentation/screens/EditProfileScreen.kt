package com.picpose.bestphotographyapp.presentation.screens

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.picpose.bestphotographyapp.data.models.AccountType
import com.picpose.bestphotographyapp.presentation.components.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.presentation.viewmodels.AuthViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onSaveSuccess: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()
    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()

    var name by remember { mutableStateOf(TextFieldValue("")) }
    var bio by remember { mutableStateOf(TextFieldValue("")) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showPhotoSheet = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Prefill name + bio
    LaunchedEffect(currentUser) {
        currentUser?.let {
            name = TextFieldValue(it.displayName)
            bio = TextFieldValue(it.bio ?: "")
        }
    }

    if (!isLoggedIn || currentUser == null) {
        NotLoggedInScreen(
            onBack = onNavigateBack,
            onNavigateToLogin = onNavigateToLogin
        )
        return
    }

    // Crop launcher
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            selectedImageUri = result.uriContent
        } else {
            Toast.makeText(context, result.error?.message ?: "Crop failed", Toast.LENGTH_SHORT).show()
        }
    }

    // Build crop options safely
    fun cropOptions(): CropImageOptions {
        return CropImageOptions().apply {
            fixAspectRatio = true
            aspectRatioX = 1
            aspectRatioY = 1
            guidelines = CropImageView.Guidelines.ON
            outputCompressFormat = Bitmap.CompressFormat.JPEG
            outputCompressQuality = 85
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            cropLauncher.launch(
                CropImageContractOptions(
                    uri,
                    cropOptions()
                )
            )
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            scope.launch {
                val uri = saveBitmapToCache(context.cacheDir, bmp)
                if (uri != null) {
                    cropLauncher.launch(
                        CropImageContractOptions(
                            uri,
                            cropOptions()
                        )
                    )
                }
            }
        }
    }

    fun openCamera() = cameraLauncher.launch(null)
    fun openGallery() = galleryLauncher.launch("image/*")

    val aiBioSuggestions = listOf(
        "Capturing moments, creating memories ✨",
        "Chasing light & freezing time 📸",
        "Turning everyday life into art 🎨",
        "Creating magic through my lens ✨",
        "Living life one snapshot at a time 🌿",
        "Finding beauty in small details 🌱",
        "Smile. Click. Repeat. 📷",
        "Collecting moments, not things 💫",
        "Where creativity meets clarity ✨",
        "Storytelling through frames 🎞️",
        "Exploring the world through my lens 🌍"
    )

    var showBioMenu by remember { mutableStateOf(false) }

    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {

            // Profile photo
            Box(
                modifier = Modifier.size(140.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            showPhotoSheet.value = true
                            scope.launch { sheetState.show() }
                        }
                ) {
                    when {
                        selectedImageUri != null -> {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = "Selected",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        !currentUser!!.displayProfilePicture.isNullOrBlank() -> {
                            AsyncImage(
                                model = currentUser!!.displayProfilePicture,
                                contentDescription = "Profile",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        else -> {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(60.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Camera button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(10.dp, 10.dp)
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            showPhotoSheet.value = true
                            scope.launch { sheetState.show() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                leadingIcon = { Icon(Icons.Default.Person, null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Bio
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = bio,
                    onValueChange = { bio = it },
                    label = { Text("Bio") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = { showBioMenu = true }) {
                            Icon(Icons.Default.Lightbulb, contentDescription = "AI Bio", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                DropdownMenu(
                    expanded = showBioMenu,
                    onDismissRequest = { showBioMenu = false }
                ) {
                    Text(
                        "AI Bio Suggestions",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    aiBioSuggestions.forEach { suggestion ->
                        DropdownMenuItem(
                            text = { Text(suggestion) },
                            onClick = {
                                bio = TextFieldValue(suggestion)
                                showBioMenu = false
                            }
                        )
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    if (name.text.trim().isEmpty()) {
                        Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    currentUser?.let { user ->  // smart-cast fix

                        isSaving = true

                        scope.launch {
                            val finalUri = selectedImageUri?.let { compressUri(context, it) }
                            val finalBio = bio.text.trim().ifBlank { null }

                            authViewModel.updateProfile(
                                name = name.text.trim(),
                                bio = finalBio,
                                profilePictureUri = finalUri,
                                accountType = user.accountType
                            ) { result ->
                                isSaving = false
                                result.onSuccess {
                                    Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
                                    onSaveSuccess()
                                }.onFailure {
                                    Toast.makeText(context, it.message, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                } else {
                    Text("Save Changes", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // PHOTO BOTTOM SHEET
    if (showPhotoSheet.value) {
        ModalBottomSheet(
            onDismissRequest = {
                showPhotoSheet.value = false
                scope.launch { sheetState.hide() }
            },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ListItem(
                    headlineContent = { Text("Take Photo") },
                    leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                    modifier = Modifier.clickable {
                        showPhotoSheet.value = false
                        scope.launch { sheetState.hide() }
                        openCamera()
                    }
                )
                ListItem(
                    headlineContent = { Text("Choose from Gallery") },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                    modifier = Modifier.clickable {
                        showPhotoSheet.value = false
                        scope.launch { sheetState.hide() }
                        openGallery()
                    }
                )
                ListItem(
                    headlineContent = {
                        Text("Remove Photo", color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            "Remove",
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedImageUri = null
                        showPhotoSheet.value = false
                        scope.launch { sheetState.hide() }
                    }
                )
            }
        }
    }
}

@Composable
fun NotLoggedInScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Icon(
            Icons.Default.PersonOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "You're not logged in",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Please login to edit your profile",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNavigateToLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }

        TextButton(onClick = onBack) {
            Text("Cancel")
        }
    }
}


/** Save bitmap to cache */
private suspend fun saveBitmapToCache(dir: File, bmp: Bitmap): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val tmp = File(dir, "camera_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(tmp)
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.close()
            Uri.fromFile(tmp)
        } catch (e: Exception) {
            null
        }
    }
}

/** Compress an image Uri before upload */
suspend fun compressUri(context: android.content.Context, uri: Uri, quality: Int = 80): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bmp = android.graphics.BitmapFactory.decodeStream(input)
            input.close()

            val file = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(file)
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.close()

            Uri.fromFile(file)
        } catch (e: Exception) {
            null
        }
    }
}
