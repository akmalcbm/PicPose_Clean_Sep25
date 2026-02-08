package com.picpose.bestphotographyapp.core.utils

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.AnnotatedString
import com.picpose.bestphotographyapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ✅ Universal clipboard helper for Compose 2025.10.01+
 * Works with new suspend Clipboard API (Clipboard / LocalClipboard)
 * and gracefully handles older versions too.
 */
fun copyToClipboard(
    context: Context,
    clipboard: Clipboard,
    text: String,
    coroutineScope: CoroutineScope
) {
    if (text.isBlank()) return

    // Declare a local variable inside coroutine scope to use safely
    coroutineScope.launch {
        val copiedText = text // local variable captured safely

        try {
            // Try calling suspend setText (new Compose API)
            val setTextMethod = clipboard::class.members.firstOrNull { it.name == "setText" }
            if (setTextMethod != null) {
                // Works with new suspend Clipboard API
                setTextMethod.call(clipboard, AnnotatedString(copiedText))
            } else {
                // Fallback: older ClipboardManager versions may use setPrimaryClip()
                val fallbackMethod = clipboard::class.members.firstOrNull { it.name.contains("Clip", ignoreCase = true) }
                fallbackMethod?.call(clipboard, AnnotatedString(copiedText))
            }

            Toast.makeText(context, context.getString(R.string.prompt_copied_toast), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, context.getString(R.string.failed_to_copy_text), Toast.LENGTH_SHORT).show()
        }
    }
}

