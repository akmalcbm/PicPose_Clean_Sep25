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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
import java.io.OutputStream

/**
 * EditProfileScreen
 *
 * - Option A workflow (Camera -> Crop, Gallery -> Crop)
 * - Bottom sheet: Take Photo / Choose from Gallery / Remove Photo
 * - Crop with CanHub (v4.x) using CropImageContract
 * - Compress resulting image before sending to server
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val currentUser by authViewModel.currentUser.collectAsState()

    // UI state
    var name by remember { mutableStateOf(TextFieldValue("")) }
    var bio by remember { mutableStateOf(TextFieldValue("")) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // bottom sheet state
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showSheet by remember { mutableStateOf(false) }

    // animate camera bubble
    val cameraScale = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        cameraScale.animateTo(1.12f, animationSpec = tween(220))
        cameraScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }

    // populate fields when user arrives
    LaunchedEffect(currentUser) {
        currentUser?.let { u ->
            // prefer displayName helper fields on user (if you added them)
            val displayName = u.name ?: u.username ?: ""
            name = TextFieldValue(displayName)
            bio = TextFieldValue(u.bio ?: "")
        }
    }

    // Crop launcher
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            // the cropped image Uri (local file)
            selectedImageUri = result.uriContent
        } else {
            val ex = result.error
            Toast.makeText(context, ex?.message ?: "Crop cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery picker: after pick -> launch cropper for chosen Uri
    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val cropOptions = CropImageOptions().apply {
                fixAspectRatio = true
                aspectRatioX = 1
                aspectRatioY = 1
                guidelines = CropImageView.Guidelines.ON
                outputCompressQuality = 85
                outputCompressFormat = Bitmap.CompressFormat.JPEG
            }
            val contractOptions = CropImageContractOptions(uri, cropOptions)
            cropLauncher.launch(contractOptions)

        }
    }

    // Camera preview launcher (TakePicturePreview) -> get Bitmap -> save to temp file -> crop
    val takePicturePreviewLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp: Bitmap? ->
        if (bmp != null) {
            // write bitmap to cache file and launch cropper
            scope.launch {
                val uri = saveBitmapToCacheAndGetUri(context.cacheDir, bmp)
                if (uri != null) {
                    val cropOptions = CropImageOptions().apply {
                        fixAspectRatio = true
                        aspectRatioX = 1
                        aspectRatioY = 1
                        guidelines = CropImageView.Guidelines.ON
                        outputCompressQuality = 85
                        outputCompressFormat = Bitmap.CompressFormat.JPEG
                    }
                    val contractOptions = CropImageContractOptions(uri, cropOptions)
                    cropLauncher.launch(contractOptions)
                } else {
                    Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(context, "Camera cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Helper to open chooser: we will show bottom sheet instead (no single-chooser API)
    fun openGallery() {
        galleryPicker.launch("image/*")
    }

    fun openCamera() {
        takePicturePreviewLauncher.launch(null)
    }

    // UI
    EdgeToEdgeScaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // PROFILE IMAGE + CAMERA BUTTON (Instagram-like)
            Box(modifier = Modifier.size(130.dp).padding(bottom = 6.dp), contentAlignment = Alignment.Center) {

                // Outer circular clickable image
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable {
                            // open sheet
                            showSheet = true
                            scope.launch { if (!sheetState.isVisible) sheetState.show() }
                        }
                ) {
                    when {
                        selectedImageUri != null -> {
                            // user selected/cropped image
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(selectedImageUri)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Selected Profile Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        !currentUser?.profilePicture.isNullOrBlank() -> {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(currentUser?.profilePicture)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            Surface(modifier = Modifier.fillMaxSize().clip(CircleShape), color = MaterialTheme.colorScheme.primary) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp), tint = MaterialTheme.colorScheme.onPrimary)
                                }
                            }
                        }
                    }
                }

                // Floating camera button positioned outside the circle
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 8.dp, y = 8.dp)
                        .size(46.dp)
                        .graphicsLayer(
                            scaleX = cameraScale.value,
                            scaleY = cameraScale.value,
                            shadowElevation = 8f
                        )
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable {
                            // open chooser bottom sheet directly
                            showSheet = true
                            scope.launch { if (!sheetState.isVisible) sheetState.show() }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Change photo", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
                }
            }

            // NAME FIELD
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Full Name") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                enabled = !isSaving
            )

            // BIO FIELD
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it },
                label = { Text("Bio") },
                placeholder = { Text("Tell us about yourself...") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(12.dp),
                maxLines = 4,
                enabled = !isSaving
            )

            // SAVE BUTTON
            Button(
                onClick = {
                    if (!isSaving) {
                        val nameText = name.text.trim()
                        val bioText = bio.text.trim()
                        if (nameText.isEmpty()) {
                            Toast.makeText(context, "Please enter your name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSaving = true
                        scope.launch {
                            // optionally compress again before upload (cropper already set compress quality)
                            val finalUri = selectedImageUri?.let { uri ->
                                compressUriToJpeg(context = context, uri = uri, quality = 80)
                            } ?: selectedImageUri

                            authViewModel.updateProfile(
                                name = nameText,
                                bio = bioText,
                                profilePictureUri = finalUri,
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
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Save Changes", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Tip: Use a friendly name and short bio to help others recognize you in the app.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }

    // Bottom sheet (take photo / gallery / remove)
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showSheet = false
                scope.launch { sheetState.hide() }
            },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Take Photo
                ListItem(
                    headlineContent = { Text("Take Photo") },
                    leadingContent = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSheet = false
                        scope.launch {
                            sheetState.hide()
                            openCamera()
                        }
                    }
                )
                // Choose from gallery
                ListItem(
                    headlineContent = { Text("Choose from Gallery") },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                    modifier = Modifier.clickable {
                        showSheet = false
                        scope.launch {
                            sheetState.hide()
                            openGallery()
                        }
                    }
                )
                // Remove photo
                ListItem(
                    headlineContent = { Text("Remove Photo", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        showSheet = false
                        scope.launch { sheetState.hide() }
                        selectedImageUri = null
                    }
                )
            }
        }
    }
}

/**
 * Save a Bitmap to cache and return a Uri to that file (Uri.fromFile).
 * Using cacheDir ensures the file is local and readable by cropper.
 */
private suspend fun saveBitmapToCacheAndGetUri(cacheDir: File, bitmap: Bitmap): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val tmpFile = File.createTempFile("camera_", ".jpg", cacheDir)
            val out: OutputStream = FileOutputStream(tmpFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.flush()
            out.close()
            Uri.fromFile(tmpFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

/**
 * Compress a Uri (image) to a temporary JPEG file and return its Uri.
 * This can be called before upload to reduce size further.
 */
suspend fun compressUriToJpeg(context: android.content.Context, uri: Uri, quality: Int = 80): Uri? {
    return withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = android.graphics.BitmapFactory.decodeStream(input)
            input.close()
            val tmpFile = File(context.cacheDir, "profile_${System.currentTimeMillis()}.jpg")
            val out = FileOutputStream(tmpFile)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            out.flush()
            out.close()
            Uri.fromFile(tmpFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
