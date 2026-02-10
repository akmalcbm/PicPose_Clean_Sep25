package com.picpose.bestphotographyapp.data.models

import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

// Guide Post DTO returned by API (robust fields to handle different API shapes)
@Keep
data class GuidePostDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    // various possible body/summary fields used by different endpoints/admin panels
    @SerializedName("content") val content: String? = null,
    @SerializedName("excerpt") val excerpt: String? = null,
    @SerializedName("short_description") val short_description: String? = null,
    @SerializedName("shortDescription") val shortDescription: String? = null,
    @SerializedName("description") val description: String? = null,
    // image fields (some APIs return image_url1, some featured_image, some imageUrl)
    @SerializedName("image_url1") val image_url1: String? = null,
    @SerializedName("image_url") val image_url: String? = null,
    @SerializedName("featured_image") val featured_image: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("images") val images: List<String>? = null,
    @SerializedName("videos") val videos: Any? = null,
    @SerializedName("videoUrls") val videoUrls: List<String>? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("category_name") val category_name: String? = null,
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
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("status") val status: String? = "published",
    @SerializedName("readTimeMinutes") val readTimeMinutes: Int? = null,
    @SerializedName("shareUrl") val shareUrl: String? = null,
    @SerializedName("author_name") val authorDisplay: String? = null,
    @SerializedName("priority") val priority: Int = 0,
    @SerializedName("content_blocks") val content_blocks: List<ContentBlockDto>? = null,
    @SerializedName("contentBlocks") val contentBlocks: List<ContentBlockDto>? = null
)

@Keep
data class ContentBlockDto(
    @SerializedName("type") val type: String = "",
    @SerializedName("text") val text: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("image") val image: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("alt") val alt: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("items") val items: List<String>? = null
)

@Keep
data class GuideVideoDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("provider") val provider: String? = null,
    @SerializedName("caption") val caption: String? = null
)

data class GuideVideo(
    val url: String,
    val provider: String = "mp4",
    val caption: String? = null
)

sealed interface GuideContentBlock {
    data class Heading(val level: Int, val text: String) : GuideContentBlock
    data class Paragraph(val text: String) : GuideContentBlock
    data class Image(val url: String, val caption: String?, val alt: String?) : GuideContentBlock
    data class Video(val url: String, val provider: String, val caption: String?) : GuideContentBlock
    data class Callout(val title: String, val text: String) : GuideContentBlock
    data class OrderedList(val items: List<String>) : GuideContentBlock
    data class UnorderedList(val items: List<String>) : GuideContentBlock
    data object Divider : GuideContentBlock
}

