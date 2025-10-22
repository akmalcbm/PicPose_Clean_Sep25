package com.picpose.bestphotographyapp.data.models

import com.google.gson.annotations.SerializedName

data class CategoryDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("slug") val slug: String? = null,
    @SerializedName("parent_id") val parentId: String? = null,
    @SerializedName("image_url") val imageUrl: String? = null,
    @SerializedName("children") val children: List<CategoryDto>? = null,
    @SerializedName("post_count") val postCount: Int = 0
)

fun CategoryDto.toCategory(): Category {
    return Category(
        id = id,
        name = name,
        description = slug ?: "",
        image = imageUrl ?: "",
        post_count = postCount,
    )
}
