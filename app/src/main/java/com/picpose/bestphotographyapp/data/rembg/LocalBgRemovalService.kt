package com.picpose.bestphotographyapp.data.rembg

import android.net.Uri
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalBgRemovalService @Inject constructor() : BackgroundRemovalService {
    override suspend fun removeBackground(sourceUri: Uri, previewSize: Boolean): Result<Uri> {
        return Result.failure(IOException("Offline processing is not available yet."))
    }
}
