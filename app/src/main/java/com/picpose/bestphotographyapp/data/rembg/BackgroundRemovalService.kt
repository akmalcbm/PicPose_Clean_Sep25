package com.picpose.bestphotographyapp.data.rembg

import android.net.Uri

interface BackgroundRemovalService {
    suspend fun removeBackground(sourceUri: Uri, previewSize: Boolean): Result<Uri>
}
