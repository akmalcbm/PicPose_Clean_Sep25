package com.picpose.bestphotographyapp.presentation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.picpose.bestphotographyapp.R
import com.yalantis.ucrop.UCrop
import java.io.File

object ImageCropper {

    fun createTempImageUri(context: Context, prefix: String): Uri? {
        return runCatching {
            val dir = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File.createTempFile(prefix, ".jpg", dir)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        }.getOrNull()
    }

    fun createCropIntent(context: Context, sourceUri: Uri): Intent? {
        val destinationUri = createTempImageUri(context, "crop_") ?: return null

        val options = UCrop.Options().apply {
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            setCompressionQuality(85)
            setFreeStyleCropEnabled(false)
            setHideBottomControls(false)
            setToolbarTitle(context.getString(R.string.edit_profile))
        }

        return UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withOptions(options)
            .getIntent(context)
    }
}
