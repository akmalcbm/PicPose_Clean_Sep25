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

    fun buildShareText(
        title: String,
        body: String?,
        ctaTitle: String,
        ctaLine: String,
        maxChars: Int = 900
    ): String {
        val normalizedBody = normalizeShareBody(body.orEmpty())
        val withLineLimit = normalizedBody.lineSequence().take(6).joinToString("\n").trim()
        val withCharLimit = trimWithoutCuttingWords(withLineLimit, maxChars)
        val trimmedBody = removeTrailingFragmentLines(withCharLimit).ifBlank {
            trimWithoutCuttingWords(withLineLimit, maxChars)
        }

        return buildString {
            append(title.trim())
            if (trimmedBody.isNotBlank()) {
                append("\n\n")
                append(trimmedBody)
            }
            if (ctaTitle.isNotBlank() || ctaLine.isNotBlank()) {
                append("\n\n")
                if (ctaTitle.isNotBlank()) append(ctaTitle.trim())
                if (ctaLine.isNotBlank()) {
                    if (ctaTitle.isNotBlank()) append('\n')
                    append(ctaLine.trim())
                }
            }
        }.trim()
    }

    suspend fun sharePrompt(
        context: Context,
        promptText: String,
        imageUrl: String? = null,
        title: String = context.getString(R.string.app_name),
        chooserTitle: String = context.getString(R.string.share_prompt_via)
    ) {
        val ctaLine = context.getString(
            R.string.share_cta_line,
            context.getString(R.string.play_store_url)
        )
        val shareText = buildShareText(
            title = title,
            body = promptText,
            ctaTitle = context.getString(R.string.share_cta_title),
            ctaLine = ctaLine
        )

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
            Intent.createChooser(intent, chooserTitle)
        )
    }

    private fun normalizeShareBody(input: String): String {
        return input
            .replace("\r\n", "\n")
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun trimWithoutCuttingWords(text: String, maxChars: Int): String {
        if (text.length <= maxChars) return text.trim()
        val cutoff = text.take(maxChars + 1)
        val breakIndex = cutoff.lastIndexOfAny(
            charArrayOf(' ', '\n', '\t', '.', '!', '?', ',', ';', '،', '。', '！', '？')
        )
        val safeCut = if (breakIndex >= (maxChars * 0.6f).toInt()) breakIndex else maxChars
        return cutoff.take(safeCut).trimEnd()
    }

    private fun removeTrailingFragmentLines(text: String): String {
        val lines = text.lines().toMutableList()
        var removed = 0
        while (lines.size > 1 && removed < 5) {
            val last = lines.last().trim()
            if (last.isBlank()) {
                lines.removeAt(lines.lastIndex)
                removed++
                continue
            }
            val endsCleanly = last.endsWith(".") || last.endsWith("!") || last.endsWith("?") ||
                last.endsWith("।") || last.endsWith("。") || last.endsWith("！") || last.endsWith("؟")
            val looksFragment = !endsCleanly && last.length < 120
            val veryShort = last.length in 1..12
            if (looksFragment || veryShort) {
                lines.removeAt(lines.lastIndex)
                removed++
            } else {
                break
            }
        }
        return lines.joinToString("\n").trim()
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
