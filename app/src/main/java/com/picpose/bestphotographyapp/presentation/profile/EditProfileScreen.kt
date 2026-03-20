/**
 * ---
 * File: EditProfileScreen.kt
 * Layer: Presentation (UI)
 * Project: PicPose
 *
 * Purpose:
 * Lists the app navigation routes and helper builders used by Navigation Compose.
 *
 * Interactions:
 * Reads immutable state from ViewModels or callbacks and emits user events back up to navigation or state owners.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Prefer stateless composables and keep side effects inside well-scoped effect APIs.
 * - Document new navigation arguments and remember that recomposition can re-run this code frequently.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.profile

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.remote.dto.AccountType
import com.picpose.bestphotographyapp.components.common.EdgeToEdgeScaffold
import com.picpose.bestphotographyapp.components.common.PicPoseTopAppBar
import com.picpose.bestphotographyapp.presentation.profile.utils.ImageCropper
import com.picpose.bestphotographyapp.presentation.auth.AuthViewModel
import com.yalantis.ucrop.UCrop
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
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var isImageProcessing by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bioSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val showPhotoSheet = remember { mutableStateOf(false) }
    var showBioSheet by remember { mutableStateOf(false) }
    var bioSearch by remember { mutableStateOf("") }
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

    // Crop launcher (uCrop)
    val cropLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isImageProcessing = false
        if (result.resultCode == Activity.RESULT_OK) {
            val output = result.data?.let(UCrop::getOutput)
            if (output != null) {
                selectedImageUri = output
            } else {
                Toast.makeText(context, context.getString(R.string.crop_failed), Toast.LENGTH_SHORT).show()
            }
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            val error = result.data?.let(UCrop::getError)
            Toast.makeText(
                context,
                error?.message ?: context.getString(R.string.crop_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun launchCrop(sourceUri: Uri) {
        val cropIntent = ImageCropper.createCropIntent(context, sourceUri)
        if (cropIntent == null) {
            isImageProcessing = false
            Toast.makeText(context, context.getString(R.string.crop_failed), Toast.LENGTH_SHORT).show()
            return
        }
        isImageProcessing = true
        cropLauncher.launch(cropIntent)
    }

    // Android 13+ Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        PickVisualMedia()
    ) { uri ->
        uri?.let(::launchCrop)
    }

    // Legacy gallery fallback
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(::launchCrop)
    }

    // Camera launcher with content Uri output (no file://)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) {
            launchCrop(uri)
        }
    }

    fun openCamera() {
        val uri = ImageCropper.createTempImageUri(context, "camera_")
        if (uri == null) {
            Toast.makeText(context, context.getString(R.string.crop_failed), Toast.LENGTH_SHORT).show()
            return
        }
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    fun openGallery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            photoPickerLauncher.launch(PickVisualMediaRequest(ImageOnly))
        } else {
            galleryLauncher.launch("image/*")
        }
    }

    val aiBioSuggestions = remember {
        context.resources.getStringArray(R.array.ai_bio_suggestions).toList()
    }
    val filteredBioSuggestions = remember(aiBioSuggestions, bioSearch) {
        val query = bioSearch.trim()
        if (query.isEmpty()) {
            aiBioSuggestions
        } else {
            aiBioSuggestions.filter { it.contains(query, ignoreCase = true) }
        }
    }

    EdgeToEdgeScaffold(
        topBar = {
            PicPoseTopAppBar(
                title = stringResource(R.string.edit_profile),
                onBack = onNavigateBack,
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
                                contentDescription = stringResource(R.string.selected_image),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        !currentUser!!.displayProfilePicture.isNullOrBlank() -> {
                            AsyncImage(
                                model = currentUser!!.displayProfilePicture,
                                contentDescription = stringResource(R.string.profile),
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
                    Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.edit), tint = MaterialTheme.colorScheme.onPrimary)
                }

                if (isImageProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp),
                        strokeWidth = 3.dp
                    )
                }
            }

            // Name
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.full_name)) },
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
                    label = { Text(stringResource(R.string.bio)) },
                    placeholder = { Text(stringResource(R.string.bio_search_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    shape = RoundedCornerShape(12.dp),
                    maxLines = 4,
                    supportingText = {
                        Text(
                            text = stringResource(R.string.bio_style_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { showBioSheet = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = stringResource(R.string.bio_pick_idea),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { showBioSheet = true },
                    label = { Text(stringResource(R.string.bio_pick_idea)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Lightbulb,
                            contentDescription = stringResource(R.string.bio_pick_idea)
                        )
                    }
                )

                AssistChip(
                    onClick = {
                        val suggestion = aiBioSuggestions.randomOrNull() ?: return@AssistChip
                        bio = TextFieldValue(suggestion)
                    },
                    label = { Text(stringResource(R.string.bio_random)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.bio_random)
                        )
                    }
                )
            }

            // Save Button
            Button(
                onClick = {
                    if (name.text.trim().isEmpty()) {
                        Toast.makeText(context, context.getString(R.string.please_enter_your_name), Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    currentUser?.let { user ->  // smart-cast fix

                        isSaving = true

                        scope.launch {
                            val finalUri = selectedImageUri?.let { compressUri(context, it) }
                            val finalBio = bio.text.trim().ifBlank { null }
                            val safeAccountType = runCatching { user.accountType }.getOrDefault(AccountType.NORMAL)

                            authViewModel.updateProfile(
                                name = name.text.trim(),
                                bio = finalBio,
                                profilePictureUri = finalUri,
                                accountType = safeAccountType
                            ) { result ->
                                isSaving = false
                                result.onSuccess {
                                    Toast.makeText(context, context.getString(R.string.profile_updated), Toast.LENGTH_SHORT).show()
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
                    Text(stringResource(R.string.save_changes), fontSize = 17.sp, fontWeight = FontWeight.Bold)
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
                    headlineContent = { Text(stringResource(R.string.take_photo)) },
                    leadingContent = { Icon(Icons.Default.CameraAlt, null) },
                    modifier = Modifier.clickable {
                        showPhotoSheet.value = false
                        scope.launch { sheetState.hide() }
                        openCamera()
                    }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.choose_from_gallery)) },
                    leadingContent = { Icon(Icons.Default.PhotoLibrary, null) },
                    modifier = Modifier.clickable {
                        showPhotoSheet.value = false
                        scope.launch { sheetState.hide() }
                        openGallery()
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.remove_photo), color = MaterialTheme.colorScheme.error)
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            stringResource(R.string.remove),
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

    if (showBioSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showBioSheet = false
                bioSearch = ""
                scope.launch { bioSheetState.hide() }
            },
            sheetState = bioSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.bio_ideas_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.bio_ideas_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = bioSearch,
                    onValueChange = { bioSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.bio_search_hint))
                    },
                    placeholder = { Text(stringResource(R.string.bio_search_hint)) },
                    singleLine = true
                )

                FilledTonalButton(
                    onClick = {
                        val suggestion = filteredBioSuggestions.randomOrNull() ?: aiBioSuggestions.randomOrNull()
                            ?: return@FilledTonalButton
                        bio = TextFieldValue(suggestion)
                        showBioSheet = false
                        bioSearch = ""
                        scope.launch { bioSheetState.hide() }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = stringResource(R.string.bio_random)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.bio_random))
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredBioSuggestions) { suggestion ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    bio = TextFieldValue(suggestion)
                                    showBioSheet = false
                                    bioSearch = ""
                                    scope.launch { bioSheetState.hide() }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = suggestion,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
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
            stringResource(R.string.you_are_not_logged_in),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.please_login_to_edit_profile),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onNavigateToLogin,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.login))
        }

        TextButton(onClick = onBack) {
            Text(stringResource(R.string.cancel))
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
            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
            out.close()
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            null
        }
    }
}