// Domain model for GuidePost (UI)
data class GuidePost(
    val id: String,
    val title: String,
    val content: String = "",
    val excerpt: String = "",
    val description: String = "",       // friendly alias used by UI components
    val image: String = "",
    val imageUrl: String = "",          // alias for image
    val thumbnailUrl: String = "",      // thumbnail version
    val category: String = "",
    val tags: List<String> = emptyList(),
    val difficultyLevel: String = "",
    val estimatedReadTime: Int = 0,
    val readingTime: Int = 0,           // alias for estimatedReadTime
    val likes: Int = 0,
    val favorites: Int = 0,            // used by UI
    val views: Int = 0,
    val viewCount: Int = 0,            // alias for views
    val isFeatured: Boolean = false,
    val isPopular: Boolean = false,
    val isFavorited: Boolean = false,  // favorite status for UI
    val author: String = "",
    val authorName: String = "",       // alias for author
    val authorId: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val isLiked: Boolean = false,
    val contentBlocks: List<GuideContentBlock> = emptyList(),
    val images: List<String> = emptyList(),
    val videos: List<String> = emptyList(),
    val videoItems: List<GuideVideo> = emptyList(),
    val shareUrl: String? = null,
    val priority: Int = 0,
    val status: String = "published"
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

private fun parseStringListOrEmpty(value: Any?): List<String> = when (value) {
    is List<*> -> value.filterIsInstance<String>().map { it.trim() }.filter { it.isNotEmpty() }
    is String -> value.split(',', ';', '|').map { it.trim() }.filter { it.isNotEmpty() }
    else -> emptyList()
}

private fun parseVideos(value: Any?): List<GuideVideo> {
    return when (value) {
        is List<*> -> value.mapNotNull { item ->
            when (item) {
                is String -> {
                    val url = item.trim()
                    if (url.isBlank()) null else GuideVideo(url = url, provider = "mp4", caption = null)
                }
                is Map<*, *> -> {
                    val url = (item["url"] as? String)?.trim().orEmpty()
                    if (url.isBlank()) null
                    else GuideVideo(
                        url = url,
                        provider = ((item["provider"] as? String)?.trim().orEmpty()).ifBlank { "mp4" },
                        caption = (item["caption"] as? String)?.trim()?.ifBlank { null }
                    )
                }
                else -> null
            }
        }
        else -> emptyList()
    }
}

fun ContentBlockDto.toDomainOrNull(): GuideContentBlock? = when (type.lowercase()) {
    "h1" -> text?.takeIf { it.isNotBlank() }?.let { GuideContentBlock.Heading(1, it) }
    "h2" -> text?.takeIf { it.isNotBlank() }?.let { GuideContentBlock.Heading(2, it) }
    "h3" -> text?.takeIf { it.isNotBlank() }?.let { GuideContentBlock.Heading(3, it) }
    "p" -> text?.takeIf { it.isNotBlank() }?.let { GuideContentBlock.Paragraph(it) }
    "hero", "image" -> (url ?: image)?.takeIf { it.isNotBlank() }?.let { GuideContentBlock.Image(it, caption, alt) }
    "video" -> url?.takeIf { it.isNotBlank() }?.let { GuideContentBlock.Video(it, provider ?: "mp4", caption) }
    "callout" -> text?.takeIf { it.isNotBlank() }?.let { GuideContentBlock.Callout(title ?: "Expert advice", it) }
    "ol" -> items?.takeIf { it.isNotEmpty() }?.let { GuideContentBlock.OrderedList(it) }
    "ul" -> items?.takeIf { it.isNotEmpty() }?.let { GuideContentBlock.UnorderedList(it) }
    "divider" -> GuideContentBlock.Divider
    else -> null
}

// Mapper DTO -> domain
fun GuidePostDto.toGuidePost(baseUrl: String? = null): GuidePost {
    @Suppress("UNUSED_VARIABLE")
    val ignoredBaseUrl = baseUrl
    // Compose content/excerpt/short_description in a sensible way
    val contentText = content ?: excerpt ?: short_description ?: ""
    val excerptText = excerpt ?: short_description ?: description ?: content?.take(150) ?: ""
    val descriptionText = description ?: short_description ?: shortDescription ?: excerpt ?: content?.take(200) ?: ""

    // Keep the API-provided path as-is. UI helpers can convert relative -> absolute when needed.
    val rawImage = (image ?: image_url1 ?: image_url ?: featured_image ?: imageUrl ?: "").trim()

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
    val updatedTime = updatedAt ?: updated_at ?: ""
    val stableId = id.trim().ifBlank {
        val key = "${title.trim()}_${createdTime.trim()}_${rawImage}"
        "guide_${key.hashCode()}"
    }
    val safeLikes = likes.coerceAtLeast(0)
    val safeFavorites = favorites.coerceAtLeast(0)
    val safeViews = views.coerceAtLeast(0)
    val mergedBlocks = (content_blocks ?: contentBlocks ?: emptyList()).mapNotNull { it.toDomainOrNull() }
    val mergedImages = parseStringListOrEmpty(images ?: emptyList<String>())
    val mergedVideoItems = parseVideos(videos)
    val mergedVideos = (mergedVideoItems.map { it.url } + (videoUrls ?: emptyList())).map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    val computedReadTime = anyToInt(estimated_read_time, default = 0).takeIf { it > 0 }
        ?: (readTimeMinutes ?: 0)

    return GuidePost(
        id = stableId,
        title = title.ifBlank { "Untitled Guide" },
        content = contentText,
        excerpt = excerptText,
        description = descriptionText,
        image = rawImage,
        imageUrl = rawImage,
        thumbnailUrl = rawImage,
        category = category ?: category_name ?: "",
        tags = parsedTags,
        difficultyLevel = difficulty_level ?: "",
        estimatedReadTime = computedReadTime,
        readingTime = computedReadTime,
        likes = safeLikes,
        favorites = safeFavorites,
        views = safeViews,
        viewCount = safeViews,
        isFeatured = anyToBoolean(is_featured),
        isPopular = anyToBoolean(is_popular),
        isFavorited = false,               // default value, will be updated by repository
        author = author ?: "",
        authorName = authorDisplay ?: author ?: "",
        authorId = author_id ?: "",
        createdAt = createdTime,
        updatedAt = updatedTime,
        isLiked = false,
        contentBlocks = mergedBlocks,
        images = mergedImages,
        videos = mergedVideos,
        videoItems = mergedVideoItems,
        shareUrl = shareUrl?.trim()?.ifBlank { null },
        priority = priority,
        status = status ?: "published"
    )
}
