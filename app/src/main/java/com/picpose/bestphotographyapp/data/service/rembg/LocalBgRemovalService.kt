/**
 * ---
 * File: LocalBgRemovalService.kt
 * Layer: Data
 * Project: PicPose
 *
 * Purpose:
 * Supports the PicPose app with feature-specific models, helpers, or UI building blocks.
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

package com.picpose.bestphotographyapp.data.service.rembg

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.picpose.bestphotographyapp.utils.ImageIO
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class LocalBgRemovalService @Inject constructor(
    private val imageIO: ImageIO
) : BackgroundRemovalService {
    override suspend fun removeBackground(sourceUri: Uri, previewSize: Boolean): Result<Uri> {
        return runCatching {
            val source = imageIO.decodeBitmap(sourceUri)
                ?: throw IOException("Unable to decode source image")
            val output = createCutoutBitmap(source)
            imageIO.saveBitmapToCache(output, "rembg_local_").getOrThrow()
        }
    }

    private fun createCutoutBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val srcPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        val border = estimateBorderColor(srcPixels, width, height)
        val threshold = estimateDistanceThreshold(srcPixels, width, height, border)
        val alphaMask = IntArray(srcPixels.size)

        val cx = width / 2f
        val cy = height / 2f
        val maxDistance = sqrt(cx * cx + cy * cy)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val c = srcPixels[idx]
                val distance = colorDistance(c, border)
                val dx = x - cx
                val dy = y - cy
                val centerBoost = 1f - (sqrt(dx * dx + dy * dy) / maxDistance).coerceIn(0f, 1f)
                val score = distance + (centerBoost * 50f)
                val alpha = when {
                    score >= threshold + 12f -> 255
                    score <= threshold - 12f -> 0
                    else -> (((score - (threshold - 12f)) / 24f) * 255f).toInt().coerceIn(0, 255)
                }
                alphaMask[idx] = alpha
            }
        }

        val smoothedAlpha = smoothMask(alphaMask, width, height)
        val outPixels = IntArray(srcPixels.size)
        for (i in srcPixels.indices) {
            val c = srcPixels[i]
            outPixels[i] = Color.argb(
                smoothedAlpha[i],
                Color.red(c),
                Color.green(c),
                Color.blue(c)
            )
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(outPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun estimateBorderColor(pixels: IntArray, width: Int, height: Int): Int {
        var r = 0L
        var g = 0L
        var b = 0L
        var count = 0L
        val border = 16.coerceAtMost(width / 8).coerceAtLeast(2)

        fun consume(x: Int, y: Int) {
            val c = pixels[y * width + x]
            r += Color.red(c)
            g += Color.green(c)
            b += Color.blue(c)
            count++
        }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val isBorder = x < border || y < border || x >= width - border || y >= height - border
                if (isBorder) consume(x, y)
            }
        }
        if (count == 0L) return Color.WHITE
        return Color.rgb((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }

    private fun estimateDistanceThreshold(
        pixels: IntArray,
        width: Int,
        height: Int,
        borderColor: Int
    ): Float {
        val samples = ArrayList<Float>(1024)
        val stepX = (width / 40).coerceAtLeast(1)
        val stepY = (height / 40).coerceAtLeast(1)
        for (y in 0 until height step stepY) {
            for (x in 0 until width step stepX) {
                samples += colorDistance(pixels[y * width + x], borderColor)
            }
        }
        if (samples.isEmpty()) return 38f
        samples.sort()
        val p60 = samples[(samples.size * 0.60f).toInt().coerceIn(0, samples.lastIndex)]
        return p60.coerceIn(24f, 120f)
    }

    private fun colorDistance(colorA: Int, colorB: Int): Float {
        val dr = (Color.red(colorA) - Color.red(colorB)).toFloat()
        val dg = (Color.green(colorA) - Color.green(colorB)).toFloat()
        val db = (Color.blue(colorA) - Color.blue(colorB)).toFloat()
        return sqrt((dr * dr) + (dg * dg) + (db * db))
    }

    private fun smoothMask(mask: IntArray, width: Int, height: Int): IntArray {
        val output = IntArray(mask.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var total = 0
                var count = 0
                for (dy in -1..1) {
                    val py = y + dy
                    if (py !in 0 until height) continue
                    for (dx in -1..1) {
                        val px = x + dx
                        if (px !in 0 until width) continue
                        total += mask[py * width + px]
                        count++
                    }
                }
                output[y * width + x] = if (count == 0) mask[y * width + x] else (total / count)
            }
        }
        return output
    }
}
