/**
 * ---
 * File: ImageIO.kt
 * Layer: Utility
 * Project: PicPose
 *
 * Purpose:
 * Provides utility helpers that do not belong to a single feature layer.
 *
 * Interactions:
 * Works with nearby classes in the same layer to keep responsibilities separated and easier to maintain.
 *
 * Data Flow:
 * Feature-specific flow; see adjacent ViewModels and repositories for the full path.
 *
 * Maintainer Notes:
 * - Keep responsibilities narrow and update this header when the file grows into a larger abstraction.
 * ---
 */

package com.picpose.bestphotographyapp.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.scale
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

@Singleton
class ImageIO @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAX_UPLOAD_BYTES = 8L * 1024L * 1024L
        private const val MAX_UPLOAD_DIMENSION = 2048
        private const val MAX_PROCESS_DIMENSION = 1080
    }

    suspend fun decodeBitmap(uri: Uri, maxDimension: Int = MAX_PROCESS_DIMENSION): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
            }.getOrNull()
        }

    suspend fun prepareUploadImage(sourceUri: Uri): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeBitmap(sourceUri, MAX_UPLOAD_DIMENSION)
                ?: throw IOException("Unable to decode source image")
            var quality = 90
            val uploadFile = createCacheFile("rembg_upload_", ".jpg")
            do {
                FileOutputStream(uploadFile).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                        throw IOException("Failed to compress image")
                    }
                }
                quality -= 10
            } while (uploadFile.length() > MAX_UPLOAD_BYTES && quality >= 40)

            if (uploadFile.length() > MAX_UPLOAD_BYTES) {
                throw IOException("Image is too large. Please choose a smaller file.")
            }
            uploadFile
        }
    }

    suspend fun persistPngFromResolver(responseResolver: (FileOutputStream) -> Unit): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outFile = createCacheFile("rembg_output_", ".png")
                FileOutputStream(outFile).use { output ->
                    responseResolver(output)
                    output.flush()
                }
                Uri.fromFile(outFile)
            }
        }

    suspend fun saveBitmapToCache(bitmap: Bitmap, prefix: String = "rembg_local_"): Result<Uri> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = createCacheFile(prefix, ".png")
                FileOutputStream(file).use { out ->
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        throw IOException("Failed to save image")
                    }
                }
                Uri.fromFile(file)
            }
        }

    suspend fun compositeBackground(
        originalUri: Uri,
        cutoutUri: Uri,
        backgroundOption: com.picpose.bestphotographyapp.data.rembg.BgBackgroundOption
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val cutout = decodeBitmap(cutoutUri, MAX_PROCESS_DIMENSION)
                ?: throw IOException("Unable to decode processed image")
            val output = Bitmap.createBitmap(cutout.width, cutout.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            when (backgroundOption.mode) {
                com.picpose.bestphotographyapp.data.rembg.BgBackgroundMode.TRANSPARENT -> {
                    canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                }

                com.picpose.bestphotographyapp.data.rembg.BgBackgroundMode.SOLID_COLOR -> {
                    canvas.drawColor(backgroundOption.solidColor)
                }

                com.picpose.bestphotographyapp.data.rembg.BgBackgroundMode.BLUR_ORIGINAL -> {
                    val original = decodeBitmap(originalUri, MAX_PROCESS_DIMENSION)
                    val blurred = original?.let { fastBlur(it, radius = 10) }
                    if (blurred != null) {
                        val scaled = if (blurred.width != cutout.width || blurred.height != cutout.height) {
                            blurred.scale(cutout.width, cutout.height)
                        } else {
                            blurred
                        }
                        canvas.drawBitmap(scaled, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
                    } else {
                        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                    }
                }
            }

            canvas.drawBitmap(cutout, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
            saveBitmapToCache(output, "rembg_preview_").getOrThrow()
        }
    }

    suspend fun savePngToGallery(sourceUri: Uri): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val name = "PicPose_Rembg_${System.currentTimeMillis()}.png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/PicPose"
                )
            }
            val outputUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Unable to create output file")

            resolver.openInputStream(sourceUri).use { input ->
                resolver.openOutputStream(outputUri).use { output ->
                    if (input == null || output == null) {
                        throw IOException("Unable to open file streams")
                    }
                    input.copyTo(output)
                }
            }
            outputUri
        }
    }

    fun readFileName(uri: Uri): String {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
            if (idx != -1 && cursor.moveToFirst()) {
                return cursor.getString(idx)
            }
        }
        return "image_${UUID.randomUUID()}"
    }

    private fun createCacheFile(prefix: String, suffix: String): File {
        val dir = File(context.cacheDir, "rembg").apply { mkdirs() }
        return File.createTempFile(prefix, suffix, dir)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var halfWidth = width / 2
        var halfHeight = height / 2
        while (halfWidth / sample >= maxDimension || halfHeight / sample >= maxDimension) {
            sample *= 2
        }
        return max(1, sample)
    }

    private fun fastBlur(source: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return source
        val downscaled = source.scale(max(1, source.width / 4), max(1, source.height / 4))
        val w = downscaled.width
        val h = downscaled.height
        val pixels = IntArray(w * h)
        downscaled.getPixels(pixels, 0, w, 0, 0, w, h)

        val result = IntArray(pixels.size)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                for (dy in -radius..radius) {
                    val py = y + dy
                    if (py !in 0 until h) continue
                    for (dx in -radius..radius) {
                        val px = x + dx
                        if (px !in 0 until w) continue
                        val c = pixels[py * w + px]
                        a += Color.alpha(c)
                        r += Color.red(c)
                        g += Color.green(c)
                        b += Color.blue(c)
                        count++
                    }
                }
                if (count == 0) {
                    result[y * w + x] = pixels[y * w + x]
                } else {
                    result[y * w + x] = Color.argb(a / count, r / count, g / count, b / count)
                }
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(result, 0, w, 0, 0, w, h)
        return out.scale(source.width, source.height)
    }
}
