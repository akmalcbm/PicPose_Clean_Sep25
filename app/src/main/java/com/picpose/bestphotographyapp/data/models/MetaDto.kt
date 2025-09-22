package com.picpose.bestphotographyapp.data.models

data class MetaDto(
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20,
    val hasMore: Boolean = false
)
