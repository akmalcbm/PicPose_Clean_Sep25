package com.picpose.bestphotographyapp.core.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.picpose.bestphotographyapp.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object ShareUtils {

    suspend fun sharePrompt(
        context: Context,
        promptText: String,
        imageUrl: String? = null
    ) {
        val shareText = buildString {
            append(context.getString(R.string.share_prompt_header))
            append("\n\n")
            append(context.getString(R.string.share_prompt_subheader))
            append("\n\n")
            append(context.getString(R.string.download_on_play_store))
            append("\n")
            append(context.getString(R.string.play_store_url))
            append("\n\n")
            append(promptText.trim())

        }

        val imageUri: Uri? = imageUrl?.let {
            downloadImageToCache(context, it)
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (imageUri != null) "image/*" else "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)

            imageUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.share_prompt_via))
        )
    }

    private suspend fun downloadImageToCache(
        context: Context,
        imageUrl: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val connection = url.openConnection()
            connection.connect()

            val inputStream = connection.getInputStream()
            val file = File(context.cacheDir, "share_prompt_${System.currentTimeMillis()}.jpg")

            FileOutputStream(file).use { output ->
                inputStream.copyTo(output)
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
