package com.picpose.bestphotographyapp.data.models

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

// Guide Post DTO returned by API (robust fields to handle different API shapes)
data class GuidePostDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    // various possible body/summary fields used by different endpoints/admin panels
    @SerializedName("content") val content: String? = null,
    @SerializedName("excerpt") val excerpt: String? = null,
    @SerializedName("short_description") val short_description: String? = null,
    // image fields (some APIs return image_url1, some featured_image, some imageUrl)
    @SerializedName("image_url1") val image_url1: String? = null,
    @SerializedName("featured_image") val featured_image: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("category") val category: String? = null,
    // tags may be returned as JSON string or as an array; keep as Any? and parse in mapper
    @SerializedName("tags") val tags: Any? = null,
    @SerializedName("difficulty_level") val difficulty_level: String? = null,

    // These can arrive as number/string; use Any? and normalize in mapper
    @SerializedName("estimated_read_time") val estimated_read_time: Any? = 0,

    // numeric counters are usually numbers; keep as Int with safe defaults
    @SerializedName("likes") val likes: Int = 0,
    @SerializedName("favorites") val favorites: Int = 0,
    @SerializedName("views") val views: Int = 0,

    // Booleans can arrive as 0/1/"0"/"1"/true/false — accept Any? and coerce in mapper
    @SerializedName("is_featured") val is_featured: Any? = null,
    @SerializedName("is_popular") val is_popular: Any? = null,
    @SerializedName("is_published") val is_published: Any? = null,

    @SerializedName("author") val author: String? = null,
    @SerializedName("author_id") val author_id: String? = null,
    @SerializedName("created_at") val created_at: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updated_at") val updated_at: String? = null,
    @SerializedName("status") val status: String? = "published"
)

// Domain model for GuidePost (UI)
data class GuidePost(
    val id: String,
    val title: String,
    val content: String = "",
    val excerpt: String = "",
    val description: String = "",       // friendly alias used by UI components
    val image: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val difficultyLevel: String = "",
    val estimatedReadTime: Int = 0,
    val likes: Int = 0,
    val favorites: Int = 0,            // used by UI
    val views: Int = 0,
    val isFeatured: Boolean = false,
    val isPopular: Boolean = false,
    val author: String = "",
    val authorId: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val isLiked: Boolean = false
)

// ---- Helpers to normalize flexible JSON fields ----
private fun anyToBoolean(value: Any?): Boolean = when (value) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value.equals("true", ignoreCase = true) || value == "1"
    else -> false
}

private fun anyToInt(value: Any?, default: Int = 0): Int = when (value) {
    is Number -> value.toInt()
    is String -> value.toIntOrNull() ?: default
    is Boolean -> if (value) 1 else 0
    else -> default
}

// Mapper DTO -> domain
fun GuidePostDto.toGuidePost(baseUrl: String? = null): GuidePost {
    // Compose content/excerpt/short_description in a sensible way
    val contentText = content ?: excerpt ?: short_description ?: ""
    val excerptText = excerpt ?: short_description ?: content?.take(150) ?: ""
    val descriptionText = short_description ?: excerpt ?: content?.take(200) ?: ""

    // Resolve image: prefer image_url1 -> featured_image -> imageUrl
    val rawImage = image_url1 ?: featured_image ?: imageUrl ?: ""
    val imageResolved = when {
        rawImage.isBlank() -> ""
        rawImage.startsWith("http://", ignoreCase = true) || rawImage.startsWith("https://", ignoreCase = true) -> rawImage
        !baseUrl.isNullOrBlank() -> baseUrl.trimEnd('/') + "/" + rawImage.trimStart('/')
        else -> rawImage
    }

    // Parse tags robustly: can be List<String>, JSON string '["a","b"]', or comma-separated string
    val parsedTags: List<String> = try {
        when (tags) {
            is List<*> -> tags.filterIsInstance<String>()
            is String -> {
                val s = tags as String
                try {
                    val arr = Gson().fromJson(s, Array<String>::class.java)
                    arr?.toList() ?: s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                } catch (_: Exception) {
                    s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                }
            }
            else -> emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    val createdTime = createdAt ?: created_at ?: ""

    return GuidePost(
        id = id,
        title = title,
        content = contentText,
        excerpt = excerptText,
        description = descriptionText,
        image = imageResolved,
        category = category ?: "",
        tags = parsedTags,
        difficultyLevel = difficulty_level ?: "",
        estimatedReadTime = anyToInt(estimated_read_time, default = 0),
        likes = likes,
        favorites = favorites,
        views = views,
        isFeatured = anyToBoolean(is_featured),
        isPopular = anyToBoolean(is_popular),
        author = author ?: "",
        authorId = author_id ?: "",
        createdAt = createdTime,
        updatedAt = updated_at ?: "",
        isLiked = false
    )
}
