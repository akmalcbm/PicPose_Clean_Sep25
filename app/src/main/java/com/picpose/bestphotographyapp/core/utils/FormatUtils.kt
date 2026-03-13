/**
 * ---
 * File: FormatUtils.kt
 * Layer: Core
 * Project: PicPose
 *
 * Purpose:
 * Provides app-wide helpers, constants, analytics, locale, formatting, or cross-cutting abstractions.
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

package com.picpose.bestphotographyapp.core.utils

import android.content.Context
import com.picpose.bestphotographyapp.R
import java.text.SimpleDateFormat
import java.util.*

fun formatTimestamp(context: Context, timestamp: String): String {
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
        val date = sdf.parse(timestamp)
        val now = System.currentTimeMillis()
        val diff = now - (date?.time ?: 0)

        when {
            diff < 60000 -> context.getString(R.string.just_now)
            diff < 3600000 -> context.getString(R.string.time_minutes_ago_short, diff / 60000)
            diff < 86400000 -> context.getString(R.string.time_hours_ago_short, diff / 3600000)
            else -> context.getString(R.string.time_days_ago_short, diff / 86400000)
        }
    } catch (e: Exception) {
        context.getString(R.string.recently)
    }
}

fun formatNumber(number: Int): String {
    return when {
        number < 1000 -> number.toString()
        number < 1000000 -> "${(number / 1000.0).let { if (it % 1.0 == 0.0) it.toInt() else String.format("%.1f", it) }}K"
        else -> "${(number / 1000000.0).let { if (it % 1.0 == 0.0) it.toInt() else String.format("%.1f", it) }}M"
    }
}
