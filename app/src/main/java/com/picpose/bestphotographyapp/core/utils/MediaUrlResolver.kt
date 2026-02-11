package com.picpose.bestphotographyapp.core.utils

import com.picpose.bestphotographyapp.BuildConfig

object MediaUrlResolver {

    fun resolve(path: String?): String? {
        val raw = path?.trim().orEmpty()
        if (raw.isBlank()) return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return raw
        }
        val base = BuildConfig.API_BASE_URL.trim().ifBlank { "https://picpose.iamakmal.in/" }
        return base.trimEnd('/') + "/" + raw.trimStart('/')
    }
}

